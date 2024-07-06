package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter.Emitter;
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter.Emitter2;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

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
                if(DataRegistries.ENTITY_CLASS.get(it) == null) throw new UnsupportedOperationException("Unsupported entity type: " + it);
            }
            data.entityTypes = new NegatableData<>(types, negated);
        });
    }
}
