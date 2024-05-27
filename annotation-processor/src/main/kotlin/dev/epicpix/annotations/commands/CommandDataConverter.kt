package dev.epicpix.annotations.commands

data class ParsedCommandData(
    val commands: List<Pair<String, String>>,
)

fun parseCommandsData(file: String): ParsedCommandData {
    val commands = mutableListOf<Pair<String, String>>()
    for(line in file.lineSequence()) {
        if(line.isBlank()) continue
        val slashPart = line.split("/", limit = 2)
        commands.add(slashPart[0] to slashPart[1])
    }
    return ParsedCommandData(commands)
}