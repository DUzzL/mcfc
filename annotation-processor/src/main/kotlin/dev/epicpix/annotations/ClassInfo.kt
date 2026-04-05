package dev.epicpix.annotations

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
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

private val loadedClasses = mutableMapOf<String, ClassInfo>()

typealias JarInfo = Pair<List<JarFile>, Map<String, Int>>
private var privateJarInfo: JarInfo? = null
private lateinit var privateResolver: Resolver

fun setJarInfo(jarInfo: JarInfo) {
    privateJarInfo = jarInfo
}

fun setResolver(resolver: Resolver) {
    privateResolver = resolver
}

private fun fixKotlinDesc(type: KSType): String {
    return when(val from = type.declaration.qualifiedName!!.asString()) {
        "kotlin.Double" -> "D"
        "kotlin.Float" -> "F"
        "kotlin.Boolean" -> "Z"
        "kotlin.Unit" -> "V"
        "kotlin.Int" -> "I"
        "kotlin.String" -> "Ljava/lang/String;"
        "kotlin.collections.MutableList" -> "Ljava/util/List;"
        "kotlin.collections.MutableCollection" -> "Ljava/util/Collection;"
        "kotlin.collections.MutableSet" -> "Ljava/util/Set;"
        "kotlin.collections.MutableMap" -> "Ljava/util/Map;"
        "kotlin.collections.List" -> "Ljava/util/List;"
        "kotlin.collections.Collection" -> "Ljava/util/Collection;"
        "kotlin.collections.Set" -> "Ljava/util/Set;"
        "kotlin.collections.Map" -> "Ljava/util/Map;"
        "kotlin.collections.Iterable" -> "Ljava/lang/Iterable;"
        "kotlin.collections.MutableIterable" -> "Ljava/lang/Iterable;"
        "kotlin.Array" -> "[Ljava/lang/Object;"
        else -> {
            if(from.startsWith("kotlin.")) throw IllegalArgumentException("Unknown kotlin class: $from")
            "L${from.replace('.', '/')};"
        }
    }
}

fun displayToInternalClassName(className: String): String? {
    if(className.contains("/")) throw RuntimeException("Expected display class name, but got '$className'")
    loadedClasses[className]?.also {
        if(it.name.contains(".")) throw IllegalStateException("Loaded class name is not stored as internal name: ${it.name}")
        return it.name
    }
    val found = privateJarInfo?.second?.entries?.find { (k, _) -> k.replace('/', '.').replace('$', '.') == "$className.class" }
    if(found != null) {
        return found.key.substring(0, found.key.length - 6)
    }
    runCatching {
        val clz = Class.forName(className)
        return clz.descriptorString().let { it.substring(1, it.length - 1) }
    }

    return privateResolver.getClassDeclarationByName(privateResolver.getKSNameFromString(className))?.qualifiedName?.asString()?.replace('.', '/')
}

fun loadClassInfo(className: String): ClassInfo {
    if(className.contains(".")) throw RuntimeException("Expected internal class name, but got '$className'")
    val className = className.replace('/', '.').replace('$', '.')
    loadedClasses[className]?.also { return it }
    val found = privateJarInfo?.second?.entries?.find { (k, _) -> k.replace('/', '.').replace('$', '.') == "$className.class" }
    if(found == null) {
        runCatching {
            val clz = Class.forName(className)
            val clzName = clz.descriptorString().run { substring(1, length - 1) }
            loadedClasses[className] = ClassInfo(
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
                        descriptor = MethodTypeDesc.ofDescriptor("(${f.parameters.joinToString("") { p -> p.type.descriptorString() }})${f.returnType.descriptorString()}"),
                        isPublic = Modifier.isPublic(f.modifiers),
                        isStatic = Modifier.isStatic(f.modifiers),
                    )
                } + clz.declaredConstructors.map { f ->
                    ClassMethodInfo(
                        owner = clzName,
                        name = "<init>",
                        descriptor = MethodTypeDesc.ofDescriptor("(${f.parameters.joinToString("") { p -> p.type.descriptorString() }})V"),
                        isPublic = Modifier.isPublic(f.modifiers),
                        isStatic = false,
                    )
                },
                isPublic = Modifier.isPublic(clz.modifiers),
                isInterface = clz.isInterface,
                isEnum = clz.isEnum,
            )
        }.getOrElse {
            val classDecl = privateResolver.getClassDeclarationByName(privateResolver.getKSNameFromString(className))
                ?: throw IllegalArgumentException("Class not found: $className", it)
            val enumEntries = classDecl.declarations.filterIsInstance<KSClassDeclaration>().filter { decl -> decl.classKind == ClassKind.ENUM_ENTRY }.toList()
            val methods = classDecl.declarations.filterIsInstance<KSFunctionDeclaration>().toList()
            loadedClasses[className] = ClassInfo(
                name = classDecl.qualifiedName!!.asString().replace('.', '/'),
                superClass = "java/lang/Object",
                interfaces = listOf(),
                fields = enumEntries.map { f ->
                    ClassFieldInfo(
                        owner = classDecl.qualifiedName!!.asString().replace('.', '/'),
                        name = f.simpleName.asString(),
                        descriptor = "L${className.replace('.', '/')};",
                        isPublic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
                        isStatic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC),
                        isFinal = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.FINAL),
                    )
                },
                methods = methods.map { f ->
                    ClassMethodInfo(
                        owner = classDecl.qualifiedName!!.asString().replace('.', '/'),
                        name = f.simpleName.asString(),
                        descriptor = MethodTypeDesc.ofDescriptor("(${f.parameters.joinToString("") { p -> fixKotlinDesc(p.type.resolve()) }})${fixKotlinDesc(f.returnType!!.resolve())}"),
                        isPublic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
                        isStatic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC),
                    )
                },
                isPublic = classDecl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC),
                isInterface = false,
                isEnum = classDecl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.ENUM),
            )
        }
        return loadedClasses[className]!!
    }else {
        val jar = privateJarInfo!!.first[found.value]
        val foundEntry = jar.getInputStream(jar.getJarEntry(found.key))
        val cf = ClassFile.of().parse(foundEntry.readAllBytes())
        foundEntry.close()
        val name = cf.thisClass().asInternalName()
        val isPublic = cf.flags().has(AccessFlag.PUBLIC)
        val isInterface = cf.flags().has(AccessFlag.INTERFACE)
        val isEnum = cf.flags().has(AccessFlag.ENUM)
        val superClass = cf.superclass().getOrNull()?.asInternalName()
        val interfaces = cf.interfaces().map { it.asInternalName() }.toList()
        val fields = cf.fields().map {
            ClassFieldInfo(
                owner = name,
                name = it.fieldName().stringValue(),
                descriptor = it.fieldType().stringValue(),
                isPublic = it.flags().has(AccessFlag.PUBLIC),
                isStatic = it.flags().has(AccessFlag.STATIC),
                isFinal = it.flags().has(AccessFlag.FINAL),
            )
        }.toList()
        val methods = cf.methods().map {
            ClassMethodInfo(
                owner = name,
                name = it.methodName().stringValue(),
                descriptor = it.methodTypeSymbol(),
                isPublic = it.flags().has(AccessFlag.PUBLIC),
                isStatic = it.flags().has(AccessFlag.STATIC),
            )
        }.toList()
        val ci = ClassInfo(
            name = name,
            superClass = superClass,
            interfaces = interfaces,
            fields = fields,
            methods = methods,
            isPublic = isPublic,
            isInterface = isInterface,
            isEnum = isEnum,
        )
        loadedClasses[className] = ci
        return ci
    }
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

fun getClassAccessibleFields(className: String): Set<ClassFieldInfo> {
    val result = mutableSetOf<ClassFieldInfo>()
    val clz = loadClassInfo(className)
    clz.superClass?.also { result.addAll(getClassAccessibleFields(it).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    clz.interfaces.forEach { result.addAll(getClassAccessibleFields(it).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    result.addAll(clz.fields.filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } })
    return result
}

fun getClassAccessibleMethods(className: String): Set<ClassMethodInfo> {
    val result = mutableSetOf<ClassMethodInfo>()
    val clz = loadClassInfo(className)
    clz.superClass?.also { result.addAll(getClassAccessibleMethods(it).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    clz.interfaces.forEach { result.addAll(getClassAccessibleMethods(it).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    result.addAll(clz.methods.filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } })
    return result
}

fun checkClassDistance(from: String, to: String, currentDepth: Int = 0): Int? {
    if(from == to) return currentDepth
    val fromInfo = loadClassInfo(from)
    if(fromInfo.superClass != null) checkClassDistance(fromInfo.superClass, to, currentDepth + 1)?.let { return it }
    for(face in fromInfo.interfaces) checkClassDistance(face, to, currentDepth + 1)?.let { return it }
    return null
}

fun getClassLowerBounds(className: String): Set<String> {
    val result = mutableSetOf<String>()
    val clz = loadClassInfo(className)
    result.add(clz.name)
    clz.superClass?.also { result.addAll(getClassLowerBounds(it)) }
    clz.interfaces.forEach { result.addAll(getClassLowerBounds(it)) }
    return result
}