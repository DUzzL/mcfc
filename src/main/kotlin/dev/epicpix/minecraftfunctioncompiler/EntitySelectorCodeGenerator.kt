package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.arguments.SelectorArgument
import org.objectweb.asm.Opcodes
import java.lang.UnsupportedOperationException

object EntitySelectorCodeGenerator {

    fun generateForEachEntity(cw: CodeWriter, selector: SelectorArgument, allowEntities: Boolean, allowMultiple: Boolean, run: (getter: () -> Unit) -> Unit) {
        if(selector.entityUuid != null) {
            if(!allowEntities) throw UnsupportedOperationException("Invalid selector")
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

        if(selector.self) {
            cw.visitGetSelfEntity()
            cw.executeIf(Opcodes.IFNONNULL) {
                run {
                    cw.visitGetSelfEntity()
                }
            }
            return
        }

        throw UnsupportedOperationException("Unsupported selector")
    }

    fun checkEntityPredicate(cw: CodeWriter, stringSelector: String) {

    }
}