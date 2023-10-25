package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Opcodes
import java.lang.UnsupportedOperationException
import java.util.*

object EntitySelectorCodeGenerator {
    fun generateForEachEntity(cw: CodeWriter, stringSelector: String, allowEntities: Boolean, allowMultiple: Boolean, run: () -> Unit) {
        if(stringSelector[0] == '@') {
            throw UnsupportedOperationException("Unsupported selector")
        }
        runCatching { UUID.fromString(stringSelector) }.fold({
            if(!allowEntities) throw UnsupportedOperationException("Invalid selector")
            cw.visitForEachLevel { controlFlowRun, getter ->
                getter()
                cw.writeUUID(it)
                cw.callMethod("net/minecraft/server/level/ServerLevel", "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;")
                cw.defineLocal("\$Entity", "Lnet/minecraft/world/entity/Entity;") {
                    cw.storeLocal("\$Entity");
                    cw.loadLocal("\$Entity")
                    cw.executeIf(Opcodes.IFNONNULL) {
                        cw.loadLocal("\$Entity")
                        run()
                        controlFlowRun.brk()
                    }
                }
            }
        }) {
            throw UnsupportedOperationException("Unsupported selector")
        }
    }

    fun checkEntityPredicate(cw: CodeWriter, stringSelector: String) {

    }
}