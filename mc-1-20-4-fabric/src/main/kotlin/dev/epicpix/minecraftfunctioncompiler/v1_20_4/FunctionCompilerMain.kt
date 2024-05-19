package dev.epicpix.minecraftfunctioncompiler.v1_20_4

import dev.epicpix.minecraftfunctioncompiler.commands.initBaseSelectorOptions
import dev.epicpix.minecraftfunctioncompiler.commands.registerSelectorOption
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.data.RegistryMapping
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.Emitter2
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.registerInstructionEmitters
import net.fabricmc.api.ModInitializer
import net.minecraft.advancements.critereon.WrappedMinMaxBounds
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.level.GameType

object FunctionCompilerMain : ModInitializer {
    override fun onInitialize() {
        EmitterBaseOptions.registerInstructionEmitters()
        Emitter2.registerInstructionEmitters()
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
                if(RegistryMapping.mapEntityTypeClass(it) == null) throw UnsupportedOperationException("Unsupported entity type: $it")
            }
            data.entityTypes = NegatableData(types, negated)
        }
        registerSelectorOption("gamemode") { data ->
            val negated = if(peek() == '!') { read(); true } else false
            data.gamemode = NegatableData(GameType.byName(readUnquotedString(), null)!!.toGamemode(), negated)
            data.entityTypes = NegatableData(listOf(DataLocation("minecraft", "player")), false)
        }
        registerSelectorOption("x_rotation") { data ->
            val bounds = WrappedMinMaxBounds.fromReader(this, true, Mth::wrapDegrees)
            data.pitch = FloatRangeArgument(bounds.min, bounds.max)
        }
        registerSelectorOption("y_rotation") { data ->
            val bounds = WrappedMinMaxBounds.fromReader(this, true, Mth::wrapDegrees)
            data.yaw = FloatRangeArgument(bounds.min, bounds.max)
        }
    }
}