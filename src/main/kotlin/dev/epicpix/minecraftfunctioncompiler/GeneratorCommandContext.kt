package dev.epicpix.minecraftfunctioncompiler

class GeneratorCommandContext {

    fun getEntity(cw: CodeWriter) {
        cw.generateOrGetLocal("CurrentEntity", "Lnet/minecraft/world/entity/Entity;", save = !cw.isInControlFlow()) {
            visitGetCommandSourceStack()
            callMethod("net/minecraft/commands/CommandSourceStack", "getEntity", "()Lnet/minecraft/world/entity/Entity;")
        }
    }

}