package dev.epicpix.minecraftfunctioncompiler.v1_20_1

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.GameType

fun ResourceLocation.toDataLocation() = DataLocation(namespace, path)

fun GameType.toGamemode() = when(this) {
    GameType.SURVIVAL -> Gamemode.SURVIVAL
    GameType.CREATIVE -> Gamemode.CREATIVE
    GameType.ADVENTURE -> Gamemode.ADVENTURE
    GameType.SPECTATOR -> Gamemode.SPECTATOR
}