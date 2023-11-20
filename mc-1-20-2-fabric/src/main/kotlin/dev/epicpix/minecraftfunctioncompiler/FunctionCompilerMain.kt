package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.commands.initBaseSelectorOptions
import dev.epicpix.minecraftfunctioncompiler.commands.registerSelectorOption
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData
import dev.epicpix.minecraftfunctioncompiler.data.mapEntityTypeClass
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.registerBaseInstructionEmitters
import dev.epicpix.minecraftfunctioncompiler.emitter.registerInstructionEmitters
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter
import net.fabricmc.api.ModInitializer
import net.minecraft.DetectedVersion
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.GameType

object FunctionCompilerMain : ModInitializer {
    override fun onInitialize() {
        FunctionCompilerReporter.setMinecraftVersion(DetectedVersion.BUILT_IN.name, Minecraft.getInstance().versionType)
        registerBaseInstructionEmitters()
        registerInstructionEmitters()
        initBaseSelectorOptions()
        registerSelectorOption("type") { data ->
            val negated = if(peek() == '!') { read(); true } else false
            val tag = if(peek() == '#') { read(); true } else false
            val type = ResourceLocation.read(this)
            val types = if(!tag) listOf(type.toDataLocation()) else {
                val tagKey = TagKey.create(Registries.ENTITY_TYPE, type)
                val typeData = BuiltInRegistries.ENTITY_TYPE.getTag(tagKey)
                if(typeData.isEmpty) {
                    listOf()
                }else {
                    BuiltInRegistries.ENTITY_TYPE.getTag(tagKey).get().map { it.unwrapKey().get().location() }.toList().map { it.toDataLocation() }
                }
            }
            types.forEach {
                if(mapEntityTypeClass(it) == null) throw UnsupportedOperationException("Unsupported entity type: $it")
            }
            data.entityTypes = NegatableData(types, negated)
        }
        registerSelectorOption("gamemode") { data ->
            val negated = if(peek() == '!') { read(); true } else false
            data.gamemode = NegatableData(GameType.byName(readUnquotedString(), null)!!.toGamemode(), negated)
            data.entityTypes = NegatableData(listOf(DataLocation("minecraft", "player")), false)
        }
    }
}