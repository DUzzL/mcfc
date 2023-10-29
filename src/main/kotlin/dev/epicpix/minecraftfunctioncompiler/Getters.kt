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
    visitGetServer()
    callMethod("net/minecraft/server/MinecraftServer", "getAllLevels", "()Ljava/lang/Iterable;")
    storeLocal(uidLocal, "Ljava/lang/Iterable;") {
        createIterableForEachLoop(uidLocal) { controlFlow, getter ->
            exec(controlFlow) {
                getter()
                castType("net/minecraft/server/level/ServerLevel")
            }
        }
    }
}

fun CodeWriter.visitForEachEntity(exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
    visitForEachLevel { controlFlow, getter ->
        val uidLocal = generateUniqueLocal("\$Iterator")
        getter()
        callMethod("net/minecraft/server/level/ServerLevel", "getAllEntities", "()Ljava/lang/Iterable;")
        storeLocal(uidLocal, "Ljava/lang/Iterable;") {
            createIterableForEachLoop(uidLocal) { localControlFlow, getter ->
                exec(ControlFlowRun(localControlFlow.continueJump, controlFlow.breakJump)) {
                    getter()
                    castType("net/minecraft/world/entity/Entity")
                }
            }
        }
    }
}

fun CodeWriter.visitForEachPlayer(exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
    val uidLocal = generateUniqueLocal("\$Players")
    visitGetPlayerList()
    callMethod("net/minecraft/server/players/PlayerList", "getPlayers", "()Ljava/util/List;")
    storeLocal(uidLocal, "Ljava/util/List;") {
        createListForEachLoop(uidLocal) { controlFlow, getter ->
            exec(controlFlow) {
                getter()
                castType("net/minecraft/world/entity/Entity")
            }
        }
    }
}