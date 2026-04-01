package dev.epicpix.annotations.commands

import dev.epicpix.annotations.snakeToLowerCamelCase
import java.util.concurrent.atomic.AtomicInteger

data class ParsedCommandArgument(
    val children: MutableMap<String, ParsedCommandArgument> = mutableMapOf(),
    val argName: String? = null,
    val kind: String = "literal",
    val kindOptions: MutableMap<String, String> = mutableMapOf(),
    val kindDefaults: String? = null,
    var executableCommand: String? = null,
    var versionRange: Pair<String?, String?>? = null,
    var types: MutableList<Pair<String, String>> = mutableListOf(),
)

data class ParsedCommandData(
    val commandRedirects: List<Pair<String, String>>,
    val commands: List<Pair<String, String>>,
    val commandTree: MutableMap<String, ParsedCommandArgument>,
)

private fun parseCommandPart(parts: List<String>, current: ParsedCommandArgument, commandId: String, range: Pair<String?, String?>?) {
    if(range == null && current.versionRange != null) {
        current.versionRange = null
    }
    if(parts.isEmpty()) {
        current.versionRange = range
        current.executableCommand = commandId
    }else {
        val nextPart = parts[0]
        parseCommandPart(parts.subList(1, parts.size), current.children.getOrPut(nextPart) {
            if(nextPart.startsWith("<")) {
                val inner = nextPart.substring(1, nextPart.length - 1)
                val split = inner.split(":")
                val kindAll = split[1]
                val kind = kindAll.substringBefore('[')
                val kindOptions = kindAll.substringAfter('[', missingDelimiterValue = "").substringBeforeLast(']').split(",")
                val options = mutableMapOf<String, String>()
                for(opt in kindOptions) {
                    if(opt.isNotBlank()) {
                        val eq = opt.split("=", limit = 2)
                        if(eq.size == 1) {
                            options[opt] = "true"
                        } else {
                            options[eq[0]] = eq[1]
                        }
                    }
                }
                ParsedCommandArgument(argName = split[0], kind = kind, kindOptions = options, kindDefaults = split.getOrNull(2), versionRange = range)
            }else {
                ParsedCommandArgument(versionRange = range)
            }
        }, commandId, range)
    }
}

private fun parseCommandLine(command: String, tree: MutableMap<String, ParsedCommandArgument>, commandId: String) {
    val parts = command.split(" ").toMutableList()
    val cmd = parts[0]
    val argument = tree.getOrPut(cmd) { ParsedCommandArgument() }
    val split = commandId.split("<")
    val range = if(split.size > 1) {
        val range = split[1].split(">")[0].split("..", limit = 2)
        if(range.size == 1) {
            range[0] to range[0]
        }else {
            if(range[0].isEmpty()) {
                null to range[1]
            }else {
                range[0] to null
            }
        }
    }else null
    parseCommandPart(parts.subList(1, parts.size), argument, split[0], range)
}

fun parseCommandsData(file: String): ParsedCommandData {
    val commandRedirects = mutableListOf<Pair<String, String>>()
    val commands = mutableListOf<Pair<String, String>>()
    val commandTree = mutableMapOf<String, ParsedCommandArgument>()

    for(line in file.lineSequence()) {
        val trimmedLine = line.trim()

        if(trimmedLine.isBlank() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
            continue
        }

        val slashPart = line.split("/", limit = 2)

        if(slashPart.size < 2) {
            throw IllegalStateException("Syntax error: missing '/' in command definition line: '$line'")
        }

        if(slashPart[0] == "=DEFINE") {
            val aliasData = slashPart[1].split("/", limit = 2)
            commandRedirects.add(aliasData[0] to aliasData[1])
        } else {
            val baseCommand = slashPart[0].split("<")[0]
            commands.add(baseCommand to slashPart[1])
            parseCommandLine(slashPart[1], commandTree, slashPart[0])
        }
    }
    return ParsedCommandData(commandRedirects, commands, commandTree)
}

private fun generateCommandReturn(builder: StringBuilder, commandTypes: MutableMap<String, List<Pair<String, String>>>, executableCommand: String, arguments: Map<String, String>, typedArguments: List<Pair<String, String>>, forced: Boolean = false) {
    commandTypes[executableCommand] = typedArguments
    val paramsClassName = "_COMMAND_$executableCommand".snakeToLowerCamelCase()
    if(!forced) {
        builder.append("    if(!reader.canRead()) {\n")
        builder.append("    ")
    }
    builder.append("    return DiagnosticResult.result(new dev.epicpix.minecraftfunctioncompiler.commands.params.$paramsClassName(")
    var index = 0
    for((t, _) in typedArguments) {
        index++
        builder.append(arguments[t])
        if(index != typedArguments.size) {
            builder.append(",")
        }
    }
    builder.append("));\n")
    if(!forced) {
        builder.append("    }\n")
    }
}

private fun generateArgumentReaderChildren(name: String, data: ParsedCommandArgument, builder: StringBuilder, commandTypes: MutableMap<String, List<Pair<String, String>>>, uniqueId: AtomicInteger, argumentPath: String, argumentDPath: String, arguments: Map<String, String>, typedArguments: List<Pair<String, String>>, indent: String, catchBlock: String?) {
    var reachable = true
    val entries = data.children.entries.toList().sortedBy { it.value.kind != "literal" }
    for(i in entries.indices) {
        val (k, v) = entries[i]
        if(reachable) {
            val innerBuilder = StringBuilder()
            val suppressException = entries.subList(i + 1, entries.size).any { it.value.kind != "literal" } && v.kind != "literal"
            if(suppressException) {
                val locationIndex = uniqueId.getAndIncrement()
                val tryIndex = uniqueId.getAndIncrement()
                builder.append("${indent}var z$locationIndex = reader.getIndex();\n")
                builder.append("$indent{\n")
                builder.append("$indent    t$tryIndex: try {\n")
                if(generateArgumentReaderVersionCheck(k, v, innerBuilder, commandTypes, uniqueId, "$argumentPath $name", "$argumentDPath.${v.argName ?: k}", arguments, typedArguments, "t$tryIndex")) {
                    innerBuilder.append("reader.setIndex(z$locationIndex);\n")
                }
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("$indent        ") + "\n")
                builder.append("$indent    } catch(DiagnosticException ignored) {}\n")
                builder.append("$indent    reader.setIndex(z$locationIndex);\n")
                builder.append("$indent}\n")
            }else {
                reachable = generateArgumentReaderVersionCheck(k, v, innerBuilder, commandTypes, uniqueId, "$argumentPath $name", "$argumentDPath.${v.argName ?: k}", arguments, typedArguments, catchBlock)
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent(indent) + "\n")
            }
        }
    }
    if(reachable) {
        builder.append("    ${returnWrappedError(catchBlock, argumentDPath, "Failed to parse any options @$argumentPath $name")}\n")
    }
}

private fun generateArgumentReaderVersionCheck(name: String, data: ParsedCommandArgument, builder: StringBuilder, commandTypes: MutableMap<String, List<Pair<String, String>>>, uniqueId: AtomicInteger, argumentPath: String, argumentDPath: String, arguments: Map<String, String>, typedArguments: List<Pair<String, String>>, catchBlock: String?, fromRoot: Boolean = false): Boolean {
    if(data.versionRange != null) {
        val innerBuilder = StringBuilder()
        val min = data.versionRange!!.first.let { if(it == null) "null" else "MinecraftVersion.MC_${it.replace('.', '_')}" }
        val max = data.versionRange!!.second.let { if(it == null) "null" else "MinecraftVersion.MC_${it.replace('.', '_')}" }
        builder.append("if(MinecraftVersion.isCurrentVersionInRange($min, $max)) {\n")
        generateArgumentReader(name, data, innerBuilder, commandTypes, uniqueId, argumentPath, argumentDPath, arguments, typedArguments, catchBlock, fromRoot)
        builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
        builder.append("}\n")
        return true
    }else {
        return generateArgumentReader(name, data, builder, commandTypes, uniqueId, argumentPath, argumentDPath, arguments, typedArguments, catchBlock, fromRoot)
    }
}

private fun generateArgumentReader(name: String, data: ParsedCommandArgument, builder: StringBuilder, commandTypes: MutableMap<String, List<Pair<String, String>>>, uniqueId: AtomicInteger, argumentPath: String, argumentDPath: String, arguments: Map<String, String>, typedArguments: List<Pair<String, String>>, catchBlock: String?, fromRoot: Boolean = false): Boolean {
    if(fromRoot) {
        if(data.executableCommand != null) {
            commandTypes[data.executableCommand!!] = typedArguments
            val paramsClassName = "_COMMAND_${data.executableCommand}".snakeToLowerCamelCase()
            builder.append("if(!reader.canRead()) return DiagnosticResult.result(new dev.epicpix.minecraftfunctioncompiler.commands.params.$paramsClassName());\n")
        }
        generateArgumentReaderChildren(name, data, builder, commandTypes, uniqueId, argumentPath, argumentDPath, arguments, typedArguments, "", catchBlock)
        return true
    }else if(data.kind != "literal") {
        val kindInfo = data.kind +
            (if(data.kindOptions.isEmpty()) "" else ("[" + data.kindOptions.map { "${it.key}=${it.value}" }.joinToString(",") + "]")) +
            (if(data.kindDefaults == null) "" else ":${data.kindDefaults}")
        builder.append("{ // ${data.argName} <${kindInfo}>\n")
        val argumentIndex = uniqueId.getAndIncrement()
        val newTypedArguments = typedArguments.toMutableList()
        when(data.kind) {
            "attribute" -> {
                builder.append("    var a$argumentIndex = reader.readAttribute();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.AttributeData")
            }
            "boolean" -> {
                builder.append("    var a$argumentIndex = reader.readBoolean();\n")
                newTypedArguments.add(data.argName!! to "boolean")
            }
            "command" -> {
                val root = data.kindOptions["root"]
                val tempIndex = uniqueId.getAndIncrement()
                if(root != null) {
                    builder.append("    var a$tempIndex = parseCommand_$root(reader, lookup);\n")
                }else {
                    builder.append("    var a$tempIndex = parseCommand(reader, lookup);\n")
                }
                builder.append("    if(a$tempIndex.isError()) return a$tempIndex;\n")
                builder.append("    var a$argumentIndex = a$tempIndex.getResult();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams")
            }
            "difficulty" -> {
                builder.append("    var a$argumentIndex = reader.readDifficulty();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.Difficulty")
            }
            "double" -> {
                builder.append("    var a$argumentIndex = reader.readDouble();\n")
                data.kindOptions["minimum"]?.toDouble()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${returnError(catchBlock, argumentDPath, "\"Double value of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toDouble()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${returnError(catchBlock, argumentDPath, "\"Double value of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
                newTypedArguments.add(data.argName!! to "double")
            }
            "entity_selector" -> {
                val allowMultiple = data.kindOptions.getOrDefault("multiple", "true").toBoolean()
                val playersOnly = data.kindOptions.getOrDefault("players", "false").toBoolean()
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument")
                if(data.executableCommand != null && data.kindDefaults == "self") {
                    generateCommandReturn(builder, commandTypes, data.executableCommand!!, arguments + mapOf(data.argName to "SelectorArgument.ofSelf()"), newTypedArguments)
                }
                builder.append("    var a$argumentIndex = SelectorParser.parseSelector(reader, ${!playersOnly}, $allowMultiple);\n")
            }
            "function" -> {
                builder.append("    var a$argumentIndex = lookup.lookupFunctions(reader.readTag());\n")
                newTypedArguments.add(data.argName!! to "java.util.List<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>")
            }
            "gamemode" -> {
                builder.append("    var a$argumentIndex = reader.readGamemode();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.Gamemode")
            }
            "greedy_string" -> {
                builder.append("    var a$argumentIndex = reader.readRemainder();\n")
                newTypedArguments.add(data.argName!! to "java.lang.String")
            }
            "int" -> {
                builder.append("    var a$argumentIndex = reader.readInt();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${returnError(catchBlock, argumentDPath, "\"Integer value of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${returnError(catchBlock, argumentDPath, "\"Integer value of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
                newTypedArguments.add(data.argName!! to "int")
            }
            "int_range" -> {
                builder.append("    var a$argumentIndex = reader.readIntRange();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex.minimum() < $min) ${returnError(catchBlock, argumentDPath, "\"Integer range minimum of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex.maximum() > $max) ${returnError(catchBlock, argumentDPath, "\"Integer range maximum of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument")
            }
            "location_anchor" -> {
                builder.append("    var a$argumentIndex = reader.readLocationAnchor();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor")
            }
            "macro_int" -> {
                var requests = mutableListOf<String>()
                data.kindOptions["minimum"]?.toInt()?.also { min -> requests.add("dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators.checkIfIntegerMinimum(${min})") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> requests.add("dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators.checkIfIntegerMaximum(${max})") }
                builder.append("    var a$argumentIndex = reader.readMacroInt(List.of(${requests.joinToString(", ")}));\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex.isStatic() && a$argumentIndex.getStaticValue() < $min) ${returnError(catchBlock, argumentDPath, "\"Integer value of \" + a$argumentIndex.getStaticValue() + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex.isStatic() && a$argumentIndex.getStaticValue() > $max) ${returnError(catchBlock, argumentDPath, "\"Integer value of \" + a$argumentIndex.getStaticValue() + \" is above allowed value ($max)\"")}\n") }
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate<Integer>")
            }
            "mob_effect" -> {
                builder.append("    var a$argumentIndex = reader.readMobEffect();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.MobEffectData")
            }
            "nbt_compound" -> {
                builder.append("    var a$argumentIndex = NbtParser.readNbtCompound(reader);\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue.Compound")
            }
            "nbt_data_location" -> {
                builder.append("    var a$argumentIndex = reader.readNbtDataLocation();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation")
            }
            "nbt_path" -> {
                builder.append("    var a$argumentIndex = NbtParser.readNbtPath(reader);\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath")
            }
            "nbt_value" -> {
                builder.append("    var a$argumentIndex = NbtParser.readNbtValue(reader);\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue")
            }
            "objective" -> {
                builder.append("    var a$argumentIndex = reader.readWord();\n")
                newTypedArguments.add(data.argName!! to "java.lang.String")
            }
            "resource_location" -> {
                builder.append("    var a$argumentIndex = reader.readDataLocation();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.DataLocation")
            }
            "result_store_location_type" -> {
                builder.append("    var a$argumentIndex = reader.readResultStoreLocationType();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ResultStoreLocationType")
            }
            "result_store_type" -> {
                builder.append("    var a$argumentIndex = reader.readResultStoreType();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ResultStoreType")
            }
            "rotation" -> {
                builder.append("    var a$argumentIndex = reader.readRotation();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.RotationCoordinates")
            }
            "score_comparison" -> {
                builder.append("    var a$argumentIndex = reader.readScoreboardCompareOperation();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCompareOperation")
            }
            "score_holder" -> {
                builder.append("    var a$argumentIndex = SelectorParser.parseScoreHolder(reader, true);\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument")
            }
            "score_accessor" -> {
                builder.append("    var a$argumentIndex = reader.readScoreAccessor();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ScoreAccessorArgument")
            }
            "scoreboard_operation" -> {
                builder.append("    var a$argumentIndex = reader.readScoreboardOperation();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.ScoreboardOperation")
            }
            "swizzle" -> {
                builder.append("    var a$argumentIndex = reader.readSwizzle();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument")
            }
            "text" -> {
                builder.append("    var a$argumentIndex = TextParser.readText(reader);\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.text.Text")
            }
            "time" -> {
                builder.append("    var a$argumentIndex = reader.readTime();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${returnError(catchBlock, argumentDPath, "\"Time value of \" + a$argumentIndex + \" ticks is below allowed value ($min ticks)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${returnError(catchBlock, argumentDPath, "\"Time value of \" + a$argumentIndex + \" ticks is above allowed value ($max ticks)\"")}\n") }
                newTypedArguments.add(data.argName!! to "int")
            }
            "word" -> {
                builder.append("    var a$argumentIndex = reader.readWord();\n")
                newTypedArguments.add(data.argName!! to "java.lang.String")
            }
            "slot" -> {
                builder.append("    var a$argumentIndex = reader.readUntilSpace();\n")
                newTypedArguments.add(data.argName!! to "java.lang.String")
            }
            "block_predicate" -> {
                builder.append("    var a$argumentIndex = reader.readBlockPredicate();\n")
                newTypedArguments.add(data.argName!! to "java.lang.String")
            }
            "vec3" -> {
                builder.append("    var a$argumentIndex = reader.readVec3();\n")
                newTypedArguments.add(data.argName!! to "dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates")
            }
            else -> {
                TODO("Reading '$kindInfo' kind is not implemented @$argumentPath <${data.argName}>")
            }
        }
        val newArguments = arguments + mapOf(data.argName to "a$argumentIndex")
        if(data.kind == "command") {
            generateCommandReturn(builder, commandTypes, data.executableCommand!!, newArguments, newTypedArguments)
            builder.append("}\n")
            return false
        }
        if(data.executableCommand != null) {
            generateCommandReturn(builder, commandTypes, data.executableCommand!!, newArguments, newTypedArguments)
            if(data.children.isEmpty()) {
                builder.append("    ${returnWrappedError(catchBlock, argumentDPath, "Too many arguments for @$argumentPath $name")}\n")
                builder.append("}\n")
                return false
            }
        }
        builder.append("    if(!reader.skipArgumentWhitespace()) ${returnWrappedError(catchBlock, argumentDPath, "Missing space after argument @$argumentPath $name")}\n")
        generateArgumentReaderChildren(name, data, builder, commandTypes, uniqueId, argumentPath, argumentDPath, newArguments, newTypedArguments, "    ", catchBlock)
        builder.append("}\n")
        return false
    }else {
        val locationIndex = uniqueId.getAndIncrement()
        builder.append("{ // $name\n")
        builder.append("    var z$locationIndex = reader.getIndex();\n")
        builder.append("    if(reader.canRead() && reader.readWord().equals(\"$name\")) {\n")
        if(data.executableCommand != null) {
            generateCommandReturn(builder, commandTypes, data.executableCommand!!, arguments, typedArguments)
        }
        builder.append("        if(!reader.skipArgumentWhitespace()) ${returnWrappedError(catchBlock, argumentDPath, "Missing space after argument @$argumentPath $name")}\n")
        generateArgumentReaderChildren(name, data, builder, commandTypes, uniqueId, argumentPath, argumentDPath, arguments, typedArguments, "        ", catchBlock)
        builder.append("    }\n")
        builder.append("    reader.setIndex(z$locationIndex);\n")
        builder.append("}\n")
        return true
    }
}

private fun createError(argumentDPath: String?, str: String) = "Diagnostic.parseError(\"command.error.parse${if(argumentDPath != null) ".$argumentDPath" else ""}\", reader.getSource(), reader.getString(), $str)"
private fun createWrappedError(argumentDPath: String?, str: String) = createError(argumentDPath, "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"")
private fun returnError(catchBlock: String?, argumentDPath: String?, str: String) = if(catchBlock != null) "break $catchBlock;" else "return DiagnosticResult.error(${createError(argumentDPath, str)});"
private fun returnWrappedError(catchBlock: String?, argumentDPath: String?, str: String) = if(catchBlock != null) "break $catchBlock;" else "return DiagnosticResult.error(${createWrappedError(argumentDPath, str)});"

fun generateCommandsDataReader(data: ParsedCommandData): Pair<String, Map<String, List<Pair<String, String>>>> {
    val builder = StringBuilder()
    val uid = AtomicInteger()
    builder.append("public static DiagnosticResult<dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams> parseCommand(String command, DataLocation source, boolean allowMacros, IArgumentLookup lookup) {\n")
    builder.append("    try {\n")
    builder.append("        return parseCommand(new StringCommandReader(command, source, allowMacros), lookup);\n")
    builder.append("    } catch (dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException e) {\n")
    builder.append("        return DiagnosticResult.error(e.toDiagnostic());\n")
    builder.append("    }\n")
    builder.append("}\n")
    builder.append("\n")
    builder.append("public static DiagnosticResult<dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams> parseCommand(StringCommandReader reader, IArgumentLookup lookup) {\n")
    builder.append("    var commandName = reader.readWord();\n")
    builder.append("    if(!reader.skipArgumentWhitespace()) ${returnError(null, null, "\"Missing space after argument @ \" + commandName")}\n")
    builder.append("    return switch(commandName) {\n")
    for((k, _) in data.commandTree) {
        builder.append("        case \"$k\" -> parseCommand_$k(reader, lookup);\n")
    }
    for((k, v) in data.commandRedirects) {
        builder.append("        case \"$k\" -> parseCommand_$v(reader, lookup);\n")
    }
    builder.append("        default -> DiagnosticResult.error(${createError(null, "\"Unknown command: \" + commandName")});\n")
    builder.append("    };\n")
    builder.append("}\n")
    builder.append("\n")
    val commandTypes = mutableMapOf<String, List<Pair<String, String>>>()
    for((k, v) in data.commandTree) {
        builder.append("public static DiagnosticResult<dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams> parseCommand_$k(StringCommandReader reader, IArgumentLookup lookup) {\n")
        val innerBuilder = StringBuilder()
        generateArgumentReaderVersionCheck(k, v, innerBuilder, commandTypes, uid, "", k, mapOf(), listOf(), null, true)
        builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
        builder.append("}\n")
    }
    return builder.toString() to commandTypes
}