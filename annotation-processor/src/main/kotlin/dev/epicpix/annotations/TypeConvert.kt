package dev.epicpix.annotations

import java.lang.constant.MethodTypeDesc

fun kotlinToJavaType(type: String, genericUsage: Boolean, generics: List<String> = emptyList()): String {
    val genericsString = if(generics.isEmpty()) "" else "<${generics.joinToString(", ") { "? extends $it" }}>"
    return when(type) {
        "kotlin.Boolean" -> if(!genericUsage) "boolean" else "Boolean"
        "kotlin.Double" -> if(!genericUsage) "double" else "Double"
        "kotlin.Float" -> if(!genericUsage) "float" else "Float"
        "kotlin.Int" -> if(!genericUsage) "int" else "Integer"
        "kotlin.Long" -> if(!genericUsage) "long" else "Long"
        "kotlin.String" -> "String"
        "kotlin.collections.List" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        "kotlin.collections.MutableList" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        "kotlin.collections.Map" -> "Map"
        "java.util.List" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        else -> if(type.startsWith("kotlin.")) throw UnsupportedOperationException("Unknown kotlin type: $type") else type
    } + genericsString
}

fun javaToMinecraftGenericType(type: String, generics: List<String> = emptyList()): String {
    return if(type.startsWith("net.minecraft.")) {
        "dev.epicpix.minecraftfunctioncompiler.il.locations.T${type.split(".").last()}"
    } else {
        kotlinToJavaType(type, true, generics)
    }
}

fun boxClassName(targetClass: String): String {
    if(targetClass == "java/lang/Void") return "ConstantDescs.CD_Void"
    if(targetClass == "java/lang/Boolean") return "ConstantDescs.CD_Boolean"
    if(targetClass == "java/lang/Byte") return "ConstantDescs.CD_Byte"
    if(targetClass == "java/lang/Character") return "ConstantDescs.CD_Character"
    if(targetClass == "java/lang/Short") return "ConstantDescs.CD_Short"
    if(targetClass == "java/lang/Integer") return "ConstantDescs.CD_Integer"
    if(targetClass == "java/lang/Long") return "ConstantDescs.CD_Long"
    if(targetClass == "java/lang/Float") return "ConstantDescs.CD_Float"
    if(targetClass == "java/lang/Double") return "ConstantDescs.CD_Double"
    if(targetClass == "java/lang/Object") return "ConstantDescs.CD_Object"
    if(targetClass == "java/lang/String") return "ConstantDescs.CD_String"
    return "ClassDesc.ofInternalName(\"$targetClass\")"
}

fun boxMethodDescriptor(methodType: MethodTypeDesc): String {
    val parameters = mutableListOf<String>()
    parameters.add(boxDescriptor(methodType.returnType().descriptorString()))
    parameters.addAll(methodType.parameterList().map { boxDescriptor(it.descriptorString()) })
    return "MethodTypeDesc.of(${parameters.joinToString()})"
}

fun boxDescriptor(descriptor: String): String {
    if(descriptor == "V") return "ConstantDescs.CD_void"
    if(descriptor == "Z") return "ConstantDescs.CD_boolean"
    if(descriptor == "B") return "ConstantDescs.CD_byte"
    if(descriptor == "C") return "ConstantDescs.CD_char"
    if(descriptor == "S") return "ConstantDescs.CD_short"
    if(descriptor == "I") return "ConstantDescs.CD_int"
    if(descriptor == "J") return "ConstantDescs.CD_long"
    if(descriptor == "F") return "ConstantDescs.CD_float"
    if(descriptor == "D") return "ConstantDescs.CD_double"
    if(descriptor.startsWith("L")) return boxClassName(descriptor.substring(1, descriptor.length - 1))
    return "ClassDesc.ofDescriptor(\"$descriptor\")"
}