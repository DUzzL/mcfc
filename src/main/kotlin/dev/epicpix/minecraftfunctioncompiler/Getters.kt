package dev.epicpix.minecraftfunctioncompiler

fun CodeWriter.generateOrGetLocal(name: String, descriptor: String, load: Boolean = true, getter: CodeWriter.() -> Unit) {
    if (getLocal(name) == null) {
        getter()
        defineGlobalLocal(name, descriptor)
        storeLocal(name)
    }
    if(load) loadLocal(name)
}

fun CodeWriter.visitGetCommandSourceStack() {
    loadLocal("CommandSourceStack")
}

fun CodeWriter.visitGetServer() {
    generateOrGetLocal("MinecraftServer", "Lnet/minecraft/server/MinecraftServer;") {
        visitGetCommandSourceStack()
        callMethod("net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;")
    }
}

fun CodeWriter.visitGetScoreboard() {
    generateOrGetLocal("ServerScoreboard", "Lnet/minecraft/server/ServerScoreboard;") {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;")
    }
}

fun CodeWriter.visitGetObjective(objective: String, load: Boolean = true) {
    generateOrGetLocal("Objective-$objective", "Lnet/minecraft/world/scores/Objective;", load) {
        visitGetScoreboard()
        writeString(objective)
        callMethod("net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")
    }
}

fun CodeWriter.visitGetObjectiveScore(objective: String, holder: String) {
    visitGetScoreboard()
    writeString(holder)
    visitGetObjective(objective)
    callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
}

fun CodeWriter.visitGetPlayerList() {
    generateOrGetLocal("PlayerList", "Lnet/minecraft/server/players/PlayerList;") {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getPlayerList", "()Lnet/minecraft/server/players/PlayerList;")
    }
}

fun CodeWriter.visitForEachLevel(exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
    defineLocal("\$Iterator", "Ljava/lang/Iterable;") {
        visitGetServer()
        callMethod("net/minecraft/server/MinecraftServer", "getAllLevels", "()Ljava/lang/Iterable;")
        storeLocal("\$Iterator")
        createIterableForEachLoop("\$Iterator") { controlFlow, getter ->
            exec(controlFlow) {
                getter()
                castType("net/minecraft/server/level/ServerLevel")
            }
        }
    }
}