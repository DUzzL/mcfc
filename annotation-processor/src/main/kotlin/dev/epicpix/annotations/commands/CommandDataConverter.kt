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
                val kindOptions = kindAll.substringAfter('[', missingDelimiterValue = "").substringBeforeLast(']').split(".")
                val options = mutableMapOf<String, String>()
                for(opt in kindOptions) {
                    val eq = opt.split("=", limit = 2)
                    if(eq.size == 1) {
                        options[opt] = "true"
                    }else {
                        options[eq[0]] = eq[1]
                    }
                }
                ParsedCommandArgument(argName = split[0], kind = kind, kindDefaults = split.getOrNull(2))
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

private fun generateArgumentReader(name: String, data: ParsedCommandArgument, builder: StringBuilder, uniqueId: AtomicInteger, argumentPath: String, arguments: Map<String, String>, fromRoot: Boolean = false): Boolean {
    val locationIndex = uniqueId.getAndIncrement();
    if(fromRoot) {
        builder.append("{ // $name\n")
        builder.append("    if(\"$name\".equals(commandName)) {\n")
        if(data.executableCommand != null) {
            builder.append("        if(!reader.canRead()) return new Command(CommandType.${data.executableCommand}, Map.of());\n")
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
        builder.append("}\n")
        return true
    }else if(data.kind != "literal") {
        val kindInfo = data.kind +
            (if(data.kindOptions.isEmpty()) "" else data.kindOptions.map { "${it.key}=${it.value}" }.joinToString(",")) +
            (if(data.kindDefaults == null) "" else ":${data.kindDefaults}")
        builder.append("{ // ${data.argName} <${kindInfo}>\n")
        if(data.kind == "entity_selector") {
            val allowMultiple = data.kindOptions.getOrDefault("multiple", "true").toBoolean()
            val playersOnly = data.kindOptions.getOrDefault("players", "false").toBoolean()
            val selectorIndex = uniqueId.getAndIncrement()
            builder.append("    var a$selectorIndex = SelectorParser.parseSelector(reader, ${!playersOnly}, $allowMultiple);\n")
            val newArguments = arguments + mapOf(data.argName!! to "a$selectorIndex")
            if(data.executableCommand != null) {
                builder.append("    if(!reader.canRead()) {\n")
                val hashMapIndex = uniqueId.getAndIncrement()
                builder.append("        var y$hashMapIndex = new HashMap<String, Object>();\n")
                for((k, v) in newArguments) {
                    builder.append("        y$hashMapIndex.put(\"$k\", $v);\n")
                }
                builder.append("        return new Command(CommandType.${data.executableCommand}, y$hashMapIndex);\n")
                builder.append("    }\n")
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
        }else if(data.kind == "word") {
            val wordIndex = uniqueId.getAndIncrement()
            builder.append("    var a$wordIndex = reader.readWord();\n")
            val newArguments = arguments + mapOf(data.argName!! to "a$wordIndex")
            if(data.executableCommand != null) {
                builder.append("    if(!reader.canRead()) {\n")
                val hashMapIndex = uniqueId.getAndIncrement()
                builder.append("        var y$hashMapIndex = new HashMap<String, Object>();\n")
                for((k, v) in newArguments) {
                    builder.append("        y$hashMapIndex.put(\"$k\", $v);\n")
                }
                builder.append("        return new Command(CommandType.${data.executableCommand}, y$hashMapIndex);\n")
                builder.append("    }\n")
            }
            builder.append("    if(!reader.skipArgumentWhitespace()) ${createWrappedError("Missing space after argument @$argumentPath $name")}\n")
            var reachable = true
            for((k, v) in data.children) {
                if(reachable) {
                    val innerBuilder = StringBuilder()
                    reachable = generateArgumentReader(k, v, innerBuilder, uniqueId, "$argumentPath $name", arguments)
                    builder.append(innerBuilder.toString().substringBeforeLast('\n').prependIndent("    ") + "\n")
                }
            }
            if(reachable) {
                builder.append("    ${createWrappedError("Failed to parse any options @$argumentPath $name")}\n")
            }
        }else {
            builder.append("    ${createWrappedError("Reading '$kindInfo' kind is not implemented @$argumentPath <${data.argName}>")}\n")
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

private fun createError(str: String) = "throw new UnsupportedOperationException($str);"
private fun createWrappedError(str: String) = createError("\"" + str.translateEscapes() + "\"")

fun generateCommandsDataReader(data: ParsedCommandData): String {
    val builder = StringBuilder()
    val uid = AtomicInteger()
    builder.append("var commandName = reader.readWord();\n")
    for((k, v) in data.commandTree) {
        generateArgumentReader(k, v, builder, uid, "", mapOf(), true)
    }
    builder.append(createError("\"Unknown command: \" + commandName"))
    builder.append("\n")
    return builder.toString()
}