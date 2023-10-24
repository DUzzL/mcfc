package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.context.CommandContextBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ScoreHolderArgument
import org.objectweb.asm.Opcodes

object CodeGenerator {
    fun generateCommand(commandName: String, reader: ImmutableStringReader, context: CommandContextBuilder<CommandSourceStack>, cw: CodeWriter) {
        when (commandName) {
            "scoreboard" -> generateCommandScoreboard(context, reader, cw)
            else -> throw UnsupportedOperationException("Cannot convert command: $commandName")
        }
    }

    private fun generateCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, reader: ImmutableStringReader, cw: CodeWriter) {
        val type = context.nodes[1].node.name
        if (type == "players") {
            val opType = context.nodes[2].node.name
            when (opType) {
                "add" -> {
                    val targets = context.arguments["targets"]!!.result
                    if (targets is ScoreHolderArgument.SelectorResult) {
                        println("Selector")
                    } else {
                        val targetsRange = context.arguments["targets"]!!.range
                        if (targetsRange.length == 1 && reader.string[targetsRange.start] == '*') {
                            load_getScoreboard(cw)
                            load_getObjective(cw, context.arguments["objective"]!!.result.toString())
                            cw.executeIfNotNullElse({
                                cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                                cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
                                cw.callFunction("com/google/common/collect/Lists", "newArrayList", "(Ljava/lang/Iterable;)Ljava/util/ArrayList;")
                                cw.defineLocal("\$Array", "Ljava/util/ArrayList;") {
                                    storeLocal("\$Array")

                                    cw.createArrayListForEachLoop("\$Array", "\$ArrayIndex", "\$ArrayLength") { getter ->
                                        load_getScoreboard(cw)
                                        getter()
                                        cw.castType("java/lang/String")
                                        load_getObjective(cw, context.arguments["objective"]!!.result.toString())
                                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                        cw.writeInstruction(Opcodes.DUP)
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        cw.writeInt(context.arguments["score"]!!.result as Int)
                                        cw.writeInstruction(Opcodes.IADD)
                                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    }
                                }
                            }) {
                                cw.writeInstruction(Opcodes.POP)
                            }
                        } else {
                            load_getObjective(cw, context.arguments["objective"]!!.result.toString())
                            cw.executeIfNotNull {
                                load_getObjectiveScore(cw, context.arguments["objective"]!!.result.toString(), targetsRange[reader.string], store = false)
                                cw.writeInstruction(Opcodes.DUP)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInt(context.arguments["score"]!!.result as Int)
                                cw.writeInstruction(Opcodes.IADD)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                        }
                    }
                }

                "enable" -> {}
                "operation" -> {}
                "remove" -> {}
                "reset" -> {}
                "set" -> {}
                "get", "list" -> {}
                else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: " + context.nodes)
            }
        } else if (type == "objectives") {
            throw UnsupportedOperationException("Unsupported scoreboard objectives arguments: " + context.nodes)
        } else {
            throw UnsupportedOperationException("Unsupported scoreboard operation type: $type")
        }
    }

    private fun findEntities() {}
    private fun load_getServer(cw: CodeWriter) {
        if (cw.getLocal("MinecraftServer") == null) {
            cw.loadLocal("CommandSourceStack")
            cw.callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
            cw.defineGlobalLocal("MinecraftServer", "Lnet/minecraft/server/MinecraftServer;")
            cw.storeLocal("MinecraftServer")
        }
        cw.loadLocal("MinecraftServer")
    }

    private fun load_getScoreboard(cw: CodeWriter) {
        if (cw.getLocal("ServerScoreboard") == null) {
            load_getServer(cw);
            cw.callMethod("net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;")
            cw.defineGlobalLocal("ServerScoreboard", "Lnet/minecraft/server/ServerScoreboard;")
            cw.storeLocal("ServerScoreboard")
        }
        cw.loadLocal("ServerScoreboard")
    }

    private fun load_getObjective(cw: CodeWriter, objective: String, load: Boolean = true) {
        val localName = "Objective-$objective"
        if (cw.getLocal(localName) == null) {
            load_getScoreboard(cw)
            cw.writeString(objective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")
            cw.defineGlobalLocal(localName, "Lnet/minecraft/world/scores/Objective;")
            cw.storeLocal(localName)
        }
        if(load) cw.loadLocal(localName)
    }

    private fun load_getObjectiveScore(cw: CodeWriter, objective: String, holder: String, load: Boolean = true, store: Boolean = true) {
        if(!store) {
            load_getScoreboard(cw)
            cw.writeString(holder)
            load_getObjective(cw, objective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            return
        }
        val localName = "Objective-$objective:$holder";
        if (cw.getLocal(localName) == null) {
            load_getScoreboard(cw)
            cw.writeString(holder)
            load_getObjective(cw, objective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            cw.defineGlobalLocal(localName, "Lnet/minecraft/world/scores/Score;")
            cw.storeLocal(localName)
        }
        if(load) cw.loadLocal(localName)
    }

}
