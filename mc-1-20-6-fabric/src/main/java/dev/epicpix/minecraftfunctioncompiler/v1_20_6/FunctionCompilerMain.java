package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.v1_20_6.data.RegistryMapping;
import dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.Emitter2;
import dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.Emitter;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

import java.util.List;

public class FunctionCompilerMain implements ModInitializer {
    public void onInitialize() {
        EmitterBaseOptions.registerInstructionEmitters();
        Emitter2.registerInstructionEmitters();
        Emitter.registerInstructionEmitters();
        SelectorParser.initBaseSelectorOptions();
        SelectorParser.registerSelectorOption("type", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var tag = reader.skipIfNext('#');
            var type = reader.readDataLocation();
            List<DataLocation> types;
            if(!tag) {
                types = List.of(type);
            }else {
                var tagKey = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(type.namespace(), type.path()));
                var typeData = BuiltInRegistries.ENTITY_TYPE.getTag(tagKey);
                types = typeData
                        .map(holders -> holders.stream()
                                .map(it -> it
                                        .unwrapKey()
                                        .get()
                                        .location())
                                .map(it -> new DataLocation(it.getNamespace(), it.getPath()))
                                .toList()
                        ).orElseGet(List::of);
            }
            for(var it : types) {
                if(RegistryMapping.mapEntityTypeClass(it) == null) throw new UnsupportedOperationException("Unsupported entity type: " + it);
            }
            data.entityTypes = new NegatableData<>(types, negated);
        });
        SelectorParser.registerSelectorOption("gamemode", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            data.gamemode = new NegatableData<>(switch(GameType.byName(reader.readWord(), GameType.DEFAULT_MODE)) {
                case SURVIVAL -> Gamemode.SURVIVAL;
                case CREATIVE -> Gamemode.CREATIVE;
                case ADVENTURE -> Gamemode.ADVENTURE;
                case SPECTATOR -> Gamemode.SPECTATOR;
            }, negated);
            data.entityTypes = new NegatableData<>(List.of(new DataLocation("minecraft", "player")), false);
        });
        SelectorParser.registerSelectorOption("x_rotation", (reader, data) -> {
            var bounds = reader.readFloatRange();
            data.pitch = new FloatRangeArgument(bounds.minimum() != null ? Mth.wrapDegrees(bounds.minimum()) : null, bounds.maximum() != null ? Mth.wrapDegrees(bounds.maximum()) : null);
        });
        SelectorParser.registerSelectorOption("y_rotation", (reader, data) -> {
            var bounds = reader.readFloatRange();
            data.yaw = new FloatRangeArgument(bounds.minimum() != null ? Mth.wrapDegrees(bounds.minimum()) : null, bounds.maximum() != null ? Mth.wrapDegrees(bounds.maximum()) : null);
        });
    }
}
