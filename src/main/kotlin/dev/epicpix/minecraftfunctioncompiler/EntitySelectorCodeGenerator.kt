package dev.epicpix.minecraftfunctioncompiler

import java.lang.UnsupportedOperationException
import java.util.*

object EntitySelectorCodeGenerator {
    fun generateForEachEntity(cw: CodeWriter, stringSelector: String, allowEntities: Boolean, allowMultiple: Boolean, run: () -> Unit) {
        if(stringSelector[0] == '@') {
            return
        }
        runCatching { UUID.fromString(stringSelector) }.fold({
            if(!allowEntities) {
                throw UnsupportedOperationException("Invalid selector")
            }
        }) {

        }
    }

    fun checkEntityPredicate(cw: CodeWriter, stringSelector: String) {

    }
}