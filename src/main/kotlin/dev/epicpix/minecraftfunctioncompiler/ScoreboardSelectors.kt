package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Opcodes

fun runScoreboardOperation(targets: ScoreHolderArgument, objective: String, context: GeneratorCommandContext, cw: CodeWriter, run: (context: GeneratorCommandContext) -> Unit) {
    visitForEachScoreHolder(cw, context, targets) { getter ->
        context.runWithObjective(cw, objective) { obj, context ->
            cw.visitGetScoreboard()
            getter()
            cw.loadLocal(obj)
            run(context)
        }
    }
}

fun visitGetSingleScoreHolder(cw: CodeWriter, context: GeneratorCommandContext, scoreHolder: ScoreHolderArgument, run: (getter: () -> Unit) -> Unit) {
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

fun visitForEachScoreHolder(cw: CodeWriter, context: GeneratorCommandContext, scoreHolder: ScoreHolderArgument, run: (getter: () -> Unit) -> Unit) {
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