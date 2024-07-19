package dev.epicpix.annotations.commands

import java.util.concurrent.atomic.AtomicInteger

data class ParsedCommandArgument(
    val children: MutableMap<String, ParsedCommandArgument> = mutableMapOf(),
    val argName: String? = null,
    val kind: String = "literal",
    val kindOptions: MutableMap<String, String> = mutableMapOf(),
    val kindDefaults: String? = null,
    var executableCommand: String? = null,
    var versionRange: Pair<String?, String?>? = null,
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
        if(line.isBlank()) continue
        val slashPart = line.split("/", limit = 2)
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

private fun generateCommandReturn(builder: StringBuilder, uniqueId: AtomicInteger, executableCommand: String, arguments: Map<String, String>, forced: Boolean = false) {
    if(!forced) {
        builder.append("    if(!reader.canRead()) {\n")
        val hashMapIndex = uniqueId.getAndIncrement()
        builder.append("        var y$hashMapIndex = new HashMap<String, Object>();\n")
        for((k, v) in arguments) {
            builder.append("        y$hashMapIndex.put(\"$k\", $v);\n")
        }
        builder.append("        return new Command(CommandType.${executableCommand}, y$hashMapIndex);\n")
        builder.append("    }\n")
    }else {
        val hashMapIndex = uniqueId.getAndIncrement()
        builder.append("    var y$hashMapIndex = new HashMap<String, Object>();\n")
        for((k, v) in arguments) {
            builder.append("    y$hashMapIndex.put(\"$k\", $v);\n")
        }
        builder.append("    return new Command(CommandType.${executableCommand}, y$hashMapIndex);\n")
    }
}

private fun generateArgumentReaderChildren(name: String, data: ParsedCommandArgument, builder: StringBuilder, uniqueId: AtomicInteger, argumentPath: String, arguments: Map<String, String>, indent: String) {
    var reachable = true
    val entries = data.children.entries.toList().sortedBy { it.value.kind != "literal" }
    for(i in entries.indices) {
        val (k, v) = entries[i]
        if(reachable) {
            val innerBuilder = StringBuilder()
            val suppressException = entries.subList(i + 1, entries.size).any { it.value.kind != "literal" } && v.kind != "literal"
            if(suppressException) {
                val locationIndex = uniqueId.getAndIncrement()
                builder.append("${indent}var z$locationIndex = reader.getIndex();\n")
                builder.append("${indent}try {\n")
                if(generateArgumentReaderVersionCheck(k, v, innerBuilder, uniqueId, "$argumentPath $name", arguments)) {
                    innerBuilder.append("reader.setIndex(z$locationIndex);\n")
                }
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("$indent    ") + "\n")
                builder.append("$indent} catch(CommandErrorException ignored) {\n")
                builder.append("$indent    reader.setIndex(z$locationIndex);\n")
                builder.append("$indent}\n")
            }else {
                reachable = generateArgumentReaderVersionCheck(k, v, innerBuilder, uniqueId, "$argumentPath $name", arguments)
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent(indent) + "\n")
            }
        }
    }
    if(reachable) {
        builder.append("    ${createWrappedError("Failed to parse any options @$argumentPath $name")}\n")
    }
}

private fun generateArgumentReaderVersionCheck(name: String, data: ParsedCommandArgument, builder: StringBuilder, uniqueId: AtomicInteger, argumentPath: String, arguments: Map<String, String>, fromRoot: Boolean = false): Boolean {
    if(data.versionRange != null) {
        val innerBuilder = StringBuilder()
        val min = data.versionRange!!.first.let { if(it == null) "null" else "MinecraftVersion.MC_${it.replace('.', '_')}" }
        val max = data.versionRange!!.second.let { if(it == null) "null" else "MinecraftVersion.MC_${it.replace('.', '_')}" }
        builder.append("if(MinecraftVersion.isCurrentVersionInRange($min, $max)) {\n")
        generateArgumentReader(name, data, innerBuilder, uniqueId, argumentPath, arguments, fromRoot)
        builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
        builder.append("}\n")
        return true
    }else {
        return generateArgumentReader(name, data, builder, uniqueId, argumentPath, arguments, fromRoot)
    }
}

private fun generateArgumentReader(name: String, data: ParsedCommandArgument, builder: StringBuilder, uniqueId: AtomicInteger, argumentPath: String, arguments: Map<String, String>, fromRoot: Boolean = false): Boolean {
    if(fromRoot) {
        if(data.executableCommand != null) {
            builder.append("if(!reader.canRead()) return new Command(CommandType.${data.executableCommand}, Map.of());\n")
        }
        generateArgumentReaderChildren(name, data, builder, uniqueId, argumentPath, arguments, "")
        return true
    }else if(data.kind != "literal") {
        val kindInfo = data.kind +
            (if(data.kindOptions.isEmpty()) "" else ("[" + data.kindOptions.map { "${it.key}=${it.value}" }.joinToString(",") + "]")) +
            (if(data.kindDefaults == null) "" else ":${data.kindDefaults}")
        builder.append("{ // ${data.argName} <${kindInfo}>\n")
        val argumentIndex = uniqueId.getAndIncrement()
        when(data.kind) {
            "attribute" -> builder.append("    var a$argumentIndex = reader.readAttribute();\n")
            "boolean" -> builder.append("    var a$argumentIndex = reader.readBoolean();\n")
            "command" -> {
                val root = data.kindOptions["root"]
                if(root != null) {
                    builder.append("    var a$argumentIndex = parseCommand_$root(reader, lookup);\n")
                }else {
                    builder.append("    var a$argumentIndex = parseCommand(reader, lookup);\n")
                }
            }
            "difficulty" -> builder.append("    var a$argumentIndex = reader.readDifficulty();\n")
            "double" -> {
                builder.append("    var a$argumentIndex = reader.readDouble();\n")
                data.kindOptions["minimum"]?.toDouble()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${createError("\"Double value of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toDouble()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${createError("\"Double value of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
            }
            "entity_selector" -> {
                val allowMultiple = data.kindOptions.getOrDefault("multiple", "true").toBoolean()
                val playersOnly = data.kindOptions.getOrDefault("players", "false").toBoolean()
                if(data.executableCommand != null && data.kindDefaults == "self") {
                    generateCommandReturn(builder, uniqueId, data.executableCommand!!, arguments + mapOf(data.argName!! to "SelectorArgument.ofSelf()"))
                }
                builder.append("    var a$argumentIndex = SelectorParser.parseSelector(reader, ${!playersOnly}, $allowMultiple);\n")
            }
            "function" -> builder.append("    var a$argumentIndex = lookup.lookupFunctions(reader.readTag());\n")
            "gamemode" -> builder.append("    var a$argumentIndex = reader.readGamemode();\n")
            "int" -> {
                builder.append("    var a$argumentIndex = reader.readInt();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${createError("\"Integer value of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${createError("\"Integer value of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
            }
            "int_range" -> {
                builder.append("    var a$argumentIndex = reader.readIntRange();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex.minimum() < $min) ${createError("\"Integer range minimum of \" + a$argumentIndex + \" is below allowed value ($min)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex.maximum() > $max) ${createError("\"Integer range maximum of \" + a$argumentIndex + \" is above allowed value ($max)\"")}\n") }
            }
            "location_anchor" -> builder.append("    var a$argumentIndex = reader.readLocationAnchor();\n")
            "mob_effect" -> builder.append("    var a$argumentIndex = reader.readMobEffect();\n")
            "nbt_compound" -> builder.append("    var a$argumentIndex = NbtParser.readNbtCompound(reader);\n")
            "nbt_path" -> builder.append("    var a$argumentIndex = NbtParser.readNbtPath(reader);\n")
            "nbt_value" -> builder.append("    var a$argumentIndex = NbtParser.readNbtValue(reader);\n")
            "objective" -> builder.append("    var a$argumentIndex = reader.readWord();\n")
            "resource_location" -> builder.append("    var a$argumentIndex = reader.readDataLocation();\n")
            "result_store_type" -> builder.append("    var a$argumentIndex = reader.readResultStoreType();\n")
            "rotation" -> builder.append("    var a$argumentIndex = reader.readRotation();\n")
            "score_comparison" -> builder.append("    var a$argumentIndex = reader.readScoreboardCompareOperation();\n")
            "score_holder" -> builder.append("    var a$argumentIndex = SelectorParser.parseScoreHolder(reader);\n")
            "scoreboard_operation" -> builder.append("    var a$argumentIndex = reader.readScoreboardOperation();\n")
            "swizzle" -> builder.append("    var a$argumentIndex = reader.readSwizzle();\n")
            "time" -> {
                builder.append("    var a$argumentIndex = reader.readTime();\n")
                data.kindOptions["minimum"]?.toInt()?.also { min -> builder.append("    if(a$argumentIndex < $min) ${createError("\"Time value of \" + a$argumentIndex + \" ticks is below allowed value ($min ticks)\"")}\n") }
                data.kindOptions["maximum"]?.toInt()?.also { max -> builder.append("    if(a$argumentIndex > $max) ${createError("\"Time value of \" + a$argumentIndex + \" ticks is above allowed value ($max ticks)\"")}\n") }
            }
            "word" -> builder.append("    var a$argumentIndex = reader.readWord();\n")
            "vec3" -> builder.append("    var a$argumentIndex = reader.readVec3();\n")
            else -> {
                TODO("Reading '$kindInfo' kind is not implemented @$argumentPath <${data.argName}>")
            }
        }
        val newArguments = arguments + mapOf(data.argName!! to "a$argumentIndex")
        if(data.kind == "command") {
            generateCommandReturn(builder, uniqueId, data.executableCommand!!, newArguments)
            builder.append("}\n")
            return false
        }
        if(data.executableCommand != null) {
            generateCommandReturn(builder, uniqueId, data.executableCommand!!, newArguments)
            if(data.children.isEmpty()) {
                builder.append("    ${createWrappedError("Too many arguments for @$argumentPath $name")}\n")
                builder.append("}\n")
                return false
            }
        }
        builder.append("    if(!reader.skipArgumentWhitespace()) ${createWrappedError("Missing space after argument @$argumentPath $name")}\n")
        generateArgumentReaderChildren(name, data, builder, uniqueId, argumentPath, newArguments, "    ")
        builder.append("}\n")
        return false
    }else {
        val locationIndex = uniqueId.getAndIncrement()
        builder.append("{ // $name\n")
        builder.append("    var z$locationIndex = reader.getIndex();\n")
        builder.append("    if(reader.canRead() && reader.readWord().equals(\"$name\")) {\n")
        if(data.executableCommand != null) {
            builder.append("    if(!reader.canRead()) {\n")
            val hashMapIndex = uniqueId.getAndIncrement()
            builder.append("        var y$hashMapIndex = new HashMap<String, Object>();\n")
            for((k, v) in arguments) {
                builder.append("        y$hashMapIndex.put(\"$k\", $v);\n")
            }
            builder.append("        return new Command(CommandType.${data.executableCommand}, y$hashMapIndex);\n")
            builder.append("    }\n")
        }
        builder.append("        if(!reader.skipArgumentWhitespace()) ${createWrappedError("Missing space after argument @$argumentPath $name")}\n")
        generateArgumentReaderChildren(name, data, builder, uniqueId, argumentPath, arguments, "        ")
        builder.append("    }\n")
        builder.append("    reader.setIndex(z$locationIndex);\n")
        builder.append("}\n")
        return true
    }
}

private fun createError(str: String) = "throw new CommandErrorException(new CommandError(reader.getString(), $str));"
private fun createWrappedError(str: String) = createError("\"" + str.translateEscapes() + "\"")

fun generateCommandsDataReader(data: ParsedCommandData): String {
    val builder = StringBuilder()
    val uid = AtomicInteger()
    builder.append("public static Command parseCommand(String command, boolean allowMacros, IArgumentLookup lookup) {\n")
    builder.append("    return parseCommand(new StringCommandReader(command, allowMacros), lookup);\n")
    builder.append("}\n")
    builder.append("\n")
    builder.append("public static Command parseCommand(StringCommandReader reader, IArgumentLookup lookup) {\n")
    builder.append("    var commandName = reader.readWord();\n")
    builder.append("    if(!reader.skipArgumentWhitespace()) ${createError("\"Missing space after argument @ \" + commandName")}\n")
    builder.append("    switch(commandName) {\n")
    for((k, _) in data.commandTree) {
        builder.append("        case \"$k\" -> { return parseCommand_$k(reader, lookup); }\n")
    }
    for((k, v) in data.commandRedirects) {
        builder.append("        case \"$k\" -> { return parseCommand_$v(reader, lookup); }\n")
    }
    builder.append("        default -> ${createError("\"Unknown command: \" + commandName")}\n")
    builder.append("    }\n")
    builder.append("}\n")
    builder.append("\n")
    for((k, v) in data.commandTree) {
        builder.append("public static Command parseCommand_$k(StringCommandReader reader, IArgumentLookup lookup) {\n")
        val innerBuilder = StringBuilder()
        generateArgumentReaderVersionCheck(k, v, innerBuilder, uid, "", mapOf(), true)
        builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
        builder.append("}\n")
    }
    return builder.toString()
}