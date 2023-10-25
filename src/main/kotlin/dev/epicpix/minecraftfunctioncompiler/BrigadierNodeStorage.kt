package dev.epicpix.minecraftfunctioncompiler

class BrigadierNodeStorage(val nodes: MutableList<BrigadierNode>) {

    operator fun get(name: String): BrigadierNode? {
        return nodes.find { it.name == name }
    }

    operator fun get(index: Int): BrigadierNode {
        return nodes[index]
    }

    override fun toString(): String {
        return nodes.toString()
    }

}