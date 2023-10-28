package dev.epicpix.minecraftfunctioncompiler

fun CodeWriter.generateOrGetLocal(name: String, descriptor: String, load: Boolean = true, save: Boolean = true, getter: CodeWriter.() -> Unit) {
    if(load && getLocal(name) != null) {
        loadLocal(name)
        return
    }
    if(!save) {
        if(!load) return
        getter()
        return
    }
    if(getLocal(name) == null) {
        getter()
        defineGlobalLocal(name, descriptor)
        storeLocal(name)
    }
    if(load) loadLocal(name)
}

fun CodeWriter.visitGetCommandSourceStack() {
    loadLocal("CommandSourceStack")
}

fun CodeWriter.visitGetServer(load: Boolean = true) {
    generateOrGetLocal("MinecraftServer", "Lnet/minecraft/server/MinecraftServer;", load, save = !isInControlFlow()) {
        visitGetCommandSourceStack()
        callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
    }
}

fun CodeWriter.visitGetScoreboard(load: Boolean = true) {
    generateOrGetLocal("ServerScoreboard", "Lnet/minecraft/server/ServerScoreboard;", load, save = !isInControlFlow()) {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;")
    }
}

fun CodeWriter.visitGetPlayerList(load: Boolean = true) {
    generateOrGetLocal("PlayerList", "Lnet/minecraft/server/players/PlayerList;", load) {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getPlayerList", "()Lnet/minecraft/server/players/PlayerList;")
    }
}

fun CodeWriter.visitForEachLevel(exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
    val uidLocal = generateUniqueLocal("\$Iterator")
    defineLocal(uidLocal, "Ljava/lang/Iterable;") {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getAllLevels", "()Ljava/lang/Iterable;")
        storeLocal(uidLocal)
        createIterableForEachLoop(uidLocal) { controlFlow, getter ->
            exec(controlFlow) {
                getter()
                castType("net/minecraft/server/level/ServerLevel")
            }
        }
    }
}