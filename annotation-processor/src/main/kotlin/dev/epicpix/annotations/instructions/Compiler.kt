package dev.epicpix.annotations.instructions

import dev.epicpix.annotations.*
import dev.epicpix.annotations.enums.EnumList
import org.objectweb.asm.Type
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

data class AccessorStatistics(val accessor: ParsedTokenValueAccessor, var gets: Boolean = false, var sets: Boolean = false)
data class LocalInfo(val mapping: String, val type: ParsedTokenExpressionType)

data class WriterData(
    val mappingData: MappingData?,
    val accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor> = mapOf(),
    val localTypes: MutableMap<String, LocalInfo> = mutableMapOf(),
    val enums: EnumList,
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
            if(param.first.scope == ParsedTokenValueAccessorScope.CONTEXT || param.first.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
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
        if(expression.op == ParsedTokenExpressionOperator.ADD || expression.op == ParsedTokenExpressionOperator.SUB || expression.op == ParsedTokenExpressionOperator.MUL) {
            if(a == ParsedTokenExpressionSimpleType.INTEGER && b == ParsedTokenExpressionSimpleType.INTEGER) {
                return ParsedTokenExpressionSimpleType.INTEGER
            }
            if(a == ParsedTokenExpressionSimpleType.DOUBLE && b == ParsedTokenExpressionSimpleType.DOUBLE) {
                return ParsedTokenExpressionSimpleType.DOUBLE
            }
        }else if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }else if(a.isObjectLike() && b.isObjectLike()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
        }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT || expression.op == ParsedTokenExpressionOperator.GE || expression.op == ParsedTokenExpressionOperator.LE) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
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
            if(expression.name == "cast" || expression.name == "map_enum") {
                return (expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex
            }
            if(expression.name == "map_attribute") {
                if(writerData.isAboveVersion(20, 4)) {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder")
                }else {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.world.entity.ai.attributes.Attribute")
                }
            }
            if(expression.name == "map_mob_effect") {
                if(writerData.isAboveVersion(20, 4)) {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder")
                }else {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.world.effect.MobEffect")
                }
            }
            if(expression.name == "map_resource_location") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.resources.ResourceLocation")
            }
            if(expression.name == "map_nbt_value") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.nbt.Tag")
            }
            if(expression.name == "map_scoreboard_criterion") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.world.scores.criteria.ObjectiveCriteria")
            }
            if(expression.name == "map_text") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.network.chat.Component")
            }
            if(expression.name == "to_double") {
                return ParsedTokenExpressionSimpleType.DOUBLE
            }
        }
        val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
        val instance = expression.instance
        val functionMatches = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
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
            throw IllegalArgumentException("Cannot run function on unknown instance: $instance")
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
            return ParsedTokenExpressionTypeComplex(def.className)
        }
        if(expression.name == "<init>") {
            if(functionMatches[0].owner == "java/lang/Object" && functionMatches[0].name == "<init>" && functionMatches[0].descriptor == "()V") {
                return ParsedTokenExpressionTypeComplex((instance as ParsedTokenInstanceStatic).className.replace('/', '.').replace('$', '.'))
            }
            return ParsedTokenExpressionTypeComplex(functionMatches[0].owner.replace('/', '.').replace('$', '.'))
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
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className)
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
        return matchType == ParsedTokenExpressionSimpleType.NULL || (matchType is ParsedTokenExpressionTypeComplex && (getClassLowerBounds(expectedType.className).any { matchType.typeName == it } || getClassLowerBounds(matchType.typeName).any { expectedType.className == it }))
    }else {
        TODO(expectedType.descriptor)
    }
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
        expression as ParsedTokenValueAccessor
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
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.add(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SUB) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.sub(${argA}, ${argB})")
            }
        }else if(expression.op == ParsedTokenExpressionOperator.MUL) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                val argA = writeExpression(writerData.copyLocalTypes(), expression.a, output, uniqId)
                val argB = writeExpression(writerData.copyLocalTypes(), expression.b, output, uniqId)
                return writeNextRegister(uniqId, output, "cw.mul(${argA}, ${argB})")
            }
        }
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance != null) {
            val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
            val instance = expression.instance
            val functionMatches = if(instance is ParsedTokenInstanceValue) {
                val instanceType = evaluateExpressionType(writerData, instance.expression)
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
            val className = writerData.mappingData.getClassName(def.owner)
            val methodName = writerData.mappingData.getMethodName(def.owner, def.name, def.descriptor)
            val methodDescriptor = remapType(Type.getMethodType(def.descriptor), writerData.mappingData).descriptor

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
            if(expression.name == "map_enum") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    val destType = ((expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex)
                    val enumMappings = writerData.enums.getEnumMappings(sourceType.typeName, destType.typeName)
                    if(enumMappings.isNotEmpty()) {
                        val reg = getNextRegister(uniqId)
                        output.appendText("var $reg = switch(context.instruction().<${sourceType.typeName}>get(${writerData.getParamIndex(source)})) {\n")
                        for((k, v) in enumMappings) {
                            output.appendText("    case $k -> {\n")
                            val fullClassName = v.substringBeforeLast('.')
                            val fieldName = v.substringAfterLast('.')
                            val expr = writeExpression(writerData, ParsedTokenExpressionField(fieldName, ParsedTokenInstanceStatic(fullClassName)), output, uniqId)
                            output.appendText("        yield $expr;\n")
                            output.appendText("    }\n")
                        }
                        output.appendText("};\n")
                        return reg
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
                        return writeNextRegister(uniqId, output, "context.fieldStorage().loadResourceLocation(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(${writerData.getParamIndex(source)}))")
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
            if(expression.name == "to_double") {
                val expr = writeExpression(writerData, expression.arguments[0], output, uniqId)
                return writeNextRegister(uniqId, output, "cw.castType($expr, org.objectweb.asm.Type.DOUBLE_TYPE)")
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
        val className = writerData.mappingData.getClassName(fieldMatch.owner)
        val fieldName = writerData.mappingData.getFieldName(fieldMatch.owner, fieldMatch.name, fieldMatch.descriptor)
        val fieldDescriptor = remapType(Type.getType(fieldMatch.descriptor), writerData.mappingData).descriptor
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
                else -> {}
            }
        }else {
            throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(writerData, expression.a)} ${expression.op} ${evaluateExpressionType(writerData, expression.b)}")
        }
    }else {
        throw IllegalArgumentException("Unsupported comparison binary operator: ${expression.op}")
    }
}

private fun writeCode(writerData: WriterData, code: List<ParsedTokenStatement>, output: OutputStream, uniqId: AtomicInteger) {
    for(el in code) {
        if(el is ParsedTokenStatementIf) {
            val failureLabel = getNextLabel(uniqId)
            output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel $failureLabel = cw.createLabel();\n")
            doComparison(writerData.copyLocalTypes(), el.expression, output, failureLabel, uniqId)
            writeCode(writerData.copyLocalTypes(), el.code, output, uniqId)
            if(el.elseCode != null) {
                val jumpLabel = getNextLabel(uniqId)
                output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel $jumpLabel = cw.createLabel();\n")
                output.appendText("cw.jump($jumpLabel);\n")
                output.appendText("cw.label($failureLabel);\n")
                writeCode(writerData.copyLocalTypes(), el.elseCode, output, uniqId)
                output.appendText("cw.label($jumpLabel);\n")
            }else {
                output.appendText("cw.label($failureLabel);\n")
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
            }else if(el.name == "return_fail") {
                output.appendText("cw.returnValue(new dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue.LongValue(${1L shl 32}L));\n")
            }else if(el.name == "return_success") {
                val sourceType = evaluateExpressionType(writerData, el.arguments[0])
                if(sourceType == ParsedTokenExpressionSimpleType.INTEGER) {
                    val expr = writeExpression(writerData, el.arguments[0], output, uniqId)
                    output.appendText("cw.returnValue(cw.castType($expr, org.objectweb.asm.Type.LONG_TYPE));\n")
                }else {
                    throw IllegalArgumentException("Invalid return type: $sourceType")
                }
            }else {
                writeExpression(writerData.copyLocalTypes(), el, output, uniqId)
            }
            continue
        }else if(el is ParsedTokenStatementDefineVariable) {
            val expr = if(el.expression != null) {
                writeExpression(writerData.copyLocalTypes(), el.expression, output, uniqId)
            }else null
            val type = if(el.accessor.scope == ParsedTokenValueAccessorScope.LOCAL) el.type!! else writerData.getAccessorType(el.accessor)
            val assignType = if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                "I"
            }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                "D"
            }else if(type == ParsedTokenExpressionSimpleType.FLOAT) {
                "F"
            }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                "Z"
            }else if(type is ParsedTokenExpressionTypeComplex) {
                "L${writerData.mappingData.getClassName(type.typeName.replace('.', '/'))};"
            }else throw UnsupportedOperationException("Unknown type: ${el.accessor} : $type")
            writerData.markSet(el.accessor)
            val storageLocal = writeNextRegister(uniqId, output, "cw.createRegister()")
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.INSTRUCTION -> throw IllegalArgumentException("Cannot assign values to instruction scope")
                ParsedTokenValueAccessorScope.CONTEXT -> {
                    output.appendText("context.store(${writerData.getParamIndex(el.accessor)}, $storageLocal);\n")
                }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    writerData.localTypes[el.accessor.name] = LocalInfo(storageLocal, el.type!!)
                }
                ParsedTokenValueAccessorScope.STATIC -> {
                    output.appendText("context.putStatic(\"${el.accessor.name}\", $storageLocal);\n")
                }
            }
            if(el.expression != null) {
                if(assignType.first() == 'L') {
                    val expressionType = evaluateExpressionType(writerData, el.expression)
                    if(expressionType != ParsedTokenExpressionSimpleType.NULL) {
                        val expressionClasses = getClassLowerBounds((expressionType as ParsedTokenExpressionTypeComplex).typeName.replace('/', '.'))
                        if(!expressionClasses.contains((type as ParsedTokenExpressionTypeComplex).typeName)) {
                            throw IllegalArgumentException("Invalid assign, wrong type: $expressionType, expected $type")
                        }
                    }
                }else if(assignType.first() != 'I' && assignType.first() != 'Z' && assignType.first() != 'D' && assignType.first() != 'F') {
                    throw IllegalStateException("Unknown assign type: $assignType")
                }
                output.appendText("cw.setValue($storageLocal, $expr);\n")
            }
            continue
        }else if(el is ParsedTokenStatementAssignVariable) {
            val expr = writeExpression(writerData.copyLocalTypes(), el.expression, output, uniqId)
            writerData.markSet(el.accessor)
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.INSTRUCTION -> throw IllegalArgumentException("Cannot assign values to instruction scope")
                ParsedTokenValueAccessorScope.STATIC -> throw IllegalArgumentException("Cannot reassign values in static scope")
                ParsedTokenValueAccessorScope.CONTEXT -> {
                    output.appendText("context.store(${writerData.getParamIndex(el.accessor)}, $expr);\n")
                }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    output.appendText("cw.setValue(${writerData.localTypes[el.accessor.name]!!.mapping}, $expr);\n")
                }
            }
            continue
        }
        throw Exception(el.toString())
    }
}

fun generateCode(mappingData: MappingData?, definition: ParsedTokenInstructionDefinition, file: ParsedFile, output: OutputStream, enums: EnumList, majorVersion: Int, minorVersion: Int, targets: MutableList<String>): List<AccessorStatistics> {
    val bout = ByteArrayOutputStream()
    val writerData = WriterData(mappingData = mappingData, file = file, params = definition.params, enums = enums, majorVersion = majorVersion, minorVersion = minorVersion, targets = targets)
    if(definition.code.isNotEmpty()) {
        try {
            writeCode(writerData, definition.code, bout, AtomicInteger(0))
        }catch(e: Exception) {
            throw RuntimeException("Failed to write code for instruction definition ${definition.name}", e)
        }
    }
    output.write(bout.toByteArray())
    return writerData.paramStatistics
}

fun parseCode(inputCode: String) = parseTokens(lexTokens(inputCode).listIterator())