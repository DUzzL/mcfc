package dev.epicpix.minecraftfunctioncompiler

import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

object CodeGenerator {

    fun generateCommandLike(commandLike: CommandLike, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        if(commandLike is Command) {
            generateCommand(commandLike, context, cw, definedFunctions)
        }else if(commandLike is CommandBlock) {
            generateCommandBlock(commandLike, context, cw, definedFunctions)
        }
    }

    fun generateCommandBlock(block: CommandBlock, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        val skipLabel = Label()
        var index = 0
        fun fullfilRequirements(context: GeneratorCommandContext) {
            if(index < block.requirements.size) {
                fulfillRequirement(block.requirements[index++], context, skipLabel, cw) { fullfilRequirements(it) }
            }else {
                for(commandLike in block.commands) {
                    generateCommandLike(commandLike, context, cw, definedFunctions)
                }
            }
        }
        fullfilRequirements(context)
        cw.methodVisitor.visitLabel(skipLabel)
    }

    fun fulfillRequirement(requirement: CommandRequirement, context: GeneratorCommandContext, skipLabel: Label, cw: CodeWriter, next: (context: GeneratorCommandContext) -> Unit) {
        when(requirement.type) {
            CommandRequirementType.OBJECTIVE_EXISTS -> {
                context.runWithObjective(cw, requirement.data["objective"] as String) { _, ctx -> next(ctx) }
            }
            CommandRequirementType.OBJECTIVE_NOT_LOCKED -> {
                context.runWithObjective(cw, requirement.data["objective"] as String) { obj, ctx ->
                    cw.loadLocal(obj)
                    cw.callMethod("net/minecraft/world/scores/Objective", "getCriteria", "()Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;")
                    cw.callMethod("net/minecraft/world/scores/criteria/ObjectiveCriteria", "isReadOnly", "()Z")
                    cw.executeIf(Opcodes.IFNE) {
                        next(ctx)
                    }
                }
            }
            CommandRequirementType.ENTITY_SELF_EXISTS -> {
                context.getEntity(cw)
                cw.methodVisitor.visitJumpInsn(Opcodes.IFNULL, skipLabel)
            }
        }
    }

    fun generateCommand(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        when(command.type) {
            CommandType.KILL -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
                    getter()
                    cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_ADD -> {
                runScoreboardOperation(command["targets"], command["objective"], context, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.IADD)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_REMOVE -> {
                runScoreboardOperation(command["targets"], command["objective"], context, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.ISUB)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_RESET -> {
                if(command.params?.get("objective") != null) {
                    runScoreboardOperation(command["targets"], command["objective"], context, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
                    }
                }else {
                    throw UnsupportedOperationException("TODO: implement /scoreboard players reset <targets> [<objective>]")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_SET -> {
                runScoreboardOperation(command["targets"], command["objective"], context, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInt(command["score"])
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_OPERATION -> {
                context.runWithObjective(cw, command["sourceObjective"]) { sourceObjective, context ->
                    context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
                        visitForEachScoreHolder(cw, context, command["targets"]) { targetGetter ->
                            visitForEachScoreHolder(cw, context, command["source"]) { sourceGetter ->
                                fun source() {
                                    cw.visitGetScoreboard()
                                    sourceGetter()
                                    cw.loadLocal(sourceObjective)
                                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                }
                                fun target() {
                                    cw.visitGetScoreboard()
                                    targetGetter()
                                    cw.loadLocal(targetObjective)
                                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                }
                                when(command.get<String>("operation")) {
                                    "=" -> {
                                        target()
                                        source()
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    }
                                    "+=" -> {
                                        target()
                                        cw.writeInstruction(Opcodes.DUP)
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        source()
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        cw.writeInstruction(Opcodes.IADD)
                                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    }
                                    "-=" -> {
                                        target()
                                        cw.writeInstruction(Opcodes.DUP)
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        source()
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        cw.writeInstruction(Opcodes.ISUB)
                                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    }
                                    "*=" -> {
                                        target()
                                        cw.writeInstruction(Opcodes.DUP)
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        source()
                                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                        cw.writeInstruction(Opcodes.IMUL)
                                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    }
                                    else -> throw UnsupportedOperationException("Unknown operation operator: ${command.get<String>("operation")}")
                                }
                            }
                        }
                    }
                }
            }

            CommandType.TAG_ADD -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
                    getter()
                    cw.writeString(command["name"])
                    cw.callMethod("net/minecraft/world/entity/Entity", "addTag", "(Ljava/lang/String;)Z")
                    cw.methodVisitor.visitInsn(Opcodes.POP)
                }
            }

            CommandType.TAG_REMOVE -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
                    getter()
                    cw.writeString(command["name"])
                    cw.callMethod("net/minecraft/world/entity/Entity", "removeTag", "(Ljava/lang/String;)Z")
                    cw.methodVisitor.visitInsn(Opcodes.POP)
                }
            }

            CommandType.EXECUTE_AS -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
                    val entityUid = cw.generateUniqueLocal("CurrentEntity")
                    val newContext = context.copy(entityLocal = entityUid, originalEntity = context.originalEntity && command.get<SelectorArgument>("targets").self)
                    getter()
                    cw.storeLocal(entityUid, "Lnet/minecraft/world/entity/Entity;") {
                        generateCommandLike(command["command"], newContext, cw, definedFunctions)
                    }
                }
            }

            CommandType.EXECUTE_IF_ENTITY -> {
                EntitySelectorCodeGenerator.generateIfEntityExists(cw, command["entities"], context) {
                    generateCommandLike(command["command"], context, cw, definedFunctions)
                }
            }

            CommandType.EXECUTE_UNLESS_ENTITY -> {
                EntitySelectorCodeGenerator.generateUnlessEntityExists(cw, command["entities"], context) {
                    generateCommandLike(command["command"], context, cw, definedFunctions)
                }
            }

            CommandType.EXECUTE_IF_SCORE_RANGE -> {
                context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
                    visitGetSingleScoreHolder(cw, context, command["target"]) { getter ->
                        cw.visitGetScoreboard()
                        getter()
                        cw.loadLocal(targetObjective)
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        val uidLocal = cw.generateUniqueLocal("\$Score")
                        cw.storeLocal(uidLocal, "Lnet/minecraft/world/scores/Score;") {
                            val range = command.get<IntRangeArgument>("range")
                            if (range.minimum != null && range.maximum == null) {
                                loadLocal(uidLocal)
                                callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                writeInt(range.minimum)
                                executeIf(Opcodes.IF_ICMPGE) {
                                    generateCommandLike(command["command"], context, cw, definedFunctions)
                                }
                            } else if (range.maximum != null && range.minimum == null) {
                                loadLocal(uidLocal)
                                callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                writeInt(range.maximum)
                                executeIf(Opcodes.IF_ICMPLE) {
                                    generateCommandLike(command["command"], context, cw, definedFunctions)
                                }
                            } else if (range.minimum != null && range.minimum == range.maximum) {
                                loadLocal(uidLocal)
                                callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                writeInt(range.minimum)
                                executeIf(Opcodes.IF_ICMPEQ) {
                                    generateCommandLike(command["command"], context, cw, definedFunctions)
                                }
                            } else if (range.minimum != null && range.maximum != null) {
                                loadLocal(uidLocal)
                                callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                writeInt(range.minimum)
                                executeIf(Opcodes.IF_ICMPGE) {
                                    loadLocal(uidLocal)
                                    callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                    writeInt(range.maximum)
                                    executeIf(Opcodes.IF_ICMPLE) {
                                        generateCommandLike(command["command"], context, cw, definedFunctions)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CommandType.EXECUTE_IF_SCORE_COMPARE -> {
                context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
                    context.runWithObjective(cw, command["sourceObjective"]) { sourceObjective, context ->
                        visitGetSingleScoreHolder(cw, context, command["target"]) { getterTarget ->
                            visitGetSingleScoreHolder(cw, context, command["source"]) { sourceSource ->
                                cw.visitGetScoreboard()
                                getterTarget()
                                cw.loadLocal(targetObjective)
                                cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.visitGetScoreboard()
                                sourceSource()
                                cw.loadLocal(sourceObjective)
                                cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")

                                val operator = command.get<String>("comparison")

                                when(operator) {
                                    "<" -> {
                                        cw.executeIf(Opcodes.IF_ICMPLT) {
                                            generateCommandLike(command["command"], context, cw, definedFunctions)
                                        }
                                    }
                                    "<=" -> {
                                        cw.executeIf(Opcodes.IF_ICMPLE) {
                                            generateCommandLike(command["command"], context, cw, definedFunctions)
                                        }
                                    }
                                    "=" -> {
                                        cw.executeIf(Opcodes.IF_ICMPEQ) {
                                            generateCommandLike(command["command"], context, cw, definedFunctions)
                                        }
                                    }
                                    ">" -> {
                                        cw.executeIf(Opcodes.IF_ICMPGT) {
                                            generateCommandLike(command["command"], context, cw, definedFunctions)
                                        }
                                    }
                                    ">=" -> {
                                        cw.executeIf(Opcodes.IF_ICMPGE) {
                                            generateCommandLike(command["command"], context, cw, definedFunctions)
                                        }
                                    }
                                    else -> throw UnsupportedOperationException("Unknown score comparison operator: $operator")
                                }
                            }
                        }
                    }
                }
            }

            CommandType.FUNCTION -> {
                for(func in command.get<List<ResourceLocation>>("commands")) {
                    if(definedFunctions.containsKey(func)) {
                        cw.visitGetCommandSourceStack()
                        if(!context.originalEntity) {
                            context.getEntity(cw)
                            cw.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
                        }
                        cw.callFunction("function/${func.namespace}/${func.path}", "run", "(Lnet/minecraft/commands/CommandSourceStack;)I")
                        cw.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        cw.visitGetServer()
                        cw.callMethod("net/minecraft/server/MinecraftServer", "getFunctions", "()Lnet/minecraft/server/ServerFunctionManager;")
                        cw.methodVisitor.visitInsn(Opcodes.DUP)
                        cw.getObjectField("net/minecraft/server/ServerFunctionManager", "library", "Lnet/minecraft/server/ServerFunctionLibrary;")
                        cw.getObjectField("net/minecraft/server/ServerFunctionLibrary", "functions", "Ljava/util/Map;")
                        cw.writeResourceLocation(func)
                        cw.callInterfaceMethod("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                        cw.castType("net/minecraft/commands/CommandFunction")
                        cw.visitGetCommandSourceStack()
                        if(!context.originalEntity) {
                            context.getEntity(cw)
                            cw.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
                        }
                        cw.writeNull()
                        cw.writeNull()
                        cw.callMethod("net/minecraft/server/ServerFunctionManager", "execute", "(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager\$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I")
                        cw.methodVisitor.visitInsn(Opcodes.POP)
                    }
                }
            }

            else -> throw UnsupportedOperationException("Cannot compile command: ${command.type}: ${command.params}")
        }
    }

    private fun runScoreboardOperation(targets: ScoreHolderArgument, objective: String, context: GeneratorCommandContext, cw: CodeWriter, run: (context: GeneratorCommandContext) -> Unit) {
        visitForEachScoreHolder(cw, context, targets) { getter ->
            context.runWithObjective(cw, objective) { obj, context ->
                cw.visitGetScoreboard()
                getter()
                cw.loadLocal(obj)
                run(context)
            }
        }
    }

    private fun visitGetSingleScoreHolder(cw: CodeWriter, context: GeneratorCommandContext, scoreHolder: ScoreHolderArgument, run: (getter: () -> Unit) -> Unit) {
        if(scoreHolder.all) {
            cw.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                cw.visitGetScoreboard()
                cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
            }

            val uidLocal = cw.generateUniqueLocal("\$Array")
            cw.storeLocal(uidLocal, "Ljava/util/ArrayList;") {
                cw.loadLocal(uidLocal)
                cw.callMethod("java/util/ArrayList", "size", "()I")
                cw.executeIf(Opcodes.IFNE) {
                    run {
                        cw.loadLocal(uidLocal)
                        cw.writeInt(0)
                        cw.callMethod("java/util/ArrayList", "get", "(I)Ljava/lang/Object;")
                        cw.castType("java/lang/String")
                    }
                }
            }
            return
        }

        if(scoreHolder.name != null) {
            run {
                cw.writeString(scoreHolder.name)
            }
            return
        }

        if(scoreHolder.selector != null) {
            EntitySelectorCodeGenerator.generateForEachEntity(cw, scoreHolder.selector.copy(limit = 1), context) { getter ->
                val uidLocal = cw.generateUniqueLocal("\$EntityName")
                getter()
                cw.callMethod("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                cw.storeLocal(uidLocal, "Ljava/lang/String;") {
                    run {
                        cw.loadLocal(uidLocal)
                    }
                }
            }
        }
    }

    private fun visitForEachScoreHolder(cw: CodeWriter, context: GeneratorCommandContext, scoreHolder: ScoreHolderArgument, run: (getter: () -> Unit) -> Unit) {
        if(scoreHolder.all) {
            cw.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                cw.visitGetScoreboard()
                cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
            }

            val uidLocal = cw.generateUniqueLocal("\$Array")
            cw.storeLocal(uidLocal, "Ljava/util/ArrayList;") {
                cw.createListForEachLoop(uidLocal) { _, getter ->
                    run {
                        getter()
                        cw.castType("java/lang/String")
                    }
                }
            }
            return
        }

        if(scoreHolder.name != null) {
            run {
                cw.writeString(scoreHolder.name)
            }
            return
        }

        if(scoreHolder.selector != null) {
            EntitySelectorCodeGenerator.generateForEachEntity(cw, scoreHolder.selector, context) { getter ->
                val uidLocal = cw.generateUniqueLocal("\$EntityName")
                getter()
                cw.callMethod("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                cw.storeLocal(uidLocal, "Ljava/lang/String;") {
                    run {
                        cw.loadLocal(uidLocal)
                    }
                }
            }
        }
    }

}
