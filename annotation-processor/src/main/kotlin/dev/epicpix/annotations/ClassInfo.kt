package dev.epicpix.annotations

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import java.lang.classfile.ClassFile
import java.lang.constant.MethodTypeDesc
import java.lang.reflect.AccessFlag
import java.lang.reflect.Modifier
import java.util.jar.JarFile
import kotlin.jvm.optionals.getOrNull

data class ClassFieldInfo(
    val owner: String,
    val name: String,
    val descriptor: String,
    val isPublic: Boolean,
    val isStatic: Boolean,
    val isFinal: Boolean,
)

data class ClassMethodInfo(
    val owner: String,
    val name: String,
    val descriptor: MethodTypeDesc,
    val isPublic: Boolean,
    val isStatic: Boolean,
)

data class ClassInfo(
    val name: String,
    val superClass: String?,
    val interfaces: List<String>,
    val fields: List<ClassFieldInfo>,
    val methods: List<ClassMethodInfo>,
    val isPublic: Boolean,
    val isInterface: Boolean,
    val isEnum: Boolean,
)

/** Keyed by display name (`a.b.Outer.Inner`). */
private val loadedClasses = HashMap<String, ClassInfo>()
/** Display name -> internal name, including negative results (null). */
private val internalNameCache = HashMap<String, String?>()
private val accessibleFieldsCache = HashMap<String, Set<ClassFieldInfo>>()
private val accessibleMethodsCache = HashMap<String, Set<ClassMethodInfo>>()
private val lowerBoundsCache = HashMap<String, Set<String>>()

private class JarEntryRef(val entryName: String, val jar: JarFile)

private var jarFiles: List<JarFile> = emptyList()
/** Display name -> class file entry; the first jar on the classpath wins, like the JVM. */
private val jarIndex = HashMap<String, JarEntryRef>()
private lateinit var privateResolver: Resolver

fun setJars(jars: List<JarFile>) {
    jarFiles = jars
    jarIndex.clear()
    for(jar in jars) {
        for(entry in jar.entries()) {
            val name = entry.name
            if(entry.isDirectory || !name.endsWith(".class")) continue
            jarIndex.putIfAbsent(name.substring(0, name.length - 6).replace('/', '.').replace('$', '.'), JarEntryRef(name, jar))
        }
    }
}

/** Closes the jars and drops every cache; called at the end of each processing run. */
fun closeClassInfo() {
    jarFiles.forEach { it.close() }
    jarFiles = emptyList()
    jarIndex.clear()
    loadedClasses.clear()
    internalNameCache.clear()
    accessibleFieldsCache.clear()
    accessibleMethodsCache.clear()
    lowerBoundsCache.clear()
}

fun setResolver(resolver: Resolver) {
    privateResolver = resolver
}

/** JVM binary name (`a/b/Outer$Inner`) of a source/classpath declaration. */
@OptIn(KspExperimental::class)
private fun binaryName(decl: KSClassDeclaration): String {
    val qualified = decl.qualifiedName!!
    privateResolver.mapKotlinNameToJava(qualified)?.let { return it.asString().replace('.', '/') }
    val names = ArrayDeque<String>()
    var current: KSClassDeclaration? = decl
    while(current != null) {
        names.addFirst(current.simpleName.asString())
        current = current.parentDeclaration as? KSClassDeclaration
    }
    val pkg = decl.packageName.asString()
    return (if(pkg.isEmpty()) "" else pkg.replace('.', '/') + "/") + names.joinToString("$")
}

fun displayToInternalClassName(className: String): String? {
    if(className.contains("/")) throw RuntimeException("Expected display class name, but got '$className'")
    if(internalNameCache.containsKey(className)) return internalNameCache[className]
    val result = loadedClasses[className]?.name
        ?: jarIndex[className]?.entryName?.let { it.substring(0, it.length - 6) }
        ?: runCatching { Class.forName(className).descriptorString().let { it.substring(1, it.length - 1) } }.getOrNull()
        ?: privateResolver.getClassDeclarationByName(privateResolver.getKSNameFromString(className))?.let { binaryName(it) }
    internalNameCache[className] = result
    return result
}

fun loadClassInfo(className: String): ClassInfo {
    if(className.contains(".")) throw RuntimeException("Expected internal class name, but got '$className'")
    val displayName = className.replace('/', '.').replace('$', '.')
    loadedClasses[displayName]?.also { return it }
    val found = jarIndex[displayName]
    val info = if(found != null) {
        loadFromClassFile(found)
    } else {
        runCatching { loadFromReflection(Class.forName(displayName)) }.getOrElse {
            val classDecl = privateResolver.getClassDeclarationByName(privateResolver.getKSNameFromString(displayName))
                ?: throw IllegalArgumentException("Class not found: $displayName", it)
            loadFromDeclaration(classDecl)
        }
    }
    loadedClasses[displayName] = info
    return info
}

private fun loadFromClassFile(found: JarEntryRef): ClassInfo {
    val cf = found.jar.getInputStream(found.jar.getJarEntry(found.entryName)).use { ClassFile.of().parse(it.readAllBytes()) }
    val name = cf.thisClass().asInternalName()
    return ClassInfo(
        name = name,
        superClass = cf.superclass().getOrNull()?.asInternalName(),
        interfaces = cf.interfaces().map { it.asInternalName() }.toList(),
        fields = cf.fields().map {
            ClassFieldInfo(
                owner = name,
                name = it.fieldName().stringValue(),
                descriptor = it.fieldType().stringValue(),
                isPublic = it.flags().has(AccessFlag.PUBLIC),
                isStatic = it.flags().has(AccessFlag.STATIC),
                isFinal = it.flags().has(AccessFlag.FINAL),
            )
        }.toList(),
        methods = cf.methods().map {
            ClassMethodInfo(
                owner = name,
                name = it.methodName().stringValue(),
                descriptor = it.methodTypeSymbol(),
                isPublic = it.flags().has(AccessFlag.PUBLIC),
                isStatic = it.flags().has(AccessFlag.STATIC),
            )
        }.toList(),
        isPublic = cf.flags().has(AccessFlag.PUBLIC),
        isInterface = cf.flags().has(AccessFlag.INTERFACE),
        isEnum = cf.flags().has(AccessFlag.ENUM),
    )
}

private fun loadFromReflection(clz: Class<*>): ClassInfo {
    val clzName = clz.descriptorString().run { substring(1, length - 1) }
    return ClassInfo(
        name = clzName,
        superClass = clz.superclass?.descriptorString()?.run { substring(1, length - 1) },
        interfaces = clz.interfaces.map { it.descriptorString().run { substring(1, length - 1) } },
        fields = clz.declaredFields.map { f ->
            ClassFieldInfo(owner = clzName, name = f.name, descriptor = f.type.descriptorString(), isPublic = Modifier.isPublic(f.modifiers), isStatic = Modifier.isStatic(f.modifiers), isFinal = Modifier.isFinal(f.modifiers))
        },
        methods = clz.declaredMethods.map { f ->
            ClassMethodInfo(
                owner = clzName,
                name = f.name,
                descriptor = MethodTypeDesc.ofDescriptor("(${f.parameterTypes.joinToString("") { p -> p.descriptorString() }})${f.returnType.descriptorString()}"),
                isPublic = Modifier.isPublic(f.modifiers),
                isStatic = Modifier.isStatic(f.modifiers),
            )
        } + clz.declaredConstructors.map { f ->
            ClassMethodInfo(
                owner = clzName,
                name = "<init>",
                descriptor = MethodTypeDesc.ofDescriptor("(${f.parameterTypes.joinToString("") { p -> p.descriptorString() }})V"),
                isPublic = Modifier.isPublic(f.modifiers),
                isStatic = false,
            )
        },
        isPublic = Modifier.isPublic(clz.modifiers),
        isInterface = clz.isInterface,
        isEnum = clz.isEnum,
    )
}

/** Source classes of the module being processed: descriptors come from KSP's exact JVM signature mapping. */
@OptIn(KspExperimental::class)
private fun loadFromDeclaration(classDecl: KSClassDeclaration): ClassInfo {
    val name = binaryName(classDecl)
    val isInterface = classDecl.classKind == ClassKind.INTERFACE || classDecl.classKind == ClassKind.ANNOTATION_CLASS
    var superClass: String? = if(isInterface) "java/lang/Object" else null
    val interfaces = mutableListOf<String>()
    for(superType in classDecl.superTypes) {
        val decl = superType.resolve().declaration as? KSClassDeclaration ?: continue
        val superName = binaryName(decl)
        if(decl.classKind == ClassKind.INTERFACE) {
            interfaces.add(superName)
        }else if(superClass == null) {
            superClass = superName
        }
    }
    if(superClass == null && name != "java/lang/Object") superClass = "java/lang/Object"
    val enumEntries = classDecl.declarations.filterIsInstance<KSClassDeclaration>().filter { decl -> decl.classKind == ClassKind.ENUM_ENTRY }.map { f ->
        ClassFieldInfo(
            owner = name,
            name = f.simpleName.asString(),
            descriptor = "L$name;",
            isPublic = true,
            isStatic = true,
            isFinal = true,
        )
    }
    val fields = classDecl.getDeclaredProperties().mapNotNull { f ->
        val descriptor = privateResolver.mapToJvmSignature(f) ?: return@mapNotNull null
        ClassFieldInfo(
            owner = name,
            name = f.simpleName.asString(),
            descriptor = descriptor,
            isPublic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
            isStatic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC),
            isFinal = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.FINAL),
        )
    }
    val methods = classDecl.declarations.filterIsInstance<KSFunctionDeclaration>().mapNotNull { f ->
        val descriptor = privateResolver.mapToJvmSignature(f) ?: return@mapNotNull null
        val methodName = f.simpleName.asString()
        ClassMethodInfo(
            owner = name,
            name = if(methodName == name.substringAfterLast('/').substringAfterLast('$')) "<init>" else methodName,
            descriptor = MethodTypeDesc.ofDescriptor(descriptor),
            isPublic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
            isStatic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC),
        )
    }
    return ClassInfo(
        name = name,
        superClass = superClass,
        interfaces = interfaces,
        fields = (enumEntries + fields).toList(),
        methods = methods.toList(),
        isPublic = classDecl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
        isInterface = isInterface,
        isEnum = classDecl.classKind == ClassKind.ENUM_CLASS,
    )
}

fun ClassInfo.checkPublic(): ClassInfo {
    if(!isPublic) {
        throw IllegalAccessError("Tried to access class $name, while it is not public")
    }
    return this
}

fun ClassFieldInfo.checkPublic(): ClassFieldInfo {
    if(!isPublic) {
        throw IllegalAccessError("Tried to access field $owner:$name:$descriptor, while it is not public")
    }
    if(!loadClassInfo(owner).isPublic) {
        throw IllegalAccessError("Tried to access field $owner:$name:$descriptor, while the class is not public")
    }
    return this
}

fun ClassMethodInfo.checkPublic(): ClassMethodInfo {
    if(!isPublic) {
        throw IllegalAccessError("Tried to access method $owner:$name$descriptor, while it is not public")
    }
    if(!loadClassInfo(owner).isPublic) {
        throw IllegalAccessError("Tried to access method $owner:$name$descriptor, while the class is not public")
    }
    return this
}

/** Members visible on [className]; the first declaration found (superclass, then interfaces, then own) wins per signature. */
private fun <T> collectAccessible(className: String, cache: HashMap<String, Set<T>>, own: (ClassInfo) -> List<T>, key: (T) -> Pair<String, Any>): Set<T> {
    cache[className]?.let { return it }
    val clz = loadClassInfo(className)
    val seen = HashSet<Pair<String, Any>>()
    val result = LinkedHashSet<T>()
    fun addAll(items: Iterable<T>) {
        for(item in items) if(seen.add(key(item))) result.add(item)
    }
    clz.superClass?.also { addAll(collectAccessible(it, cache, own, key)) }
    clz.interfaces.forEach { addAll(collectAccessible(it, cache, own, key)) }
    addAll(own(clz))
    cache[className] = result
    return result
}

fun getClassAccessibleFields(className: String): Set<ClassFieldInfo> =
    collectAccessible(className, accessibleFieldsCache, { it.fields }, { it.name to it.descriptor })

fun getClassAccessibleMethods(className: String): Set<ClassMethodInfo> =
    collectAccessible(className, accessibleMethodsCache, { it.methods }, { it.name to it.descriptor })

fun checkClassDistance(from: String, to: String, currentDepth: Int = 0): Int? {
    if(from == to) return currentDepth
    val fromInfo = loadClassInfo(from)
    if(fromInfo.superClass != null) checkClassDistance(fromInfo.superClass, to, currentDepth + 1)?.let { return it }
    for(face in fromInfo.interfaces) checkClassDistance(face, to, currentDepth + 1)?.let { return it }
    return null
}

fun getClassLowerBounds(className: String): Set<String> {
    lowerBoundsCache[className]?.let { return it }
    val result = HashSet<String>()
    val clz = loadClassInfo(className)
    result.add(clz.name)
    clz.superClass?.also { result.addAll(getClassLowerBounds(it)) }
    clz.interfaces.forEach { result.addAll(getClassLowerBounds(it)) }
    lowerBoundsCache[className] = result
    return result
}
