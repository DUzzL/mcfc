package dev.epicpix.minecraftfunctioncompiler.v1_20_1;

import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParserKt;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.data.RegistryMapping;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.Emitter2;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.EmitterKt;
import net.fabricmc.api.ModInitializer;
import net.minecraft.advancements.critereon.WrappedMinMaxBounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.commands.StringReaderExtensionsKt.optRead;

public class FunctionCompilerMain implements ModInitializer {
    public void onInitialize() {
        EmitterBaseOptions.registerInstructionEmitters();
        Emitter2.registerInstructionEmitters();
        EmitterKt.registerInstructionEmitters();
        SelectorParserKt.initBaseSelectorOptions();
        SelectorParserKt.registerSelectorOption("type", (reader, data) -> {
            var negated = optRead(reader, '!');
            var tag = optRead(reader, '#');
            var type = ResourceLocation.read(reader);
            List<DataLocation> types;
            if(!tag) {
                types = List.of(ExtensionsKt.toDataLocation(type));
            }else {
                var tagKey = TagKey.create(Registries.ENTITY_TYPE, type);
                var typeData = BuiltInRegistries.ENTITY_TYPE.getTag(tagKey);
                types = typeData
                        .map(holders -> holders.stream()
                                .map(it -> ExtensionsKt.toDataLocation(it
                                        .unwrapKey()
                                        .get()
                                        .location()))
                                .toList()
                        ).orElseGet(List::of);
            }
            for(var it : types) {
                if(RegistryMapping.mapEntityTypeClass(it) == null) throw new UnsupportedOperationException("Unsupported entity type: " + it);
            }
            data.entityTypes = new NegatableData<>(types, negated);
        });
        SelectorParserKt.registerSelectorOption("gamemode", (reader, data) -> {
            var negated = optRead(reader, '!');
            data.gamemode = new NegatableData<>(ExtensionsKt.toGamemode(GameType.byName(reader.readUnquotedString(), GameType.DEFAULT_MODE)), negated);
            data.entityTypes = new NegatableData<>(List.of(new DataLocation("minecraft", "player")), false);
        });
        SelectorParserKt.registerSelectorOption("x_rotation", (reader, data) -> {
            var bounds = WrappedMinMaxBounds.fromReader(reader, true, Mth::wrapDegrees);
            data.pitch = new FloatRangeArgument(bounds.getMin(), bounds.getMax());
        });
        SelectorParserKt.registerSelectorOption("y_rotation", (reader, data) -> {
            var bounds = WrappedMinMaxBounds.fromReader(reader, true, Mth::wrapDegrees);
            data.yaw = new FloatRangeArgument(bounds.getMin(), bounds.getMax());
        });
    }
}
