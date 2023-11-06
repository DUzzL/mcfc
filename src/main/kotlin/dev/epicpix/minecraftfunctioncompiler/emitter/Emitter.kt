package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

private data class EmitControlFlow(val breakLabel: Label, val continueLabel: Label)

fun emitBytecode(instructions: List<Instruction>, fieldStorage: CodeFieldStorage, codeWriter: CodeWriter, definedFunctions: Map<ResourceLocation, List<Command>>) {
    for(instruction in instructions) {
        emitInstruction(instruction, fieldStorage, codeWriter, definedFunctions)
    }
}

private fun LocationData.load(codeWriter: CodeWriter, transforms: Map<Int, String>) {
    if(this.string != null) {
        codeWriter.writeString(string)
    }else if(this.local != null) {
        codeWriter.loadLocal(transforms[local]!!)
    }
}

private fun emitInstruction(
    instruction: Instruction,
    fieldStorage: CodeFieldStorage,
    codeWriter: CodeWriter,
    definedFunctions: Map<ResourceLocation, List<Command>>,
    transforms: Map<Int, String> = mapOf(),
    loops: Map<Int, EmitControlFlow> = mapOf()
) {
    val newTransforms = transforms.toMutableMap()
    val newLoops = loops.toMutableMap()

    fun run() = instruction.children.forEach { emitInstruction(it, fieldStorage, codeWriter, definedFunctions, newTransforms, newLoops) }

    when(instruction.type) {
        InstructionType.GET_CURRENT_ENTITY -> {
            val data = instruction.data as InstructionDataLocation
            val uniqueId = codeWriter.generateUniqueLocal("\$GetCurrentEntity")
            newTransforms[data.local.local!!] = uniqueId
            val end = codeWriter.defineLocal(uniqueId, "Lnet/minecraft/world/entity/Entity;")

            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "getEntity", "()Lnet/minecraft/world/entity/Entity;")
            codeWriter.storeLocal(uniqueId)

            run()

            end()
        }
        InstructionType.IF_NOT_NULL -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.executeIf(Opcodes.IFNONNULL) { run() }
        }
        InstructionType.IF_NULL -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.executeIf(Opcodes.IFNULL) { run() }
        }
        InstructionType.ADD_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationString
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeString(data.value)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "addTag", "(Ljava/lang/String;)Z")
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.REMOVE_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationString
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeString(data.value)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "removeTag", "(Ljava/lang/String;)Z")
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.EMPTY_CONDITION -> {
            val data = instruction.data as InstructionDataLoop
            val label = Label()
            newLoops[data.loopId] = EmitControlFlow(label, label)

            run()

            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.BREAK_LOOP -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newLoops[data.loopId]!!.breakLabel)
        }
        InstructionType.CONTINUE_LOOP -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationStringLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            codeWriter.writeString(data.value)
            codeWriter.callInterfaceMethod("java/util/Set", "contains", "(Ljava/lang/Object;)Z")
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_NOT_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationStringLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            codeWriter.writeString(data.value)
            codeWriter.callInterfaceMethod("java/util/Set", "contains", "(Ljava/lang/Object;)Z")
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_ENTITY_TAGS -> {
            val data = instruction.data as InstructionDataLocationLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            codeWriter.callInterfaceMethod("java/util/Set", "size", "()I")
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_NOT_ENTITY_TAGS -> {
            val data = instruction.data as InstructionDataLocationLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            codeWriter.callInterfaceMethod("java/util/Set", "size", "()I")
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.LIMITER -> {
            val data = instruction.data as InstructionDataIntLoop
            val uniqueId = codeWriter.generateUniqueLocal("\$LimitRemaining")
            newTransforms[data.loopId] = uniqueId
            val end = codeWriter.defineLocal(uniqueId, "I")
            codeWriter.writeInt(data.value)
            codeWriter.storeLocal(uniqueId)

            val label = Label()
            newLoops[data.loopId] = EmitControlFlow(label, label)

            run()

            end()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.CHECK_LIMIT -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitIincInsn(codeWriter.getLocalIndex(newTransforms[data.loopId]!!), -1)
            codeWriter.loadLocal(newTransforms[data.loopId]!!)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.breakLabel)
        }
        InstructionType.FOR_EACH_PLAYER -> {
            val data = instruction.data as InstructionDataLocation

            val uidLocal = codeWriter.generateUniqueLocal("\$Players")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
            codeWriter.callMethod("net/minecraft/server/MinecraftServer", "getPlayerList", "()Lnet/minecraft/server/players/PlayerList;")
            codeWriter.callMethod("net/minecraft/server/players/PlayerList", "getPlayers", "()Ljava/util/List;")
            val end = codeWriter.defineLocal(uidLocal, "Ljava/util/List;")
            codeWriter.storeLocal(uidLocal)
            codeWriter.createListForEachLoop(uidLocal, "net/minecraft/world/entity/Entity", true) { _, getter ->
                val uidLocal2 = codeWriter.generateUniqueLocal("\$Entity")
                val lend = codeWriter.defineLocal(uidLocal2, "Lnet/minecraft/world/entity/Entity;")
                codeWriter.storeLocal(uidLocal2)
                newTransforms[data.local.local!!] = uidLocal2
                run()
                lend()
            }
            end()
        }
        InstructionType.FOR_EACH_ENTITY -> {
            val data = instruction.data as InstructionDataLocation

            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
            codeWriter.callMethod("net/minecraft/server/MinecraftServer", "getAllLevels", "()Ljava/lang/Iterable;")
            val end = codeWriter.defineLocal(uidLocalLevels, "Ljava/lang/Iterable;")
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, "net/minecraft/server/level/ServerLevel", true) { _, _ ->
                val uidLocalEntities = codeWriter.generateUniqueLocal("\$Entities")
                codeWriter.callMethod("net/minecraft/server/level/ServerLevel", "getAllEntities", "()Ljava/lang/Iterable;")
                codeWriter.callInterfaceMethod("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;")
                val end2 = codeWriter.defineLocal(uidLocalEntities, "Ljava/util/Iterator;")
                codeWriter.storeLocal(uidLocalEntities)
                codeWriter.createIteratorForEachLoop(uidLocalEntities, "net/minecraft/world/entity/Entity", true) { _, _ ->
                    val uidLocalEntity = codeWriter.generateUniqueLocal("\$Entity")
                    val lend = codeWriter.defineLocal(uidLocalEntity, "Lnet/minecraft/world/entity/Entity;")
                    codeWriter.storeLocal(uidLocalEntity)
                    newTransforms[data.local.local!!] = uidLocalEntity
                    run()
                    lend()
                }

                end2()
            }

            end()
        }
        InstructionType.FOR_PLAYER -> {
            val data = instruction.data as InstructionDataLocationString

            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
            codeWriter.callMethod("net/minecraft/server/MinecraftServer", "getPlayerList", "()Lnet/minecraft/server/players/PlayerList;")
            codeWriter.writeString(data.value)
            codeWriter.callMethod("net/minecraft/server/players/PlayerList", "getPlayerByName", "(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;")
            val uidLocal = codeWriter.generateUniqueLocal("\$Player")
            val end = codeWriter.defineLocal(uidLocal, "Lnet/minecraft/server/level/ServerPlayer;")
            newTransforms[data.local.local!!] = uidLocal
            codeWriter.storeLocal(uidLocal)

            run()

            end()
        }
        InstructionType.FOR_ENTITY -> {
            val data = instruction.data as InstructionDataLocationUUID

            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
            codeWriter.callMethod("net/minecraft/server/MinecraftServer", "getAllLevels", "()Ljava/lang/Iterable;")
            val end = codeWriter.defineLocal(uidLocalLevels, "Ljava/lang/Iterable;")
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, "net/minecraft/server/level/ServerLevel", true) { controlFlow, _ ->
                fieldStorage.loadUUID(codeWriter, data.value)
                codeWriter.callMethod("net/minecraft/server/level/ServerLevel", "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;")
                val uidLocal = codeWriter.generateUniqueLocal("\$Entity")
                val end2 = codeWriter.defineLocal(uidLocal, "Lnet/minecraft/world/entity/Entity;")
                newTransforms[data.local.local!!] = uidLocal
                codeWriter.storeLocal(uidLocal)
                codeWriter.loadLocal(uidLocal)
                codeWriter.executeIf(Opcodes.IFNONNULL) {
                    run()
                }
                end2()
            }

            end()
        }
        InstructionType.FOR_SCORE_HOLDERS -> {
            val data = instruction.data as InstructionDataLocationBoolean

            codeWriter.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                fieldStorage.loadScoreboard(codeWriter)
                codeWriter.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                codeWriter.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
            }

            val uidLocal = codeWriter.generateUniqueLocal("\$Array")
            val end = codeWriter.defineLocal(uidLocal, "Ljava/util/ArrayList;")
            codeWriter.storeLocal(uidLocal)
            if(!data.value) {
                codeWriter.createListForEachLoop(uidLocal, "java/lang/String") { _, getter ->
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, "Ljava/lang/String;")
                    getter()
                    codeWriter.storeLocal(uidLocalName)
                    newTransforms[data.local.local!!] = uidLocalName
                    run()
                    end2()
                }
            }else {
                codeWriter.loadLocal(uidLocal)
                codeWriter.callMethod("java/util/ArrayList", "size", "()I")
                codeWriter.executeIf(Opcodes.IFNE) {
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, "Ljava/lang/String;")
                    codeWriter.loadLocal(uidLocal)
                    codeWriter.writeInt(0)
                    codeWriter.callMethod("java/util/ArrayList", "get", "(I)Ljava/lang/Object;")
                    codeWriter.castType("java/lang/String")
                    codeWriter.storeLocal(uidLocalName)
                    newTransforms[data.local.local!!] = uidLocalName
                    run()
                    end2()
                }

            }

            end()

        }
        InstructionType.GET_SCOREBOARD_NAME -> {
            val data = instruction.data as InstructionDataLocationLocation

            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")

            val uidLocal = codeWriter.generateUniqueLocal("\$ScoreboardName")
            val end = codeWriter.defineLocal(uidLocal, "Ljava/lang/String;")
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local2.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.SET_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocationInt

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            codeWriter.writeInt(data.value)
            codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
        }
        InstructionType.ADD_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocationInt

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
            codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
            if(data.value >= 0) {
                codeWriter.writeInt(data.value)
                codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
            }else {
                codeWriter.writeInt(-data.value)
                codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
            }
            codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
        }
        InstructionType.RUN_SCORE_OPERATION -> {
            val data = instruction.data as InstructionDataRunScoreOperation

            when(data.operation) {
                ScoreboardOperation.SET -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    }else {
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    }
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    if(data.operation == ScoreboardOperation.PLUS) codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    else if(data.operation == ScoreboardOperation.MINUS) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    } else codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
                ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                    val endBeh = codeWriter.defineLocal(uniqBeh, "I")
                    codeWriter.storeLocal(uniqBeh)

                    fieldStorage.loadScoreboard(codeWriter)
                    data.sourceName.load(codeWriter, newTransforms)
                    data.sourceObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    val uniq = codeWriter.generateUniqueLocal("\$Score")
                    val end = codeWriter.defineLocal(uniq, "Lnet/minecraft/world/scores/Score;")
                    codeWriter.storeLocal(uniq)
                    codeWriter.loadLocal(uniq)
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.exceptionLoopId]!!.breakLabel)
                    codeWriter.loadLocal(uniq)
                    codeWriter.loadLocal(uniqBeh)
                    codeWriter.loadLocal(uniq)
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    if(data.operation == ScoreboardOperation.DIVIDE) codeWriter.callFunction("java/lang/Math", "floorDiv", "(II)I")
                    else codeWriter.callFunction("java/lang/Math", "floorMod", "(II)I")
                    codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    end()
                    endBeh()
                }
                ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        if(data.operation == ScoreboardOperation.MIN) codeWriter.callFunction("java/lang/Math", "min", "(II)I")
                        else codeWriter.callFunction("java/lang/Math", "max", "(II)I")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                ScoreboardOperation.SWAP -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        val targetId = codeWriter.generateUniqueLocal("\$Score")
                        val targetEnd = codeWriter.defineLocal(targetId, "Lnet/minecraft/world/scores/Score;")
                        codeWriter.storeLocal(targetId)

                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        val sourceId = codeWriter.generateUniqueLocal("\$Score")
                        val sourceEnd = codeWriter.defineLocal(sourceId, "Lnet/minecraft/world/scores/Score;")
                        codeWriter.storeLocal(sourceId)

                        codeWriter.loadLocal(sourceId)
                        codeWriter.loadLocal(targetId)
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        codeWriter.loadLocal(targetId)
                        codeWriter.loadLocal(sourceId)
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                        codeWriter.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")

                        sourceEnd()
                        targetEnd()
                    }
                }
            }
        }
        InstructionType.GET_OBJECTIVE -> {
            val data = instruction.data as InstructionDataLocationString

            fieldStorage.loadScoreboard(codeWriter)
            codeWriter.writeString(data.value)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")

            val uidLocal = codeWriter.generateUniqueLocal("\$Objective")
            val end = codeWriter.defineLocal(uidLocal, "Lnet/minecraft/world/scores/Objective;")
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.CALL_FUNCTIONS -> {
            val data = instruction.data as InstructionDataFunctionCall

            for(func in data.functions) {
                if(definedFunctions.containsKey(func)) {
                    codeWriter.loadLocal("CommandSourceStack")
                    if(data.entity != null) {
                        data.entity.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
                    }
                    codeWriter.callFunction("function/${func.namespace}/${func.path}", "run", "(Lnet/minecraft/commands/CommandSourceStack;)I")
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }else {
                    fieldStorage.loadServerFunctionManager(codeWriter)
                    fieldStorage.loadFunction(codeWriter, func)
                    codeWriter.loadLocal("CommandSourceStack")
                    if(data.entity != null) {
                        data.entity.load(codeWriter, newTransforms)
                        codeWriter.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
                    }
                    codeWriter.writeNull()
                    codeWriter.writeNull()
                    codeWriter.callMethod("net/minecraft/server/ServerFunctionManager", "execute", "(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager\$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I")
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }
            }
        }
        InstructionType.KILL_ENTITY -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
        }
        InstructionType.RESET_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocation

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
        }
        InstructionType.CHECK_SCORE_RANGE -> {
            val data = instruction.data as InstructionDataCheckScoreRange
            fieldStorage.loadScoreboard(codeWriter)
            data.name.load(codeWriter, newTransforms)
            data.objective.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
            if(data.range.minimum != null && data.range.maximum == null) {
                codeWriter.writeInt(data.range.minimum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPGE else Opcodes.IF_ICMPLT) {
                    run()
                }
            }else if(data.range.minimum == null && data.range.maximum != null) {
                codeWriter.writeInt(data.range.maximum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPLE else Opcodes.IF_ICMPGT) {
                    run()
                }
            }else if(data.range.minimum != null && data.range.minimum == data.range.maximum) {
                codeWriter.writeInt(data.range.minimum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPEQ else Opcodes.IF_ICMPNE) {
                    run()
                }
            }else if(data.range.minimum != null && data.range.maximum != null) {
                val uid = codeWriter.generateUniqueLocal("\$Score")
                val end = codeWriter.defineLocal(uid, "I")
                codeWriter.storeLocal(uid)
                if(!data.negate) {
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(data.range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(data.range.maximum)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            run()
                        }
                    }
                }else {
                    val skip = Label()
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(data.range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(data.range.maximum)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, skip)
                        }
                    }
                    run()
                    codeWriter.methodVisitor.visitLabel(skip)
                }
                end()
            }
        }
        InstructionType.CHECK_SCORE_COMPARE -> {
            val data = instruction.data as InstructionDataCheckScoreCompare
            fieldStorage.loadScoreboard(codeWriter)
            data.sourceName.load(codeWriter, newTransforms)
            data.sourceObjective.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")

            fieldStorage.loadScoreboard(codeWriter)
            data.targetName.load(codeWriter, newTransforms)
            data.targetObjective.load(codeWriter, newTransforms)
            codeWriter.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            codeWriter.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")

            when(data.compareOperation) {
                ScoreboardCompareOperation.LT -> { codeWriter.executeIf(Opcodes.IF_ICMPLT) { run() } }
                ScoreboardCompareOperation.LE -> { codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() } }
                ScoreboardCompareOperation.EQ -> { codeWriter.executeIf(Opcodes.IF_ICMPEQ) { run() } }
                ScoreboardCompareOperation.NE -> { codeWriter.executeIf(Opcodes.IF_ICMPNE) { run() } }
                ScoreboardCompareOperation.GE -> { codeWriter.executeIf(Opcodes.IF_ICMPGE) { run() } }
                ScoreboardCompareOperation.GT -> { codeWriter.executeIf(Opcodes.IF_ICMPGT) { run() } }
            }

        }
    }
}