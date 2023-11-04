package dev.epicpix.minecraftfunctioncompiler.il

import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")

fun printInstructionList(instructions: List<Instruction>, currentDepth: Int = 0) {
    for(instruction in instructions) {
        LOGGER.info("-".repeat(currentDepth) + instruction.type + " - " + instruction.data)
        printInstructionList(instruction.children, currentDepth + 1)
    }
}