package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.context.StringRange

data class BrigadierNode(val source: ImmutableStringReader, val range: StringRange, val name: String, val data: Any) {
    override fun toString(): String {
        return name
    }
}