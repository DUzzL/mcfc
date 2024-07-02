package dev.epicpix.annotations.commands

import java.util.concurrent.atomic.AtomicInteger

data class ParsedCommandArgument(
    val children: MutableMap<String, ParsedCommandArgument> = mutableMapOf(),
    val argName: String? = null,
    val kind: String = "literal",
    val kindOptions: MutableMap<String, String> = mutableMapOf(),
    val kindDefaults: String? = null,
    var executableCommand: String? = null,
)

data class ParsedCommandData(
    val commands: List<Pair<String, String>>,
    val commandTree: MutableMap<String, ParsedCommandArgument>,
)

private fun parseCommandPart(parts: List<String>, current: ParsedCommandArgument, commandId: String) {
    if(parts.isEmpty()) {
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
                ParsedCommandArgument(argName = split[0], kind = kind, kindOptions = options, kindDefaults = split.getOrNull(2))
            }else {
                ParsedCommandArgument()
            }
        }, commandId)
    }
}

private fun parseCommandLine(command: String, tree: MutableMap<String, ParsedCommandArgument>, commandId: String) {
    val parts = command.split(" ").toMutableList()
    val cmd = parts[0]
    val argument = tree.getOrPut(cmd) { ParsedCommandArgument() }
    parseCommandPart(parts.subList(1, parts.size), argument, commandId)
}

fun parseCommandsData(file: String): ParsedCommandData {
    val commands = mutableListOf<Pair<String, String>>()
    val commandTree = mutableMapOf<String, ParsedCommandArgument>()
    for(line in file.lineSequence()) {
        if(line.isBlank()) continue
        val slashPart = line.split("/", limit = 2)
        commands.add(slashPart[0] to slashPart[1])
        parseCommandLine(slashPart[1], commandTree, slashPart[0])
    }
    return ParsedCommandData(commands, commandTree)
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

private fun generateArgumentReader(name: String, data: ParsedCommandArgument, builder: StringBuilder, uniqueId: AtomicInteger, argumentPath: String, arguments: Map<String, String>, fromRoot: Boolean = false): Boolean {
    val locationIndex = uniqueId.getAndIncrement();
    if(fromRoot) {
        if(data.executableCommand != null) {
            builder.append("if(!reader.canRead()) return new Command(CommandType.${data.executableCommand}, Map.of());\n")
        }
        var reachable = true
        for((k, v) in data.children) {
            if(reachable) {
                val innerBuilder = StringBuilder()
                reachable = generateArgumentReader(k, v, innerBuilder, uniqueId, "$argumentPath $name", arguments)
                builder.append(innerBuilder.toString().substringBeforeLast('\n') + "\n")
            }
        }
        if(reachable) {
            builder.append("${createWrappedError("Failed to parse any options @$argumentPath $name")}\n")
        }
        return true
    }else if(data.kind != "literal") {
        val kindInfo = data.kind +
            (if(data.kindOptions.isEmpty()) "" else ("[" + data.kindOptions.map { "${it.key}=${it.value}" }.joinToString(",") + "]")) +
            (if(data.kindDefaults == null) "" else ":${data.kindDefaults}")
        builder.append("{ // ${data.argName} <${kindInfo}>\n")
        val argumentIndex = uniqueId.getAndIncrement()
        when(data.kind) {
            "attribute" -> builder.append("    var a$argumentIndex = reader.readAttribute();\n")
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
            "execute_subcommand" -> builder.append("    var a$argumentIndex = parseCommand_execute(reader, lookup);\n")
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
            "objective" -> builder.append("    var a$argumentIndex = reader.readWord();\n")
            "root_command" -> builder.append("    var a$argumentIndex = parseCommand(reader, lookup);\n")
            "score_comparison" -> builder.append("    var a$argumentIndex = reader.readScoreboardCompareOperation();\n")
            "score_holder" -> builder.append("    var a$argumentIndex = SelectorParser.parseScoreHolder(reader);\n")
            "scoreboard_operation" -> builder.append("    var a$argumentIndex = reader.readScoreboardOperation();\n")
            "swizzle" -> builder.append("    var a$argumentIndex = reader.readSwizzle();\n")
            "word" -> builder.append("    var a$argumentIndex = reader.readWord();\n")
            "vec3" -> builder.append("    var a$argumentIndex = reader.readVec3();\n")
            else -> {
                TODO("Reading '$kindInfo' kind is not implemented @$argumentPath <${data.argName}>")
            }
        }
        val newArguments = arguments + mapOf(data.argName!! to "a$argumentIndex")
        if(data.kind == "execute_subcommand" || data.kind == "root_command") {
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
        var reachable = true
        for((k, v) in data.children) {
            if(reachable) {
                val innerBuilder = StringBuilder()
                reachable = generateArgumentReader(k, v, innerBuilder, uniqueId, "$argumentPath $name", newArguments)
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
            }
        }
        if(reachable) {
            builder.append("    ${createWrappedError("Failed to parse any options @$argumentPath $name")}\n")
        }
        builder.append("}\n")
        return false
    }else {
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
        var reachable = true
        for((k, v) in data.children) {
            if(reachable) {
                val innerBuilder = StringBuilder()
                reachable = generateArgumentReader(k, v, innerBuilder, uniqueId, "$argumentPath $name", arguments)
                builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("        ") + "\n")
            }
        }
        if(reachable) {
            builder.append("        ${createWrappedError("Failed to parse any options @$argumentPath $name")}\n")
        }
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
    builder.append("public static Command parseCommand(String command, IArgumentLookup lookup) {\n")
    builder.append("    return parseCommand(new StringCommandReader(command), lookup);\n")
    builder.append("}\n")
    builder.append("\n")
    builder.append("public static Command parseCommand(StringCommandReader reader, IArgumentLookup lookup) {\n")
    builder.append("    var commandName = reader.readWord();\n")
    builder.append("    if(!reader.skipArgumentWhitespace()) ${createError("\"Missing space after argument @ \" + commandName")}\n")
    builder.append("    switch(commandName) {\n")
    for((k, _) in data.commandTree) {
        builder.append("        case \"$k\" -> { return parseCommand_$k(reader, lookup); }\n")
    }
    builder.append("        default -> ${createError("\"Unknown command: \" + commandName")}\n")
    builder.append("    }\n")
    builder.append("}\n")
    builder.append("\n")
    for((k, v) in data.commandTree) {
        builder.append("public static Command parseCommand_$k(StringCommandReader reader, IArgumentLookup lookup) {\n")
        val innerBuilder = StringBuilder()
        generateArgumentReader(k, v, innerBuilder, uid, "", mapOf(), true)
        builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
        builder.append("}\n")
    }
    return builder.toString()
}