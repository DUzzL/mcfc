package dev.epicpix.annotations

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import org.objectweb.asm.*
import java.lang.reflect.Modifier
import java.util.jar.JarFile

data class ClassFieldInfo(
    val owner: String,
    val name: String,
    val descriptor: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
)

data class ClassMethodInfo(
    val owner: String,
    val name: String,
    val descriptor: String,
    val isStatic: Boolean,
)

data class ClassInfo(
    val name: String,
    val superClass: String?,
    val interfaces: List<String>,
    val fields: List<ClassFieldInfo>,
    val methods: List<ClassMethodInfo>,
    val isInterface: Boolean,
)

private val loadedClasses = mutableMapOf<String, ClassInfo>()

typealias JarInfo = Pair<List<JarFile>, Map<String, Int>>
private var privateJarInfo: JarInfo? = null
private var privateResolver: Resolver? = null

fun setJarInfo(jarInfo: JarInfo) {
    privateJarInfo = jarInfo
}

fun setResolver(resolver: Resolver) {
    privateResolver = resolver
}

private fun fixKotlinDesc(from: String): String {
    return when(from) {
        "kotlin.Boolean" -> "Z"
        "kotlin.Unit" -> "V"
        "kotlin.Int" -> "I"
        "kotlin.String" -> "Ljava/lang/String;"
        "kotlin.collections.MutableList" -> "Ljava/util/List;"
        else -> {
            if(from.startsWith("kotlin.")) throw IllegalArgumentException("Unknown kotlin class: $from")
            "L${from.replace('.', '/')};"
        }
    }
}

fun loadClassInfo(className: String): ClassInfo {
    loadedClasses[className]?.also { return it }
    val found = privateJarInfo?.second?.entries?.find { (k, _) -> k.replace('/', '.').replace('$', '.') == "$className.class" }
    if(found == null) {
        runCatching {
            val clz = Class.forName(className)
            loadedClasses[className] = ClassInfo(
                name = clz.descriptorString().run { substring(1, length - 1) },
                superClass = clz.superclass?.descriptorString()?.run { substring(1, length - 1) },
                interfaces = clz.interfaces.map { it.descriptorString().run { substring(1, length - 1) } },
                fields = clz.declaredFields.map { f ->
                    ClassFieldInfo(owner = clz.descriptorString().run { substring(1, length - 1) }, name = f.name, descriptor = f.type.descriptorString(), isStatic = Modifier.isStatic(f.modifiers), isFinal = Modifier.isFinal(f.modifiers))
                },
                methods = clz.declaredMethods.map { f ->
                    ClassMethodInfo(
                        owner = clz.descriptorString().run { substring(1, length - 1) },
                        name = f.name,
                        descriptor = "(${f.parameters.joinToString("") { p -> p.type.descriptorString() }})${f.returnType.descriptorString()}",
                        isStatic = Modifier.isStatic(f.modifiers),
                    )
                } + clz.declaredConstructors.map { f ->
                    ClassMethodInfo(
                        owner = clz.descriptorString().run { substring(1, length - 1) },
                        name = "<init>",
                        descriptor = "(${f.parameters.joinToString("") { p -> p.type.descriptorString() }})V",
                        isStatic = false,
                    )
                },
                isInterface = clz.isInterface,
            )
        }.getOrElse {
            val classDecl = privateResolver!!.getClassDeclarationByName(privateResolver!!.getKSNameFromString(className))
                ?: throw IllegalArgumentException("Class not found: $className", it)
            val methods = classDecl.declarations.filter { it is KSFunctionDeclaration }.map { it as KSFunctionDeclaration }.toList()
            loadedClasses[className] = ClassInfo(
                name = classDecl.qualifiedName!!.asString(),
                superClass = "java.lang.Object",
                interfaces = listOf(),
                fields = listOf(),
                methods = methods.map { f ->
                    ClassMethodInfo(
                        owner = classDecl.qualifiedName!!.asString().replace('.', '/'),
                        name = f.simpleName.asString(),
                        descriptor = "(${f.parameters.joinToString("") { p -> fixKotlinDesc(p.type.resolve().declaration.qualifiedName!!.asString()) }})${fixKotlinDesc(f.returnType!!.resolve().declaration.qualifiedName!!.asString())}",
                        isStatic = f.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC)
                    )
                },
                isInterface = false,
            )
        }
        return loadedClasses[className]!!
    }else {
        val jar = privateJarInfo!!.first[found.value]
        val foundEntry = jar.getInputStream(jar.getJarEntry(found.key))
        val classReader = ClassReader(foundEntry)

        var outName: String? = null
        var superClass: String? = null
        var interfaces: List<String>? = null
        val fields = mutableListOf<ClassFieldInfo>()
        val methods = mutableListOf<ClassMethodInfo>()
        var isInterface: Boolean? = null

        val classVisitor = object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, inName: String, signature: String?, inSuperName: String?, inInterfaces: Array<String>) {
                outName = inName
                superClass = inSuperName
                interfaces = inInterfaces.toList()
                isInterface = Modifier.isInterface(access)
                super.visit(version, access, inName, signature, inSuperName, inInterfaces)
            }

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                fields.add(ClassFieldInfo(
                    owner = outName!!,
                    name = name,
                    descriptor = descriptor,
                    isStatic = Modifier.isStatic(access),
                    isFinal = Modifier.isFinal(access),
                ))
                return super.visitField(access, name, descriptor, signature, value)
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String?>?): MethodVisitor? {
                methods.add(ClassMethodInfo(
                    owner = outName!!,
                    name = name,
                    descriptor = descriptor,
                    isStatic = Modifier.isStatic(access),
                ))
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }
        classReader.accept(classVisitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        foundEntry.close()
        loadedClasses[className] = ClassInfo(
            name = outName!!,
            superClass = superClass,
            interfaces = interfaces!!,
            fields = fields,
            methods = methods,
            isInterface = isInterface!!,
        )
        return loadedClasses[className]!!
    }
}

fun getClassAccessibleFields(className: String): Set<ClassFieldInfo> {
    val result = mutableSetOf<ClassFieldInfo>()
    val clz = loadClassInfo(className)
    clz.superClass?.also { result.addAll(getClassAccessibleFields(it.replace('/', '.').replace('$', '.')).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    clz.interfaces.forEach { result.addAll(getClassAccessibleFields(it.replace('/', '.').replace('$', '.')).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    result.addAll(clz.fields.filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } })
    return result
}

fun getClassAccessibleMethods(className: String): Set<ClassMethodInfo> {
    val result = mutableSetOf<ClassMethodInfo>()
    val clz = loadClassInfo(className)
    clz.superClass?.also { result.addAll(getClassAccessibleMethods(it.replace('/', '.').replace('$', '.')).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    clz.interfaces.forEach { result.addAll(getClassAccessibleMethods(it.replace('/', '.').replace('$', '.')).filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } }) }
    result.addAll(clz.methods.filter { a -> result.none { b -> a.name == b.name && a.descriptor == b.descriptor } })
    return result
}

fun checkClassDistance(from: String, to: String, currentDepth: Int = 0): Int? {
    if(from == to) return currentDepth
    val fromInfo = loadClassInfo(from)
    if(fromInfo.superClass != null) checkClassDistance(fromInfo.superClass.replace('/', '.').replace('$', '.'), to, currentDepth + 1)?.let { return it }
    for(face in fromInfo.interfaces) checkClassDistance(face.replace('/', '.').replace('$', '.'), to, currentDepth + 1)?.let { return it }
    return null
}

fun getClassLowerBounds(className: String): Set<String> {
    val result = mutableSetOf<String>()
    val clz = loadClassInfo(className)
    result.add(clz.name.replace('/', '.').replace('$', '.'))
    clz.superClass?.also { result.addAll(getClassLowerBounds(it.replace('/', '.').replace('$', '.'))) }
    clz.interfaces.forEach { result.addAll(getClassLowerBounds(it.replace('/', '.').replace('$', '.'))) }
    return result
}