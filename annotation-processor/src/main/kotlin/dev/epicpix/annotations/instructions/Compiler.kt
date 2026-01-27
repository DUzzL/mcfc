package dev.epicpix.annotations.instructions

import dev.epicpix.annotations.*
import org.objectweb.asm.Type
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

data class AccessorStatistics(val accessor: ParsedTokenValueAccessor, var gets: Boolean = false, var sets: Boolean = false)
data class LocalInfo(val mapping: String, val type: ParsedTokenExpressionType)

data class WriterData(
    val accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor> = mapOf(),
    val localTypes: MutableMap<String, LocalInfo> = mutableMapOf(),
    val file: ParsedFile,
    val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>,
    val paramStatistics: List<AccessorStatistics> = params.map { AccessorStatistics(it.first) },
    val majorVersion: Int,
    val minorVersion: Int,

    val targets: MutableList<String>,
) {
    fun copyLocalTypes() = copy(localTypes = localTypes.toMutableMap())

    fun getAccessorType(accessor: ParsedTokenValueAccessor): ParsedTokenExpressionType {
        if(accessor.scope == ParsedTokenValueAccessorScope.LOCAL) {
            return localTypes.entries.find { it.key == accessor.name }?.value?.type ?: throw NullPointerException("Failed to find type of $accessor")
        }
        return params.find { (it, _) -> it.name == accessor.name && it.scope == accessor.scope }?.second ?: throw NullPointerException("Failed to find type of $accessor")
    }
    fun getParamIndex(accessor: ParsedTokenValueAccessor): Int {
        var i = 0
        for(param in params) {
            if(param.first.scope == ParsedTokenValueAccessorScope.CONTEXT || param.first.scope == ParsedTokenValueAccessorScope.INSTRUCTION || param.first.scope == ParsedTokenValueAccessorScope.RETURN) {
                if(param.first == accessor) {
                    return i
                }
                i++
            }
        }
        throw IllegalArgumentException("Parameter for accessor $accessor not found")
    }
    fun markGet(accessor: ParsedTokenValueAccessor) {
        paramStatistics.find { it.accessor == accessor }?.gets = true
    }
    fun markSet(accessor: ParsedTokenValueAccessor) {
        paramStatistics.find { it.accessor == accessor }?.sets = true
    }
    fun isAboveVersion(major: Int, minor: Int): Boolean {
        return majorVersion > major || (majorVersion == major && minorVersion > minor)
    }
    fun isBelowVersion(major: Int, minor: Int): Boolean {
        return majorVersion < major || (majorVersion == major && minorVersion < minor)
    }
}

private fun evaluateExpressionType(writerData: WriterData, expression: ParsedTokenExpression): ParsedTokenExpressionType {
    if(expression is ParsedTokenExpressionTyped) {
        return expression.type
    }else if(expression is ParsedTokenExpressionBinary) {
        val a = evaluateExpressionType(writerData, expression.a)
        val b = evaluateExpressionType(writerData, expression.b)
        if(expression.op == ParsedTokenExpressionOperator.ADD || expression.op == ParsedTokenExpressionOperator.SUB || expression.op == ParsedTokenExpressionOperator.MUL || expression.op == ParsedTokenExpressionOperator.DIV || expression.op == ParsedTokenExpressionOperator.MOD) {
            if(a.isNumber() && a == b) {
                return a
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SHR) {
            if(a == ParsedTokenExpressionSimpleType.LONG && b == ParsedTokenExpressionSimpleType.INTEGER) {
                return ParsedTokenExpressionSimpleType.LONG
            }
        }else if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }else if(a.isNumber() && a == b) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }else if(a.isObjectLike() && b.isObjectLike()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
        }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT || expression.op == ParsedTokenExpressionOperator.GE || expression.op == ParsedTokenExpressionOperator.LE) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }else if(a.isNumber() && a == b) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
        }
    }else if(expression is ParsedTokenValueAccessor) {
        return writerData.getAccessorType(expression)
    }else if(expression is ParsedTokenExpressionNumber) {
        return ParsedTokenExpressionSimpleType.INTEGER
    }else if(expression is ParsedTokenExpressionBoolean) {
        return ParsedTokenExpressionSimpleType.BOOLEAN
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance == null) {
            if(expression.name == "cast") {
                return (expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex
            }
            if(expression.name == "map_advancement") {
                return if(writerData.isAboveVersion(20, 1)) {
                    ParsedTokenExpressionTypeComplex("net.minecraft.advancements.AdvancementHolder", emptyList())
                }else {
                    ParsedTokenExpressionTypeComplex("net.minecraft.advancements.Advancement", emptyList())
                }
            }
            if(expression.name == "map_attribute") {
                return if(writerData.isAboveVersion(20, 4)) {
                    ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder", emptyList())
                }else {
                    ParsedTokenExpressionTypeComplex("net.minecraft.world.entity.ai.attributes.Attribute", emptyList())
                }
            }
            if(expression.name == "map_mob_effect") {
                return if(writerData.isAboveVersion(20, 4)) {
                    ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder", emptyList())
                }else {
                    ParsedTokenExpressionTypeComplex("net.minecraft.world.effect.MobEffect", emptyList())
                }
            }
            if(expression.name == "map_resource_location") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.resources.Identifier", emptyList())
            }
            if(expression.name == "map_nbt_value") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.nbt.Tag", emptyList())
            }
            if(expression.name == "map_scoreboard_criterion") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.world.scores.criteria.ObjectiveCriteria", emptyList())
            }
            if(expression.name == "map_text") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.network.chat.Component", emptyList())
            }
            if(expression.name == "map_text_style") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.network.chat.Style", emptyList())
            }
            if(expression.name == "map_predicate") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.world.level.storage.loot.predicates.LootItemCondition", emptyList())
            }
            if(expression.name == "map_entity_type") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.world.entity.EntityType", emptyList())
            }
            if(expression.name == "to_double") {
                return ParsedTokenExpressionSimpleType.DOUBLE
            }
            if(expression.name == "to_float") {
                return ParsedTokenExpressionSimpleType.FLOAT
            }
            if(expression.name == "to_integer") {
                return ParsedTokenExpressionSimpleType.INTEGER
            }
            if(expression.name == "compile_eval") {
                return evaluateExpressionType(writerData, expression.arguments[0])
            }
            if(expression.name == "compile_or") {
                val typeA = evaluateExpressionType(writerData, expression.arguments[1])
                val typeB = evaluateExpressionType(writerData, expression.arguments[2])
                if(typeA != typeB) throw IllegalStateException("compile_or invalid types: ${typeA} | ${typeB}")
                return typeA
            }
            if(expression.name == "_loadMacro") {
                return ParsedTokenExpressionTypeComplex("java.lang.Object", emptyList())
            }
        }
        val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
        val instance = expression.instance
        val functionMatches = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression).let { if(it is ParsedTokenExpressionTypeGeneric && it.lowerBound.typeName != "java.lang.Number") ParsedTokenExpressionTypeComplex(it.lowerBound.typeName, emptyList()) else it }
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }.toMutableList()
            if(matches.size < 1) {
                throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
            }
            if(matches.size > 1) {
                val closest = matches.map { it to checkClassDistance(it.owner.replace('/', '.').replace('$', '.'), instanceType.typeName) }.reduce { a, b -> if(a.second == null || a.second!! > b.second!!) b else a }
                matches.clear()
                matches.add(closest.first)
            }
            matches
        }else if(instance is ParsedTokenInstanceStatic) {
            val checkStatic = expression.name != "<init>"
            val matches = getClassAccessibleMethods(instance.className).filter { it.isStatic == checkStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
            if(matches.size < 1) {
                throw IllegalArgumentException("Failed to find function <${instance.className}>.${expression.name}(${paramTypes.joinToString()})")
            }
            if(matches.size > 1) {
                throw IllegalArgumentException("Found too many functions matching <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $matches")
            }
            matches
        }else {
            throw IllegalArgumentException("Cannot run function on unknown instance: $instance | ${expression.name}")
        }
        val def = Type.getMethodType(functionMatches[0].descriptor).returnType
        if(def == Type.BOOLEAN_TYPE) {
            return ParsedTokenExpressionSimpleType.BOOLEAN
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleType.INTEGER
        }
        if(def == Type.FLOAT_TYPE) {
            return ParsedTokenExpressionSimpleType.FLOAT
        }
        if(def == Type.DOUBLE_TYPE) {
            return ParsedTokenExpressionSimpleType.DOUBLE
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className.replace('$', '.'), emptyList())
        }
        if(expression.name == "<init>") {
            if(functionMatches[0].owner == "java/lang/Object" && functionMatches[0].name == "<init>" && functionMatches[0].descriptor == "()V") {
                return ParsedTokenExpressionTypeComplex((instance as ParsedTokenInstanceStatic).className.replace('/', '.').replace('$', '.'), emptyList())
            }
            return ParsedTokenExpressionTypeComplex(functionMatches[0].owner.replace('/', '.').replace('$', '.'), emptyList())
        }
        throw IllegalArgumentException(def.descriptor)
    }else if(expression is ParsedTokenExpressionField) {
        val instance = expression.instance
        val fieldMatch = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot access fields on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleFields(instanceType.typeName).filter { !it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field [${instanceType.typeName}].${expression.name}")
            }
            matches[0]
        }else if(instance is ParsedTokenInstanceStatic) {
            val matches = getClassAccessibleFields(instance.className).filter { it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field <${instance.className}>.${expression.name}")
            }
            matches[0]
        }else {
            throw IllegalArgumentException("Cannot access field on unknown instance: $instance")
        }
        val def = Type.getType(fieldMatch.descriptor)
        if(def == Type.BOOLEAN_TYPE) {
            return ParsedTokenExpressionSimpleType.BOOLEAN
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleType.INTEGER
        }
        if(def == Type.FLOAT_TYPE) {
            return ParsedTokenExpressionSimpleType.FLOAT
        }
        if(def == Type.DOUBLE_TYPE) {
            return ParsedTokenExpressionSimpleType.DOUBLE
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className.replace('$', '.'), emptyList())
        }
        TODO(def.descriptor)
    }
    TODO("$expression")
}

private fun ParsedTokenExpressionType.isObjectLike(): Boolean {
    return this is ParsedTokenExpressionTypeComplex || this == ParsedTokenExpressionSimpleType.NULL || this == ParsedTokenExpressionSimpleType.OBJECT
}

private fun ParsedTokenExpressionType.isIntegerLikeType(): Boolean {
    return this == ParsedTokenExpressionSimpleType.INTEGER || this == ParsedTokenExpressionSimpleType.BOOLEAN
}

private fun ParsedTokenExpressionType.isNumber(): Boolean {
    return when(this) {
        ParsedTokenExpressionSimpleType.NUMBER -> true
        ParsedTokenExpressionSimpleType.INTEGER -> true
        ParsedTokenExpressionSimpleType.LONG -> true
        ParsedTokenExpressionSimpleType.FLOAT -> true
        ParsedTokenExpressionSimpleType.DOUBLE -> true
        else -> (this is ParsedTokenExpressionTypeGeneric && this.lowerBound.typeName == "java.lang.Number") || (this is ParsedTokenExpressionTypeComplex && this.typeName == "java.lang.Number")
    }
}

private fun ParsedTokenExpressionType.getClassName(): String? {
    return when(this) {
        is ParsedTokenExpressionTypeGeneric -> this.lowerBound.typeName
        is ParsedTokenExpressionTypeComplex -> this.typeName
        else -> null
    }
}

private fun ParsedTokenExpressionType.getGenericClassName(): String {
    return when(this) {
        ParsedTokenExpressionSimpleType.NULL -> "java.lang.Object"
        ParsedTokenExpressionSimpleType.BOOLEAN -> "java.lang.Boolean"
        ParsedTokenExpressionSimpleType.INTEGER -> "java.lang.Integer"
        ParsedTokenExpressionSimpleType.LONG -> "java.lang.Long"
        ParsedTokenExpressionSimpleType.FLOAT -> "java.lang.Float"
        ParsedTokenExpressionSimpleType.DOUBLE -> "java.lang.Double"
        ParsedTokenExpressionSimpleType.OBJECT -> "java.lang.Object"
        ParsedTokenExpressionSimpleType.NUMBER -> "java.lang.Number"
        is ParsedTokenExpressionTypeGeneric -> this.lowerBound.typeName
        is ParsedTokenExpressionTypeComplex -> this.typeName
        else -> throw IllegalArgumentException("Cannot get generic class name for $this")
    }
}

private fun isExpressionSimpleType(writerData: WriterData, expression: ParsedTokenExpression, type: ParsedTokenExpressionSimpleType): Boolean {
    return evaluateExpressionType(writerData, expression) == type
}

private fun doesArgumentTypeMatch(matchType: ParsedTokenExpressionType, expectedType: Type): Boolean {
    if(expectedType == Type.BOOLEAN_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.BOOLEAN
    }else if(expectedType == Type.INT_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.INTEGER
    }else if(expectedType == Type.LONG_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.LONG
    }else if(expectedType == Type.FLOAT_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.FLOAT
    }else if(expectedType == Type.DOUBLE_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.DOUBLE
    }else if(expectedType.sort == Type.OBJECT) {
        return doesTypeMatch(matchType, ParsedTokenExpressionTypeComplex(expectedType.className.replace('$', '.'), emptyList()))
    }else if(expectedType.sort == Type.ARRAY) {
        return false
    }else {
        TODO(expectedType.descriptor)
    }
}

private fun doesTypeMatch(matchType: ParsedTokenExpressionType, expectedType: ParsedTokenExpressionType): Boolean {
    if(matchType == expectedType) return true
    val expectedClassName = expectedType.getClassName()
    if(expectedClassName != null) {
        if(matchType == ParsedTokenExpressionSimpleType.NULL) return true
        val matchClassName = matchType.getClassName()
        if(matchClassName == null) return false
        return getClassLowerBounds(expectedClassName).any { matchClassName == it } || getClassLowerBounds(matchClassName).any { expectedClassName == it }
    }
    return false
}

private fun getNextLabel(uniqId: AtomicInteger): String {
    return "l${uniqId.getAndIncrement()}"
}

private fun getNextRegister(uniqId: AtomicInteger): String {
    return "r${uniqId.getAndIncrement()}"
}

private fun writeNextRegister(uniqId: AtomicInteger, output: OutputStream, definition: String): String {
    val reg = getNextRegister(uniqId)
    output.appendText("var $reg = ${definition};\n")
    return reg
}

private fun writeExpression(writerData: WriterData, expression: ParsedTokenExpression, output: OutputStream, uniqId: AtomicInteger): String {
    var expression = expression
    if(expression is ParsedTokenValueAccessor) {
        writerData.markGet(expression)
        while(true) {
            val mapping = writerData.accessorMappings.entries.find {
                val expression = expression as ParsedTokenValueAccessor
                it.key.name == expression.name && writerData.getAccessorType(it.key) == writerData.getAccessorType(expression) && it.key.scope == expression.scope
            }?.value
            if(mapping != null && expression != mapping) {
                expression = mapping
            }else {
                break
            }
        }
        if(expression.scope == ParsedTokenValueAccessorScope.CONTEXT) {
            return writeNextRegister(uniqId, output, "context.load(${writerData.getParamIndex(expression)})")
        }else if(expression.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
            val type = writerData.getAccessorType(expression)
            if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.IntegerValue(context.instruction().<Integer>get(${writerData.getParamIndex(expression)}))")
            }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.IntegerValue(context.instruction().<Boolean>get(${writerData.getParamIndex(expression)}) ? 1 : 0)")
            }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.DoubleValue(context.instruction().<Double>get(${writerData.getParamIndex(expression)}))")
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.lang.String") {
                return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.StringValue(context.instruction().<String>get(${writerData.getParamIndex(expression)}))")
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.util.UUID") {
                return writeNextRegister(uniqId, output, "context.fieldStorage().loadUUID(cw, context.instruction().<java.util.UUID>get(${writerData.getParamIndex(expression)}))")
            }else {
                throw IllegalStateException("Invalid accessor type for instruction scope: $type")
            }
        }else if(expression.scope == ParsedTokenValueAccessorScope.LOCAL) {
            if(writerData.localTypes[expression.name] == null) {
                throw IllegalStateException("Local variable '${expression.name}' not found")
            }
            return writeNextRegister(uniqId, output, "cw.cloneValue(${writerData.localTypes[expression.name]!!.mapping})")
        }else if(expression.scope == ParsedTokenValueAccessorScope.STATIC) {
            return writeNextRegister(uniqId, output, "context.getStatic(\"${expression.name}\")")
        }
    }else if(expression is ParsedTokenExpressionNumber) {
        return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.IntegerValue(${expression.number.toInt()})")
    }else if(expression is ParsedTokenExpressionBoolean) {
        return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.IntegerValue(${if(expression.value) 1 else 0})")
    }else if(expression is ParsedTokenExpressionTyped) {
        return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.NullValue()")
    }else if(expression is ParsedTokenExpressionBinary) {
        if(expression.op == ParsedTokenExpressionOperator.ADD) {
            if(evaluateExpressionType(writerData, expression).isNumber()) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.add(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SUB) {
            if(evaluateExpressionType(writerData, expression).isNumber()) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.sub(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.MUL) {
            if(evaluateExpressionType(writerData, expression).isNumber()) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.mul(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.DIV) {
            if(evaluateExpressionType(writerData, expression).isNumber()) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.div(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.MOD) {
            if(evaluateExpressionType(writerData, expression).isNumber()) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.mod(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SHR) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.LONG) || isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.shiftRight(${argA}, ${argB})")
            }
        }
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance != null) {
            val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
            val instance = expression.instance
            val functionMatches = if(instance is ParsedTokenInstanceValue) {
                val instanceType = evaluateExpressionType(writerData, instance.expression).let { if(it is ParsedTokenExpressionTypeGeneric && it.lowerBound.typeName != "java.lang.Number") ParsedTokenExpressionTypeComplex(it.lowerBound.typeName, emptyList()) else it }
                if(instanceType !is ParsedTokenExpressionTypeComplex) {
                    throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
                }
                val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }.toMutableList()
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
                }
                if(matches.size > 1) {
                    val closest = matches.map { it to checkClassDistance(it.owner.replace('/', '.').replace('$', '.'), instanceType.typeName) }.reduce { a, b -> if(a.second == null || a.second!! > b.second!!) b else a }
                    matches.clear()
                    matches.add(closest.first)
                }
                matches
            }else if(instance is ParsedTokenInstanceStatic) {
                val checkStatic = expression.name != "<init>"
                val matches = getClassAccessibleMethods(instance.className).filter { it.isStatic == checkStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): ${checkStatic} ${getClassAccessibleMethods(instance.className)}")
                }
                if(matches.size > 1) {
                    throw IllegalArgumentException("Found too many functions matching <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $matches")
                }
                matches
            }else {
                throw IllegalArgumentException("Cannot run function on unknown instance: $instance")
            }
            val def = functionMatches[0].run { if(owner == "java/lang/Object" && name == "<init>" && descriptor == "()V") ClassMethodInfo((instance as ParsedTokenInstanceStatic).className.replace('.', '/'), "<init>", "()V", false) else this }
            val classOwner = loadClassInfo(def.owner.replace('/', '.').replace('$', '.'))
            val className = def.owner
            val methodName = def.name
            val methodDescriptor = def.descriptor

            val allArguments = mutableListOf<String>()
            if(instance is ParsedTokenInstanceValue) {
                allArguments.add(writeExpression(writerData.copyLocalTypes(), instance.expression, output, uniqId))
            }
            for(arg in expression.arguments) {
                allArguments.add(writeExpression(writerData.copyLocalTypes(), arg, output, uniqId))
            }
            val functionTarget = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FunctionTarget(\"$className\", \"$methodName\", \"$methodDescriptor\", ${def.isStatic}, ${classOwner.isInterface}, ${expression.name == "<init>"}, false)"
            var index = writerData.targets.indexOf(functionTarget)
            if(index == -1) {
                index = writerData.targets.size
                writerData.targets.add(functionTarget)
            }
            return if(methodName == "<init>") {
                writeNextRegister(uniqId, output, "cw.newObjectCreate(TARGETS[$index]${allArguments.joinToString("") {", $it"}})")
            }else {
                writeNextRegister(uniqId, output, "cw.writeTarget(TARGETS[$index]${allArguments.joinToString("") {", $it"}})")
            }
        }else {
            if(expression.name == "cast") {
                val expr = writeExpression(writerData, expression.arguments[0], output, uniqId)
                val className = ((expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex).typeName
                return writeNextRegister(uniqId, output, "cw.castType($expr, org.objectweb.asm.Type.getType($className.class))")
            }
            if(expression.name == "map_advancement") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadAdvancement(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_attribute") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadAttribute(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_mob_effect") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadEffect(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_resource_location") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadIdentifier(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_nbt_value") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadNbtValue(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_scoreboard_criterion") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadScoreboardCriterion(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData>get(${writerData.getParamIndex(source)}).name())")
                    }
                }
            }
            if(expression.name == "map_text") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.text.Text") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadText(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.text.Text>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_text_style") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadTextStyle(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_predicate") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadPredicate(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "map_entity_type") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadEntityType(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
                    }
                }
            }
            if(expression.name == "to_double") {
                val expr = writeExpression(writerData, expression.arguments[0], output, uniqId)
                return writeNextRegister(uniqId, output, "cw.castType($expr, org.objectweb.asm.Type.DOUBLE_TYPE)")
            }
            if(expression.name == "to_float") {
                val expr = writeExpression(writerData, expression.arguments[0], output, uniqId)
                return writeNextRegister(uniqId, output, "cw.castType($expr, org.objectweb.asm.Type.FLOAT_TYPE)")
            }
            if(expression.name == "to_integer") {
                val exprType = evaluateExpressionType(writerData, expression.arguments[0])
                if(exprType == ParsedTokenExpressionSimpleType.BOOLEAN) {
                    return writeExpression(writerData, expression.arguments[0], output, uniqId)
                }
                val expr = writeExpression(writerData, expression.arguments[0], output, uniqId)
                return writeNextRegister(uniqId, output, "cw.castType($expr, org.objectweb.asm.Type.INT_TYPE)")
            }
            if(expression.name == "compile_eval") {
                val compileExprType = evaluateExpressionType(writerData, expression.arguments[0])
                val compileExpr = createCompileExpression(writerData, expression.arguments[0])
                if(compileExprType == ParsedTokenExpressionSimpleType.INTEGER) {
                    return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.IntegerValue(${compileExpr.toCompileString(writerData)})")
                }else if(compileExprType == ParsedTokenExpressionSimpleType.FLOAT) {
                    return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.FloatValue(${compileExpr.toCompileString(writerData)})")
                }else if(compileExprType == ParsedTokenExpressionSimpleType.DOUBLE) {
                    return writeNextRegister(uniqId, output, "new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.DoubleValue(${compileExpr.toCompileString(writerData)})")
                }
                throw UnsupportedOperationException("Cannot convert compile type to included type in compile_eval: $compileExprType | $compileExpr")
            }
            if(expression.name == "compile_or") {
                val condition = createCompileExpression(writerData, expression.arguments[0])
                val resultRegister = getNextRegister(uniqId)
                val bout = ByteArrayOutputStream()
                output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue $resultRegister;\n")
                output.appendText("if(${condition.toCompileString(writerData)}) {\n")
                bout.appendText("$resultRegister = ${writeExpression(writerData, expression.arguments[1], bout, uniqId)};\n")
                output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("    ") + "\n")
                output.appendText("}else {\n")
                bout.reset()
                bout.appendText("$resultRegister = ${writeExpression(writerData, expression.arguments[2], bout, uniqId)};\n")
                output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("    ") + "\n")
                output.appendText("}\n")
                return resultRegister
            }
            if(expression.name == "_loadMacro") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate") {
                        return writeNextRegister(uniqId, output, "context.instruction().<dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate>get(${writerData.getParamIndex(source)}).writeBytecode(context)")
                    }
                }
            }
            throw UnsupportedOperationException("Unknown function call: ${expression.name} / $expression")
        }
    }else if(expression is ParsedTokenExpressionField) {
        val instance = expression.instance
        val fieldMatch = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot access fields on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleFields(instanceType.typeName).filter { !it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field [${instanceType.typeName}].${expression.name}")
            }
            matches[0]
        }else if(instance is ParsedTokenInstanceStatic) {
            val matches = getClassAccessibleFields(instance.className).filter { it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field <${instance.className}>.${expression.name}")
            }
            matches[0]
        }else {
            throw IllegalArgumentException("Cannot access field on unknown instance: $instance")
        }
        val className = fieldMatch.owner
        val fieldName = fieldMatch.name
        val fieldDescriptor = fieldMatch.descriptor
        val allArguments = mutableListOf<String>()
        if(instance is ParsedTokenInstanceValue) {
            allArguments.add(writeExpression(writerData.copyLocalTypes(), instance.expression, output, uniqId))
        }
        val fieldTarget = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FieldTarget(\"$className\", \"$fieldName\", \"$fieldDescriptor\", ${fieldMatch.isStatic}, false)"
        var index = writerData.targets.indexOf(fieldTarget)
        if(index == -1) {
            index = writerData.targets.size
            writerData.targets.add(fieldTarget)
        }
        return writeNextRegister(uniqId, output, "cw.writeTarget(TARGETS[$index]${allArguments.joinToString("") {", $it"}})")
    }
    throw UnsupportedOperationException("Unsupported expression: $expression")
}

interface CompileTimeExpression {
    fun toCompileString(writerData: WriterData): String
}
data class CompileTimeExpressionString(val string: String) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        return "\"${string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}
object CompileTimeExpressionNull : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        return "null"
    }
}

enum class CompileTimeExpressionComparisonOperator {
    EQ, NE, GT, LT, GE, LE,
}
data class CompileTimeExpressionComparison(val comparison: CompileTimeExpressionComparisonOperator, val a: CompileTimeExpression, val b: CompileTimeExpression) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        val stringForm = when(comparison) {
            CompileTimeExpressionComparisonOperator.EQ -> "=="
            CompileTimeExpressionComparisonOperator.NE -> "!="
            CompileTimeExpressionComparisonOperator.GT -> ">"
            CompileTimeExpressionComparisonOperator.LT -> "<"
            CompileTimeExpressionComparisonOperator.GE -> ">="
            CompileTimeExpressionComparisonOperator.LE -> "<="
        }
        return "((${a.toCompileString(writerData)}) $stringForm (${b.toCompileString(writerData)}))"
    }
}
data class CompileTimeExpressionAccessor(val accessor: ParsedTokenValueAccessor) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        val index = writerData.getParamIndex(accessor)
        return "context.instruction().<${writerData.getAccessorType(accessor).getGenericClassName()}>get($index)"
    }
}
data class CompileTimeExpressionField(val instance: CompileTimeExpression?, val field: ClassFieldInfo) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        val accessInstance = instance?.toCompileString(writerData) ?: field.owner.replace("/", ".")
        return "$accessInstance.${field.name}"
    }
}
data class CompileTimeExpressionMethod(val instance: CompileTimeExpression?, val method: ClassMethodInfo, val arguments: List<CompileTimeExpression>) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        val accessInstance = instance?.toCompileString(writerData) ?: method.owner.replace("/", ".")
        return "$accessInstance.${method.name}(${arguments.joinToString(", ") { it.toCompileString(writerData) }})"
    }
}
data class CompileTimeExpressionCast(val type: ParsedTokenExpressionType, val expression: CompileTimeExpression) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        if(type == ParsedTokenExpressionSimpleType.FLOAT) {
            return "(float) (${expression.toCompileString(writerData)})"
        }
        TODO("cast into $type not implemented")
    }
}
data class CompileTimeExpressionSelect(val condition: CompileTimeExpression, val trueValue: CompileTimeExpression, val falseValue: CompileTimeExpression) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        return "((${condition.toCompileString(writerData)}) ? (${trueValue.toCompileString(writerData)}) : (${falseValue.toCompileString(writerData)}))"
    }
}
data class CompileTimeExpressionValueProvided(val accessor: ParsedTokenValueAccessor) : CompileTimeExpression {
    override fun toCompileString(writerData: WriterData): String {
        return "context.instruction().get(${writerData.getParamIndex(accessor)}) != null"
    }
}

private fun createCompileExpression(writerData: WriterData, expression: ParsedTokenExpression): CompileTimeExpression {
    if(expression is ParsedTokenExpressionString) {
        return CompileTimeExpressionString(expression.string)
    }else if(expression is ParsedTokenExpressionBinary) {
        if(expression.op == ParsedTokenExpressionOperator.EQ) {
            return CompileTimeExpressionComparison(CompileTimeExpressionComparisonOperator.EQ, createCompileExpression(writerData, expression.a), createCompileExpression(writerData, expression.b))
        }else if(expression.op == ParsedTokenExpressionOperator.NE) {
            return CompileTimeExpressionComparison(CompileTimeExpressionComparisonOperator.NE, createCompileExpression(writerData, expression.a), createCompileExpression(writerData, expression.b))
        }
    }else if(expression is ParsedTokenExpressionTyped) {
        if(expression.type == ParsedTokenExpressionSimpleType.NULL) {
            return CompileTimeExpressionNull
        }
    }else if(expression is ParsedTokenValueAccessor) {
        if(expression.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
            return CompileTimeExpressionAccessor(expression)
        }
    }else if(expression is ParsedTokenExpressionField) {
        val instance = expression.instance
        val fieldMatch = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot access fields on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleFields(instanceType.typeName).filter { !it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field [${instanceType.typeName}].${expression.name}")
            }
            matches[0]
        }else if(instance is ParsedTokenInstanceStatic) {
            val matches = getClassAccessibleFields(instance.className).filter { it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field <${instance.className}>.${expression.name}")
            }
            matches[0]
        }else {
            throw IllegalArgumentException("Cannot access field on an unknown instance: $instance")
        }
        return CompileTimeExpressionField(if(instance is ParsedTokenInstanceValue) createCompileExpression(writerData, instance.expression) else null, fieldMatch)
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance == null) {
            if(expression.name == "to_float") {
                val expr = createCompileExpression(writerData, expression.arguments[0])
                return CompileTimeExpressionCast(ParsedTokenExpressionSimpleType.FLOAT, expr)
            }else if(expression.name == "compile_or") {
                val condition = createCompileExpression(writerData, expression.arguments[0])
                val trueValue = createCompileExpression(writerData, expression.arguments[1])
                val falseValue = createCompileExpression(writerData, expression.arguments[2])
                return CompileTimeExpressionSelect(condition, trueValue, falseValue)
            }else if(expression.name == "compile_is_provided") {
                val param = expression.arguments[0]
                if(param is ParsedTokenValueAccessor) {
                    return CompileTimeExpressionValueProvided(param)
                }
            }
        }
        if(expression.instance != null) {
            val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
            val instance = expression.instance
            val functionMatches = if(instance is ParsedTokenInstanceValue) {
                val instanceType = evaluateExpressionType(writerData, instance.expression).let { if(it is ParsedTokenExpressionTypeGeneric && it.lowerBound.typeName != "java.lang.Number") ParsedTokenExpressionTypeComplex(it.lowerBound.typeName, emptyList()) else it }
                if(instanceType !is ParsedTokenExpressionTypeComplex) {
                    throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
                }
                val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }.toMutableList()
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
                }
                if(matches.size > 1) {
                    val closest = matches.map { it to checkClassDistance(it.owner.replace('/', '.').replace('$', '.'), instanceType.typeName) }.reduce { a, b -> if(a.second == null || a.second!! > b.second!!) b else a }
                    matches.clear()
                    matches.add(closest.first)
                }
                matches
            }else if(instance is ParsedTokenInstanceStatic) {
                val checkStatic = expression.name != "<init>"
                val matches = getClassAccessibleMethods(instance.className).filter { it.isStatic == checkStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $checkStatic ${getClassAccessibleMethods(instance.className)}")
                }
                if(matches.size > 1) {
                    throw IllegalArgumentException("Found too many functions matching <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $matches")
                }
                matches
            }else {
                throw IllegalArgumentException("Cannot run function on unknown instance: $instance")
            }
            val def = functionMatches[0]

            val allArguments = mutableListOf<CompileTimeExpression>()
            for(arg in expression.arguments) {
                allArguments.add(createCompileExpression(writerData.copyLocalTypes(), arg))
            }
            return CompileTimeExpressionMethod(if(instance is ParsedTokenInstanceValue) createCompileExpression(writerData, instance.expression) else null, def, allArguments)
        }else {
            throw UnsupportedOperationException("Unknown function call: ${expression.name} / $expression")
        }
    }
    throw IllegalStateException("Unsupported expression: $expression")
}

private fun doComparison(writerData: WriterData, expression: ParsedTokenExpression, output: OutputStream, failureLabel: String, uniqId: AtomicInteger) {
    if(!isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.BOOLEAN)) {
        throw IllegalStateException("comparison <expr> must return a boolean")
    }
    if(expression !is ParsedTokenExpressionBinary) {
        throw IllegalStateException("comparison must use a binary operator")
    }
    if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
        if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.NULL) xor isExpressionSimpleType(writerData, expression.b, ParsedTokenExpressionSimpleType.NULL)) {
            val nonNull = if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.NULL)) expression.b else expression.a
            val expr = writeExpression(writerData.copyLocalTypes(), nonNull, output, uniqId)
            if(expression.op == ParsedTokenExpressionOperator.NE) {
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.EQ, $expr, new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.NullValue(), $failureLabel);\n")
            }else {
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.NE, $expr, new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.NullValue(), $failureLabel);\n")
            }
        }else {
            val typeA = evaluateExpressionType(writerData, expression.a)
            val typeB = evaluateExpressionType(writerData, expression.b)
            if(typeA != typeB && (!typeA.isIntegerLikeType() || !typeB.isIntegerLikeType())) {
                throw IllegalStateException("if(<expr> ==/!= <expr>) only works if both <expr> have the same type")
            }
            if((isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.BOOLEAN)) || (typeA is ParsedTokenExpressionTypeComplex && typeB is ParsedTokenExpressionTypeComplex)) {
                val exprA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val exprB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                if(expression.op == ParsedTokenExpressionOperator.NE) {
                    output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.EQ, $exprA, $exprB, $failureLabel);\n")
                }else {
                    output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.NE, $exprA, $exprB, $failureLabel);\n")
                }
            }else {
                throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(writerData, expression.a)}")
            }
        }
    }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT || expression.op == ParsedTokenExpressionOperator.LE || expression.op == ParsedTokenExpressionOperator.GE) {
        if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.INTEGER) && isExpressionSimpleType(writerData, expression.b, ParsedTokenExpressionSimpleType.INTEGER)) {
            val exprA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
            val exprB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
            when(expression.op) {
                ParsedTokenExpressionOperator.LT -> output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.GE, $exprA, $exprB, $failureLabel);\n")
                ParsedTokenExpressionOperator.LE -> output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.GT, $exprA, $exprB, $failureLabel);\n")
                ParsedTokenExpressionOperator.GT -> output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.LE, $exprA, $exprB, $failureLabel);\n")
                ParsedTokenExpressionOperator.GE -> output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.LT, $exprA, $exprB, $failureLabel);\n")
            }
        }else {
            throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(writerData, expression.a)} ${expression.op} ${evaluateExpressionType(writerData, expression.b)}")
        }
    }else {
        throw IllegalArgumentException("Unsupported comparison binary operator: ${expression.op}")
    }
}

enum class ReturnTypeResult {
    CONTINUE,
    BREAK,
    RETURN,
}

private fun writeCode(writerData: WriterData, code: List<ParsedTokenStatement>, output: OutputStream, uniqId: AtomicInteger, insideCompileBlock: Boolean): ReturnTypeResult {
    for(el in code) {
        if(el is ParsedTokenStatementIf) {
            val failureLabel = getNextLabel(uniqId)
            output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel $failureLabel = cw.createLabel();\n")
            doComparison(writerData.copyLocalTypes(), el.expression, output, failureLabel, uniqId)
            val baseReturn = writeCode(writerData.copyLocalTypes(), el.code, output, uniqId, insideCompileBlock)
            if(el.elseCode != null) {
                val jumpLabel = getNextLabel(uniqId)
                output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel $jumpLabel = cw.createLabel();\n")
                output.appendText("cw.jump($jumpLabel);\n")
                output.appendText("cw.label($failureLabel);\n")
                val otherReturn = writeCode(writerData.copyLocalTypes(), el.elseCode, output, uniqId, insideCompileBlock)
                output.appendText("cw.label($jumpLabel);\n")
                if(baseReturn == ReturnTypeResult.RETURN && otherReturn == ReturnTypeResult.RETURN) {
                    output.appendText("return true;\n")
                    return ReturnTypeResult.RETURN
                }
                if(baseReturn != ReturnTypeResult.CONTINUE && otherReturn != ReturnTypeResult.CONTINUE) {
                    return ReturnTypeResult.BREAK
                }
            }else {
                output.appendText("cw.label($failureLabel);\n")
            }
            continue
        }else if(el is ParsedTokenStatementCompileIf) {
            val comparisonString = createCompileExpression(writerData.copyLocalTypes(), el.expression)
            output.appendText("if(${comparisonString.toCompileString(writerData)}) {\n")
            val bout = ByteArrayOutputStream()
            val baseReturn = writeCode(writerData.copyLocalTypes(), el.code, bout, uniqId, true)
            output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("    ") + "\n")
            output.appendText("}")
            if(el.elseCode != null) {
                output.appendText("else {\n")
                bout.reset()
                val otherReturn = writeCode(writerData.copyLocalTypes(), el.elseCode, bout, uniqId, true)
                output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("    ") + "\n")
                if(baseReturn == ReturnTypeResult.RETURN && otherReturn == ReturnTypeResult.RETURN) {
                    output.appendText("return true;\n")
                    return ReturnTypeResult.RETURN
                }
                if(baseReturn != ReturnTypeResult.CONTINUE && otherReturn != ReturnTypeResult.CONTINUE) {
                    return ReturnTypeResult.BREAK
                }
                output.appendText("}")
            }
            output.appendText("\n")
            continue
        }else if(el is ParsedTokenStatementCompileMatch) {
            val comparisonType = evaluateExpressionType(writerData.copyLocalTypes(), el.expression)
            val comparisonExpression = createCompileExpression(writerData.copyLocalTypes(), el.expression)
            if(comparisonType is ParsedTokenExpressionTypeComplex && loadClassInfo(comparisonType.typeName).isEnum) {
                output.appendText("switch(${comparisonExpression.toCompileString(writerData)}) {\n")
                for((case, code) in el.matches) {
                    output.appendText("    case $case -> {\n")
                    val bout = ByteArrayOutputStream()
                    writeCode(writerData.copyLocalTypes(), code, bout, uniqId, true)
                    output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("        ") + "\n")
                    output.appendText("    }\n")
                }
                output.appendText("}\n")
            }else {
                val comparisonReg = writeNextRegister(uniqId, output, comparisonExpression.toCompileString(writerData))
                val entries = el.matches.entries.toList()
                val casePrefix = if(comparisonType is ParsedTokenExpressionTypeComplex) "${comparisonType.typeName}." else ""
                for(i in entries.indices) {
                    val (case, code) = entries[i]
                    output.appendText("if(java.util.Objects.equals($comparisonReg, $casePrefix$case)) {\n")
                    val bout = ByteArrayOutputStream()
                    writeCode(writerData.copyLocalTypes(), code, bout, uniqId, true)
                    output.appendText(bout.toByteArray().decodeToString().trimEnd().prependIndent("    ") + "\n")
                    output.appendText("}")
                    if(i != entries.size - 1) {
                        output.appendText("else ")
                    }else {
                        output.appendText("\n")
                    }
                }
            }
            continue
        }else if(el is ParsedTokenStatementFunctionCall) {
            if(el.name == "_jump") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.jump(context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfNotInstanceOf") {
                val expr = writeExpression(writerData, el.arguments[0], output, uniqId)
                val className = ((el.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex).typeName
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.instanceCheckJump(false, ${expr}, org.objectweb.asm.Type.getType(${className}.class), context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfEqual") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.EQ, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfNotEqual") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.NE, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfGreaterEqual") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.GE, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfGreaterThan") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.GT, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfLessThan") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.LT, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_jumpIfLessEqual") {
                val exprA = writeExpression(writerData, el.arguments[0], output, uniqId)
                val exprB = writeExpression(writerData, el.arguments[1], output, uniqId)
                val scopeAccessor = el.arguments[2] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("cw.conditionalJump(dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition.LE, $exprA, $exprB, context.mapLabel(${writerData.getParamIndex(scopeAccessor)}));\n")
            }else if(el.name == "_throwCompileException") {
                val expressions = mutableListOf<String>()
                for(arg in el.arguments) {
                    expressions.add(createCompileExpression(writerData, arg).toCompileString(writerData))
                }
                output.appendText("throw new RuntimeException(${expressions.joinToString(" + ") { "$it+\"\"" }});\n")
                return ReturnTypeResult.RETURN
            }else if(el.name == "return_fail") {
                output.appendText("cw.returnValue(new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.LongValue(${1L shl 32}L));\n")
                return ReturnTypeResult.BREAK
            }else if(el.name == "return_success") {
                val sourceType = evaluateExpressionType(writerData, el.arguments[0])
                if(sourceType == ParsedTokenExpressionSimpleType.INTEGER) {
                    val expr = writeExpression(writerData, el.arguments[0], output, uniqId)
                    output.appendText("cw.returnValue(cw.castType($expr, org.objectweb.asm.Type.LONG_TYPE));\n")
                    return ReturnTypeResult.BREAK
                }else {
                    throw IllegalArgumentException("Invalid return type: $sourceType")
                }
            }else {
                writeExpression(writerData.copyLocalTypes(), el, output, uniqId)
            }
            continue
        }else if(el is ParsedTokenStatementAssignVariable) {
            val type = if(el.accessor.scope == ParsedTokenValueAccessorScope.LOCAL) evaluateExpressionType(writerData, el.expression) else writerData.getAccessorType(el.accessor)
            val expr = writeExpression(writerData.copyLocalTypes(), el.expression, output, uniqId)
            writerData.markSet(el.accessor)
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.INSTRUCTION -> throw IllegalArgumentException("Cannot assign values to instruction scope")
                ParsedTokenValueAccessorScope.STATIC -> {
                    output.appendText("context.putStatic(\"${el.accessor.name}\", $expr);\n")
                }
                ParsedTokenValueAccessorScope.CONTEXT -> {
                    output.appendText("context.store(${writerData.getParamIndex(el.accessor)}, $expr);\n")
                }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    if(writerData.localTypes[el.accessor.name] == null) {
                        val storageLocal = writeNextRegister(uniqId, output, "cw.createRegister()")
                        writerData.localTypes[el.accessor.name] = LocalInfo(storageLocal, type)
                    }
                    output.appendText("cw.setValue(${writerData.localTypes[el.accessor.name]!!.mapping}, $expr);\n")
                }
                ParsedTokenValueAccessorScope.RETURN -> throw IllegalArgumentException("Cannot assign values to return scope manually")
            }
            if(type is ParsedTokenExpressionTypeComplex) {
                val expressionType = evaluateExpressionType(writerData, el.expression)
                if(expressionType != ParsedTokenExpressionSimpleType.NULL) {
                    val expressionClasses = getClassLowerBounds((expressionType as ParsedTokenExpressionTypeComplex).typeName.replace('/', '.'))
                    if(!expressionClasses.contains(type.typeName)) {
                        throw IllegalArgumentException("Invalid assign, wrong type: $expressionType, expected $type")
                    }
                }
            }
            continue
        }else if(el is ParsedTokenStatementReturn) {
            if(el.value == null) {
                if(writerData.params.any { it.first.scope == ParsedTokenValueAccessorScope.RETURN }) {
                    throw IllegalArgumentException("Tried to return with no value, when there's a return type in the instruction definition")
                }
                if(insideCompileBlock) {
                    output.appendText("return true;\n")
                    return ReturnTypeResult.RETURN
                }
                return ReturnTypeResult.BREAK
            }
            val returnAccessor = writerData.params.find { it.first.scope == ParsedTokenValueAccessorScope.RETURN } ?: throw IllegalArgumentException("Tried to return with a value, when there's no return type in the instruction definition")
            val returnType = returnAccessor.second
            val expressionType = evaluateExpressionType(writerData, el.value)
            if(!doesTypeMatch(expressionType, returnType)) {
                throw IllegalArgumentException("Tried to return with a value, which doesn't match the return type of the instruction")
            }
            val expr = writeExpression(writerData, el.value, output, uniqId)
            output.appendText("context.store(${writerData.getParamIndex(returnAccessor.first)}, $expr);\n")
            if(insideCompileBlock) {
                output.appendText("return true;\n")
                return ReturnTypeResult.RETURN
            }
            return ReturnTypeResult.BREAK
        }
        throw Exception(el.toString())
    }
    return ReturnTypeResult.CONTINUE
}

fun generateCode(definition: ParsedTokenInstructionDefinition, file: ParsedFile, output: OutputStream, majorVersion: Int, minorVersion: Int, targets: MutableList<String>): Pair<List<AccessorStatistics>, Boolean> {
    val writerData = WriterData(file = file, params = definition.params, majorVersion = majorVersion, minorVersion = minorVersion, targets = targets)
    var writeNormalReturn = true
    if(definition.code.isNotEmpty()) {
        try {
            val hasReturnType = writerData.params.any { it.first.scope == ParsedTokenValueAccessorScope.RETURN }
            val returnKind = writeCode(writerData, definition.code, output, AtomicInteger(0), false)
            if(hasReturnType && returnKind == ReturnTypeResult.CONTINUE) {
                throw IllegalArgumentException("Not all branches return a value, required since the instruction definition has a return type")
            }
            writeNormalReturn = returnKind != ReturnTypeResult.RETURN
        }catch(e: Exception) {
            throw RuntimeException("Failed to write code for instruction definition ${definition.name}", e)
        }
    }
    return writerData.paramStatistics to writeNormalReturn
}

fun parseCode(inputCode: String) = parseTokens(lexTokens(inputCode).listIterator())