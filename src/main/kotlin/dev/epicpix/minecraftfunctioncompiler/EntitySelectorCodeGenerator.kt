package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

object EntitySelectorCodeGenerator {

    fun generateIfEntityExists(cw: CodeWriter, selector: SelectorArgument, context: GeneratorCommandContext, run: () -> Unit) {
        if(selector.entityUuid != null) {
            cw.visitForEachLevel { controlFlowRun, getter ->
                getter()
                cw.writeUUID(selector.entityUuid)
                cw.callMethod("net/minecraft/server/level/ServerLevel", "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;")
                cw.executeIf(Opcodes.IFNONNULL) {
                    run()
                    controlFlowRun.brk()
                }
            }
            return
        }

        if(selector.playerName != null) {
            cw.visitGetPlayerList()
            cw.writeString(selector.playerName)
            cw.callMethod("net/minecraft/server/players/PlayerList", "getPlayerByName", "(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;")
            cw.executeIf(Opcodes.IFNONNULL, exec = run)
            return
        }

        iterateEntities(cw, context, selector.copy(limit = 1)) {
            run()
        }
    }

    fun generateForEachEntity(cw: CodeWriter, selector: SelectorArgument, context: GeneratorCommandContext, run: (getter: () -> Unit) -> Unit) {
        if(selector.entityUuid != null) {
            cw.visitForEachLevel { controlFlowRun, getter ->
                getter()
                cw.writeUUID(selector.entityUuid)
                cw.callMethod("net/minecraft/server/level/ServerLevel", "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;")
                val uidLocal = cw.generateUniqueLocal("\$Entity")
                cw.storeLocal(uidLocal, "Lnet/minecraft/world/entity/Entity;") {
                    cw.loadLocal(uidLocal)
                    cw.executeIf(Opcodes.IFNONNULL) {
                        run {
                            cw.loadLocal(uidLocal)
                        }
                        controlFlowRun.brk()
                    }
                }
            }
            return
        }

        if(selector.playerName != null) {
            cw.visitGetPlayerList()
            cw.writeString(selector.playerName)
            cw.callMethod("net/minecraft/server/players/PlayerList", "getPlayerByName", "(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;")
            val uidLocal = cw.generateUniqueLocal("\$Player")
            cw.storeLocal(uidLocal, "Lnet/minecraft/server/level/ServerPlayer;") {
                cw.loadLocal(uidLocal)
                cw.executeIf(Opcodes.IFNONNULL) {
                    run {
                        cw.loadLocal(uidLocal)
                    }
                }
            }
            return
        }

        iterateEntities(cw, context, selector, run)
    }

    fun iterateEntities(cw: CodeWriter, context: GeneratorCommandContext, selector: SelectorArgument, run: (getter: () -> Unit) -> Unit) {
        if(selector.self) {
            val skipLabel = Label()
            context.getEntity(cw)
            cw.methodVisitor.visitJumpInsn(Opcodes.IFNULL, skipLabel)
            checkEntityPredicates(cw, null, selector, {
                context.getEntity(cw)
            }, ControlFlowRun({ cw.methodVisitor.visitJumpInsn(it, skipLabel) }, { cw.methodVisitor.visitJumpInsn(it, skipLabel) }), run)
            cw.methodVisitor.visitLabel(skipLabel)
            return
        }

        if(selector.entityType == "minecraft:player") {
            if(selector.limit == 1 || selector.limit == null) {
                cw.visitForEachPlayer { controlFlow, getter ->
                    checkEntityPredicates(cw, null, selector, getter, controlFlow, run)
                }
            }else {
                val uidLocal = cw.generateUniqueLocal("\$Index")
                cw.writeInt(0)
                cw.storeLocal(uidLocal, "I") {
                    cw.visitForEachPlayer { controlFlow, getter ->
                        checkEntityPredicates(cw, uidLocal, selector, getter, controlFlow, run)
                    }
                }
            }
            return
        }

        if(selector.limit == 1 || selector.limit == null) {
            cw.visitForEachEntity { controlFlow, getter ->
                checkEntityPredicates(cw, null, selector, getter, controlFlow, run)
            }
        }else {
            val uidLocal = cw.generateUniqueLocal("\$Index")
            cw.writeInt(0)
            cw.storeLocal(uidLocal, "I") {
                cw.visitForEachEntity { controlFlow, getter ->
                    checkEntityPredicates(cw, uidLocal, selector, getter, controlFlow, run)
                }
            }
        }
    }

    fun checkEntityPredicates(cw: CodeWriter, indexLocal: String?, selector: SelectorArgument, getter: () -> Unit, controlFlow: ControlFlowRun, exec: (getter: () -> Unit) -> Unit) {
        if(selector.hasTags != null) {
            getter()
            cw.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            cw.callInterfaceMethod("java/util/Set", "size", "()I")
            controlFlow.cont(if(selector.hasTags) Opcodes.IFNE else Opcodes.IFEQ)

        }
        for(tag in selector.tags) {
            getter()
            cw.callMethod("net/minecraft/world/entity/Entity", "getTags", "()Ljava/util/Set;")
            cw.writeString(tag.data)
            cw.callInterfaceMethod("java/util/Set", "contains", "(Ljava/lang/Object;)Z")
            controlFlow.cont(if(tag.negated) Opcodes.IFNE else Opcodes.IFEQ)
        }

        exec(getter)

        if(selector.limit == 1) {
            controlFlow.brk()
        }else if(selector.limit != null) {
            cw.methodVisitor.visitIincInsn(cw.getLocal(indexLocal!!)!!, 1)
            cw.loadLocal(indexLocal)
            cw.writeInt(selector.limit)
            controlFlow.brk(Opcodes.IF_ICMPGE)
        }
    }
}