package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import dev.epicpix.minecraftfunctioncompiler.data.mapEntityTypeClass
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.*
import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Score
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
    }else if(this.integer != null) {
        codeWriter.writeInt(integer)
    }
}

private fun emitInstruction(
    instruction: Instruction,
    fieldStorage: CodeFieldStorage,
    codeWriter: CodeWriter,
    definedFunctions: Map<ResourceLocation, List<Command>>,
    transforms: Map<Int, String> = mapOf(),
    scopes: Map<Int, EmitControlFlow> = mapOf()
) {
    val newTransforms = transforms.toMutableMap()
    val newScopes = scopes.toMutableMap()

    fun run() = instruction.children.forEach { emitInstruction(it, fieldStorage, codeWriter, definedFunctions, newTransforms, newScopes) }
    fun store(location: String, uid: String) {
        val loc = instruction.get<LocationData>(location)
        if(loc.local == null) throw UnsupportedOperationException("Tried to store variable in a non-defined local location: $loc, $instruction")
        newTransforms[loc.local] = uid
    }
    fun load(location: String) = instruction.get<LocationData>(location).load(codeWriter, newTransforms)
    fun scopeId(location: String) = instruction.get<ScopeData>(location).id


    when(instruction.type) {
        InstructionType.GET_CURRENT_ENTITY -> {
            val uniqueId = codeWriter.generateUniqueLocal("\$GetCurrentEntity")
            store("entity", uniqueId)
            val end = codeWriter.defineLocal(uniqueId, Entity::class.java)

            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getEntity)
            codeWriter.storeLocal(uniqueId)

            run()

            end()
        }
        InstructionType.IF_NOT_NULL -> {
            load("local")
            codeWriter.executeIf(Opcodes.IFNONNULL) { run() }
        }
        InstructionType.ADD_ENTITY_TAG -> {
            load("entity")
            codeWriter.writeString(instruction["tag"])
            codeWriter.writeMapped(Entity_addTag)
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.REMOVE_ENTITY_TAG -> {
            load("entity")
            codeWriter.writeString(instruction["tag"])
            codeWriter.writeMapped(Entity_removeTag)
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.SCOPE -> {
            val label = Label()
            newScopes[scopeId("scope")] = EmitControlFlow(label, label)

            run()

            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.EXIT_SCOPE -> {
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newScopes[scopeId("scope")]!!.breakLabel)
        }
        InstructionType.CHECK_ENTITY_TAG -> {
            load("entity")
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeString(instruction["tag"])
            codeWriter.writeMapped(Set_contains)

            val label = Label()
            codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
            run()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.CHECK_ENTITY_TAGS -> {
            load("entity")
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeMapped(Set_size)

            val label = Label()
            codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
            run()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.LIMITER -> {
            val uniqueId = codeWriter.generateUniqueLocal("\$LimitRemaining")
            store("scope", uniqueId)
            val end = codeWriter.defineLocal(uniqueId, Int::class.javaPrimitiveType!!)
            codeWriter.writeInt(instruction["limit"])
            codeWriter.storeLocal(uniqueId)

            val label = Label()
            newScopes[scopeId("scope")] = EmitControlFlow(label, label)

            run()

            end()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.CHECK_LIMIT -> {
            val scope = scopeId("scope")
            codeWriter.methodVisitor.visitIincInsn(codeWriter.getLocalIndex(newTransforms[scope]!!), -1)
            load("scope")
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scope]!!.breakLabel)
        }
        InstructionType.FOR_EACH_PLAYER -> {
            val uidLocal = codeWriter.generateUniqueLocal("\$Players")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getPlayerList)
            codeWriter.writeMapped(PlayerList_getPlayers)
            val end = codeWriter.defineLocal(uidLocal, List::class.java)
            codeWriter.storeLocal(uidLocal)
            codeWriter.createListForEachLoop(uidLocal, Entity::class.java, true) { _, getter ->
                val uidLocal2 = codeWriter.generateUniqueLocal("\$Entity")
                val lend = codeWriter.defineLocal(uidLocal2, Entity::class.java)
                codeWriter.storeLocal(uidLocal2)
                store("entity", uidLocal2)
                run()
                lend()
            }
            end()
        }
        InstructionType.FOR_EACH_ENTITY -> {
            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getAllLevels)
            val end = codeWriter.defineLocal(uidLocalLevels, Iterable::class.java)
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) { _, _ ->
                val uidLocalEntities = codeWriter.generateUniqueLocal("\$Entities")
                codeWriter.writeMapped(ServerLevel_getAllEntities)
                codeWriter.writeMapped(Iterable_iterator)
                val end2 = codeWriter.defineLocal(uidLocalEntities, Iterator::class.java)
                codeWriter.storeLocal(uidLocalEntities)
                codeWriter.createIteratorForEachLoop(uidLocalEntities, Entity::class.java, true) { _, _ ->
                    val uidLocalEntity = codeWriter.generateUniqueLocal("\$Entity")
                    val lend = codeWriter.defineLocal(uidLocalEntity, Entity::class.java)
                    codeWriter.storeLocal(uidLocalEntity)
                    store("entity", uidLocalEntity)
                    run()
                    lend()
                }

                end2()
            }

            end()
        }
        InstructionType.FOR_PLAYER -> {
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeString(instruction["name"])
            codeWriter.writeMapped(PlayerList_getPlayerByName)
            val uidLocal = codeWriter.generateUniqueLocal("\$Player")
            val end = codeWriter.defineLocal(uidLocal, ServerPlayer::class.java)
            store("entity", uidLocal)
            codeWriter.storeLocal(uidLocal)

            run()

            end()
        }
        InstructionType.FOR_ENTITY -> {
            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getAllLevels)
            val end = codeWriter.defineLocal(uidLocalLevels, Iterable::class.java)
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) { controlFlow, _ ->
                fieldStorage.loadUUID(codeWriter, instruction["uuid"])
                codeWriter.writeMapped(ServerLevel_getEntity)
                val uidLocal = codeWriter.generateUniqueLocal("\$Entity")
                val end2 = codeWriter.defineLocal(uidLocal, Entity::class.java)
                store("entity", uidLocal)
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
            codeWriter.newObject(ArrayList::class.java, Collection::class.java) {
                fieldStorage.loadScoreboard(codeWriter)
                codeWriter.writeMapped(Scoreboard_playerScores)
                codeWriter.writeMapped(Map_keys)
            }

            val uidLocal = codeWriter.generateUniqueLocal("\$Array")
            val end = codeWriter.defineLocal(uidLocal, ArrayList::class.java)
            codeWriter.storeLocal(uidLocal)
            if(!instruction.get<Boolean>("single")) {
                codeWriter.createListForEachLoop(uidLocal, String::class.java) { _, getter ->
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, String::class.java)
                    getter()
                    codeWriter.storeLocal(uidLocalName)
                    store("local", uidLocalName)
                    run()
                    end2()
                }
            }else {
                codeWriter.loadLocal(uidLocal)
                codeWriter.writeMapped(ArrayList_size)
                codeWriter.executeIf(Opcodes.IFNE) {
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, String::class.java)
                    codeWriter.loadLocal(uidLocal)
                    codeWriter.writeInt(0)
                    codeWriter.writeMapped(ArrayList_get)
                    codeWriter.castType(String::class.java)
                    codeWriter.storeLocal(uidLocalName)
                    store("local", uidLocalName)
                    run()
                    end2()
                }

            }

            end()

        }
        InstructionType.GET_SCOREBOARD_NAME -> {
            load("entity")
            codeWriter.writeMapped(Entity_getScoreboardName)

            val uidLocal = codeWriter.generateUniqueLocal("\$ScoreboardName")
            val end = codeWriter.defineLocal(uidLocal, String::class.java)
            codeWriter.storeLocal(uidLocal)
            store("name", uidLocal)
            run()
            end()
        }
        InstructionType.SCORE_DATA -> {
            fieldStorage.loadScoreboard(codeWriter)
            load("name")
            load("objective")
            codeWriter.writeMapped(if(instruction["autoCreate"]) Scoreboard_getOrCreatePlayerScore else Scoreboard_getScore)

            val uidLocal = codeWriter.generateUniqueLocal("\$ScoreData")
            val end = codeWriter.defineLocal(uidLocal, Score::class.java)
            codeWriter.storeLocal(uidLocal)
            store("score", uidLocal)
            run()
            end()
        }
        InstructionType.GET_SCORE_VALUE -> {
            load("score")
            codeWriter.writeMapped(Score_getScore)

            val uidLocal = codeWriter.generateUniqueLocal("\$ScoreValue")
            val end = codeWriter.defineLocal(uidLocal, Int::class.javaPrimitiveType!!)
            codeWriter.storeLocal(uidLocal)
            store("scoreValue", uidLocal)
            run()
            end()
        }
        InstructionType.SET_SCORE -> {
            load("score")
            load("value")
            codeWriter.writeMapped(Score_setScore)
        }
        InstructionType.ADD_SCORE -> {
            load("score")
            codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
            codeWriter.writeMapped(Score_getScore)
            load("value")
            codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
            codeWriter.writeMapped(Score_setScore)
        }
        InstructionType.REMOVE_SCORE -> {
            load("score")
            codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
            codeWriter.writeMapped(Score_getScore)
            load("value")
            codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
            codeWriter.writeMapped(Score_setScore)
        }
        InstructionType.RUN_SCORE_OPERATION -> {
            val operation = instruction.get<ScoreboardOperation>("operation")
            when(operation) {
                ScoreboardOperation.SET -> {
                    load("target")
                    load("source")
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.writeMapped(Score_setScore)
                }
                ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                    load("target")
                    codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    load("source")
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                    codeWriter.writeMapped(Score_getScore)
                    if(operation == ScoreboardOperation.PLUS) codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    else if(operation == ScoreboardOperation.MINUS) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    } else codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                    codeWriter.writeMapped(Score_setScore)
                }
                ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                    load("target")
                    codeWriter.writeMapped(Score_getScore)
                    val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                    val endBeh = codeWriter.defineLocal(uniqBeh, Int::class.javaPrimitiveType!!)
                    codeWriter.storeLocal(uniqBeh)

                    load("source")
                    val uniq = codeWriter.generateUniqueLocal("\$Score")
                    val end = codeWriter.defineLocal(uniq, Score::class.java)
                    codeWriter.storeLocal(uniq)
                    codeWriter.loadLocal(uniq)
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scopeId("exceptionScope")]!!.breakLabel)
                    codeWriter.loadLocal(uniq)
                    codeWriter.loadLocal(uniqBeh)
                    codeWriter.loadLocal(uniq)
                    codeWriter.writeMapped(Score_getScore)
                    if(operation == ScoreboardOperation.DIVIDE) codeWriter.writeMapped(Math_floorDiv)
                    else codeWriter.writeMapped(Math_floorMod)
                    codeWriter.writeMapped(Score_setScore)
                    end()
                    endBeh()
                }
                ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                    load("target")
                    codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    load("source")
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                    codeWriter.writeMapped(Score_getScore)
                    if(operation == ScoreboardOperation.MIN) codeWriter.writeMapped(Math_min)
                    else codeWriter.writeMapped(Math_max)
                    codeWriter.writeMapped(Score_setScore)
                }
                ScoreboardOperation.SWAP -> {
                    load("target")
                    val targetId = codeWriter.generateUniqueLocal("\$Score")
                    val targetEnd = codeWriter.defineLocal(targetId, Score::class.java)
                    codeWriter.storeLocal(targetId)

                    load("source")
                    val sourceId = codeWriter.generateUniqueLocal("\$Score")
                    val sourceEnd = codeWriter.defineLocal(sourceId, Score::class.java)
                    codeWriter.storeLocal(sourceId)

                    codeWriter.loadLocal(sourceId)
                    codeWriter.loadLocal(targetId)
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.loadLocal(targetId)
                    codeWriter.loadLocal(sourceId)
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.writeMapped(Score_setScore)
                    codeWriter.writeMapped(Score_setScore)

                    sourceEnd()
                    targetEnd()
                }
            }
        }
        InstructionType.GET_OBJECTIVE -> {
            fieldStorage.loadScoreboard(codeWriter)
            codeWriter.writeString(instruction["name"])
            codeWriter.writeMapped(Scoreboard_getObjective)

            val uidLocal = codeWriter.generateUniqueLocal("\$Objective")
            val end = codeWriter.defineLocal(uidLocal, Objective::class.java)
            codeWriter.storeLocal(uidLocal)
            store("objective", uidLocal)
            run()
            end()
        }
        InstructionType.CALL_FUNCTIONS -> {
            for(func in instruction.get<List<ResourceLocation>>("functions")) {
                if(!definedFunctions.containsKey(func)) {
                    fieldStorage.loadServerFunctionManager(codeWriter)
                    fieldStorage.loadFunction(codeWriter, func)
                }
                codeWriter.loadLocal("CommandSourceStack")
                if(instruction.get<LocationData?>("entity") != null) {
                    load("entity")
                    codeWriter.writeMapped(CommandSourceStack_withEntity)
                }
                if(instruction.get<LocationData?>("world") != null) {
                    load("world")
                    codeWriter.writeMapped(CommandSourceStack_withLevel)
                }
                if(instruction.get<LocationData?>("position") != null) {
                    load("position")
                    codeWriter.writeMapped(CommandSourceStack_withPosition)
                }
                if(instruction.get<LocationData?>("rotation") != null) {
                    load("rotation")
                    codeWriter.writeMapped(CommandSourceStack_withRotation)
                }
                if(definedFunctions.containsKey(func)) {
                    codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}", "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }else {
                    codeWriter.writeNull()
                    codeWriter.writeNull()
                    codeWriter.writeMapped(ServerFunctionManager_execute)
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }
            }
        }
        InstructionType.KILL_ENTITY -> {
            load("entity")
            codeWriter.writeMapped(Entity_kill)
        }
        InstructionType.RESET_SCORE -> {
            fieldStorage.loadScoreboard(codeWriter)
            load("name")
            load("objective")
            codeWriter.writeMapped(Scoreboard_resetPlayerScore)
        }
        InstructionType.CHECK_SCORE_RANGE -> {
            load("score")
            codeWriter.writeMapped(Score_getScore)
            val range = instruction.get<IntRangeArgument>("range")
            val negate = instruction.get<Boolean>("negate")
            if(range.minimum != null && range.maximum == null) {
                codeWriter.writeInt(range.minimum)
                codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPGE else Opcodes.IF_ICMPLT) {
                    run()
                }
            }else if(range.minimum == null && range.maximum != null) {
                codeWriter.writeInt(range.maximum)
                codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPLE else Opcodes.IF_ICMPGT) {
                    run()
                }
            }else if(range.minimum != null && range.minimum == range.maximum) {
                codeWriter.writeInt(range.minimum)
                codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPEQ else Opcodes.IF_ICMPNE) {
                    run()
                }
            }else if(range.minimum != null && range.maximum != null) {
                val uid = codeWriter.generateUniqueLocal("\$Score")
                val end = codeWriter.defineLocal(uid, Int::class.javaPrimitiveType!!)
                codeWriter.storeLocal(uid)
                if(!negate) {
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(range.maximum)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            run()
                        }
                    }
                }else {
                    val skip = Label()
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(range.maximum)
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
            load("source")
            codeWriter.writeMapped(Score_getScore)

            load("target")
            codeWriter.writeMapped(Score_getScore)

            when(instruction.get<ScoreboardCompareOperation>("compareOperation")) {
                ScoreboardCompareOperation.LT -> { codeWriter.executeIf(Opcodes.IF_ICMPLT) { run() } }
                ScoreboardCompareOperation.LE -> { codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() } }
                ScoreboardCompareOperation.EQ -> { codeWriter.executeIf(Opcodes.IF_ICMPEQ) { run() } }
                ScoreboardCompareOperation.NE -> { codeWriter.executeIf(Opcodes.IF_ICMPNE) { run() } }
                ScoreboardCompareOperation.GE -> { codeWriter.executeIf(Opcodes.IF_ICMPGE) { run() } }
                ScoreboardCompareOperation.GT -> { codeWriter.executeIf(Opcodes.IF_ICMPGT) { run() } }
            }

        }
        InstructionType.GET_ENTITY_WORLD -> {
            load("entity")
            codeWriter.writeMapped(Entity_level)

            val uidLocal = codeWriter.generateUniqueLocal("\$World")
            val end = codeWriter.defineLocal(uidLocal, ServerLevel::class.java)
            codeWriter.castType(ServerLevel::class.java)
            codeWriter.storeLocal(uidLocal)
            store("world", uidLocal)
            run()
            end()
        }
        InstructionType.GET_ENTITY_POSITION -> {
            load("entity")
            codeWriter.writeMapped(Entity_position)

            val uidLocal = codeWriter.generateUniqueLocal("\$Position")
            val end = codeWriter.defineLocal(uidLocal, Vec3::class.java)
            codeWriter.storeLocal(uidLocal)
            store("position", uidLocal)
            run()
            end()
        }
        InstructionType.GET_ENTITY_ROTATION -> {
            load("entity")
            codeWriter.writeMapped(Entity_getRotationVector)

            val uidLocal = codeWriter.generateUniqueLocal("\$Rotation")
            val end = codeWriter.defineLocal(uidLocal, Vec2::class.java)
            codeWriter.storeLocal(uidLocal)
            store("rotation", uidLocal)
            run()
            end()
        }
        InstructionType.APPLY_POSITION_WORLD -> {
            val uidLocal = codeWriter.generateUniqueLocal("\$Position")
            codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java) {
                val pos = instruction.get<WorldCoordinates>("position")
                if(pos.isXRelative) {
                    load("from")
                    codeWriter.writeMapped(Vec3_x)
                    if(pos.x != 0.0) {
                        codeWriter.writeDouble(pos.x)
                        codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                    }
                }else {
                    codeWriter.writeDouble(pos.x)
                }
                if(pos.isYRelative) {
                    load("from")
                    codeWriter.writeMapped(Vec3_y)
                    if(pos.y != 0.0) {
                        codeWriter.writeDouble(pos.y)
                        codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                    }
                }else {
                    codeWriter.writeDouble(pos.y)
                }
                if(pos.isZRelative) {
                    load("from")
                    codeWriter.writeMapped(Vec3_z)
                    if(pos.z != 0.0) {
                        codeWriter.writeDouble(pos.z)
                        codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                    }
                }else {
                    codeWriter.writeDouble(pos.z)
                }
            }
            val end = codeWriter.defineLocal(uidLocal, Vec3::class.java)
            codeWriter.storeLocal(uidLocal)
            store("to", uidLocal)
            run()
            end()
        }
        InstructionType.CHECK_ENTITY_TYPES -> {
            val label = Label()
            for(res in instruction.get<List<ResourceLocation>>("types")) {
                load("entity")
                codeWriter.methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, mapEntityTypeClass(res)?.descriptorString()?.run { substring(1, length - 1) })
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, label)
            }
            if(!instruction.get<Boolean>("negated")) {
                val label2 = Label()
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label2)
                codeWriter.methodVisitor.visitLabel(label)
                run()
                codeWriter.methodVisitor.visitLabel(label2)
            }else {
                run()
                codeWriter.methodVisitor.visitLabel(label)
            }
        }
        InstructionType.ALIGN_POSITION -> {
            codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java) {
                val swizzle = instruction.get<SwizzleArgument>("swizzle")
                load("position")
                codeWriter.writeMapped(Vec3_x)
                if(swizzle.x) codeWriter.writeMapped(Math_floor)
                load("position")
                codeWriter.writeMapped(Vec3_y)
                if(swizzle.y) codeWriter.writeMapped(Math_floor)
                load("position")
                codeWriter.writeMapped(Vec3_z)
                if(swizzle.z) codeWriter.writeMapped(Math_floor)
            }

            val uidLocal = codeWriter.generateUniqueLocal("\$Position")
            val end = codeWriter.defineLocal(uidLocal, Vec3::class.java)
            codeWriter.storeLocal(uidLocal)
            store("result", uidLocal)
            run()
            end()
        }
        InstructionType.CHECK_GAMEMODE -> {
            load("entity")
            codeWriter.castType(ServerPlayer::class.java)
            codeWriter.writeMapped(ServerPlayer_gameMode)
            codeWriter.writeMapped(ServerPlayerGameMode_getGameModeForPlayer)
            when(instruction.get<GameType>("gamemode")) {
                GameType.SURVIVAL -> codeWriter.writeMapped(GameType_SURVIVAL)
                GameType.CREATIVE -> codeWriter.writeMapped(GameType_CREATIVE)
                GameType.ADVENTURE -> codeWriter.writeMapped(GameType_ADVENTURE)
                GameType.SPECTATOR -> codeWriter.writeMapped(GameType_SPECTATOR)
            }
            val label = Label()
            codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IF_ACMPNE else Opcodes.IF_ACMPEQ, label)
            run()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.SET_GAMEMODE -> {
            load("entity")
            codeWriter.castType(ServerPlayer::class.java)
            when(instruction.get<GameType>("gamemode")) {
                GameType.SURVIVAL -> codeWriter.writeMapped(GameType_SURVIVAL)
                GameType.CREATIVE -> codeWriter.writeMapped(GameType_CREATIVE)
                GameType.ADVENTURE -> codeWriter.writeMapped(GameType_ADVENTURE)
                GameType.SPECTATOR -> codeWriter.writeMapped(GameType_SPECTATOR)
            }
            codeWriter.writeMapped(ServerPlayer_setGameMode)
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.CHECK_TEAM -> {
            if(instruction["negated"]) {
                load("entity")
                codeWriter.writeMapped(Entity_getTeam)
                val label = Label()
                codeWriter.executeIf(Opcodes.IFNULL) {
                    codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label)
                }
                codeWriter.writeString(instruction["team"])
                load("entity")
                codeWriter.writeMapped(Entity_getTeam)
                codeWriter.writeMapped(Team_getName)
                codeWriter.writeMapped(String_equals)
                codeWriter.executeIf(Opcodes.IFNE) {
                    codeWriter.methodVisitor.visitLabel(label)
                    run()
                }
            }else {
                val label = Label()
                load("entity")
                codeWriter.writeMapped(Entity_getTeam)
                codeWriter.executeIf(Opcodes.IFNONNULL) {
                    codeWriter.writeString(instruction["team"])
                    load("entity")
                    codeWriter.writeMapped(Entity_getTeam)
                    codeWriter.writeMapped(Team_getName)
                    codeWriter.writeMapped(String_equals)
                    codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, label)
                }
                val after = Label()
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, after)
                codeWriter.methodVisitor.visitLabel(label)
                run()
                codeWriter.methodVisitor.visitLabel(after)
            }
        }
        InstructionType.HAS_TEAM -> {
            load("entity")
            codeWriter.writeMapped(Entity_getTeam)
            codeWriter.executeIf(if(!instruction.get<Boolean>("negated")) Opcodes.IFNULL else Opcodes.IFNONNULL) {
                run()
            }
        }
        InstructionType.GET_ATTRIBUTE_INSTANCE -> {
            load("entity")
            codeWriter.castType(LivingEntity::class.java)
            codeWriter.writeMapped(LivingEntity_getAttributes)
            fieldStorage.loadAttribute(codeWriter, instruction["attribute"])
            codeWriter.writeMapped(AttributeMap_getInstance)

            val uidLocal = codeWriter.generateUniqueLocal("\$AttributeInstance")
            val end = codeWriter.defineLocal(uidLocal, AttributeInstance::class.java)
            codeWriter.storeLocal(uidLocal)
            store("instance", uidLocal)
            run()
            end()
        }
        InstructionType.SET_ATTRIBUTE_BASE ->  {
            load("instance")
            codeWriter.writeDouble(instruction["value"])
            codeWriter.writeMapped(AttributeInstance_setBaseValue)
        }
    }
}