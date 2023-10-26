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

        throw UnsupportedOperationException("Unsupported selector")
    }

    fun checkEntityPredicate(cw: CodeWriter, stringSelector: String) {

    }
}