package dev.epicpix.minecraftfunctioncompiler

private fun extractCommands(command: CommandLike, requirements: List<CommandRequirement>, result: MutableList<CommandBlock>) {
    if(command is CommandBlock) {
        val reqs = requirements.toMutableList().apply { addAll(command.requirements) }
        command.commands.forEach { extractCommands(it, reqs, result) }
    }else if(command is Command) {
        result.add(CommandBlock(listOf(command), requirements))
    }
}

fun mergeCommandRequirements(commands: List<CommandLike>): List<CommandLike> {
    val flatCommands = mutableListOf<CommandBlock>()
    commands.forEach { extractCommands(it, emptyList(), flatCommands) }
    val allRequirements = mutableListOf<CommandRequirement>().apply { flatCommands.forEach { addAll(it.requirements) } }.distinct()
    val commandsData = mutableListOf<CommandMergeData>()
    for(cmd in flatCommands) {
        commandsData.add(CommandMergeData(cmd.commands[0] as Command, cmd.requirements))
    }
    val previousRequirements = mutableMapOf<CommandRequirement, Int>()
    allRequirements.forEach { previousRequirements[it] = 0 }
    for(cmd in commandsData) {
        val prevReqs = previousRequirements.keys.toSet()
        val curReqs = cmd.requirements.toSet()
        val oldReqs = prevReqs subtract curReqs

        oldReqs.forEach(previousRequirements::remove)
        curReqs.forEach { previousRequirements[it] = (previousRequirements[it] ?: 0) + 1 }
        cmd.requirementInfo = previousRequirements.toMutableMap()
    }
    val temp = mutableListOf<CommandMergeData>()
    for(i in commandsData.size-1 downTo 1) {
        val intersection = commandsData[i - 1].requirements intersect commandsData[i].requirements.toSet()
        intersection.forEach {
            commandsData[i - 1].requirementInfo[it] = commandsData[i].requirementInfo[it]!!
        }
        if(intersection.size != commandsData[i - 1].requirements.size || intersection.size != commandsData[i].requirements.size) {
            commandsData[i].split = true
        }
    }
    val blocks = mutableListOf<CommandMergeBlockData>()
    for(cmd in commandsData) {
        if(cmd.split) {
            if(temp.isNotEmpty()) {
                blocks.add(CommandMergeBlockData(temp.toMutableList(), temp[0].requirementInfo.toMutableMap()))
            }
            temp.clear()
        }
        temp.add(cmd)
    }
    if(temp.isNotEmpty()) {
        blocks.add(CommandMergeBlockData(temp.toMutableList(), temp[0].requirementInfo.toMutableMap()))
        temp.clear()
    }
    for(i in 1..<blocks.size) {
        val prevBlock = blocks[i - 1]
        val currBlock = blocks[i]
        (prevBlock.requirementPriorities.keys intersect currBlock.requirementPriorities.keys).forEach {
            currBlock.requirementPriorities[it] = prevBlock.requirementPriorities[it]!! - prevBlock.commands.size
        }
    }
    for(block in blocks) {
        val sorted = block.requirementPriorities.entries.sortedByDescending { a -> a.value }
        block.requirementPrioritiesList = sorted.map { it.key }
    }

    val base = MutableCommandBlock()

    var blockStack = mutableListOf<MutableCommandBlock>()
    blockStack.add(base)
    var generatedRequirements = mutableListOf<CommandRequirement>()

    for(block in blocks) {
        if(block.requirementPrioritiesList.size < generatedRequirements.size) {
            generatedRequirements = generatedRequirements.subList(0, block.requirementPrioritiesList.size).toMutableList()
            blockStack = blockStack.subList(0, block.requirementPrioritiesList.size+1).toMutableList()
        }
        var lastValid = generatedRequirements.size
        for(i in generatedRequirements.indices) {
            if(generatedRequirements[i] != block.requirementPrioritiesList[i]) {
                lastValid = i
                break
            }
        }
        if(lastValid != generatedRequirements.size) {
            generatedRequirements = generatedRequirements.subList(0, lastValid).toMutableList()
            blockStack = blockStack.subList(0, lastValid+1).toMutableList()
        }
        for(i in block.requirementPrioritiesList.indices) {
            if(generatedRequirements.size <= i) {
                val generatedBlock = MutableCommandBlock(requirements = listOf(block.requirementPrioritiesList[i]))
                blockStack.last().commands.add(generatedBlock)
                blockStack.add(generatedBlock)
                generatedRequirements.add(block.requirementPrioritiesList[i])
            }
        }
        block.commands.forEach { blockStack.last().commands.add(MutableCommandData(it.command)) }
    }

    return (base.toCommandLike() as CommandBlock).commands
}

data class CommandMergeData(
    val command: Command,
    val requirements: List<CommandRequirement>,
    var requirementInfo: MutableMap<CommandRequirement, Int> = mutableMapOf(),
    var split: Boolean = false,
)

data class CommandMergeBlockData(
    val commands: List<CommandMergeData>,
    var requirementPriorities: MutableMap<CommandRequirement, Int> = mutableMapOf(),
    var requirementPrioritiesList: List<CommandRequirement> = listOf(),
)

interface MutableCommandLike {
    fun toCommandLike(): CommandLike
}

data class MutableCommandData(
    val command: Command
) : MutableCommandLike {
    override fun toCommandLike(): CommandLike = command
}

data class MutableCommandBlock(
    val commands: MutableList<MutableCommandLike> = mutableListOf(),
    val requirements: List<CommandRequirement> = listOf(),
) : MutableCommandLike {
    override fun toCommandLike(): CommandLike {
        val generatedCommands = commands.map { it.toCommandLike() }
        return CommandBlock(generatedCommands, requirements)
    }
}