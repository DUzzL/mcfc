package dev.epicpix.minecraftfunctioncompiler.v1_20_4;

import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMap;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapValue;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMap_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.MappedUsageDefinitionsExtensions.*;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionLibrary_functions;

public class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl();

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Attribute.class, (cf, cw, value) -> {
            loadBuiltInRegistries_ATTRIBUTE(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_get(cw);
            cw.castType(Attribute.class);
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(MobEffect.class, (cf, cw, value) -> {
            loadBuiltInRegistries_MOB_EFFECT(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_get(cw);
            cw.castType(MobEffect.class);
        }));
        set(CodeFieldStorageMapKind.FUNCTION, new CodeFieldStorageMapValue<>(CommandFunction.class, (cf, cw, value) -> {
            cf.loadServerFunctionManager(cw);
            loadServerFunctionManager_library(cw);
            loadServerFunctionLibrary_functions(cw);
            cf.loadResourceLocation(cw, value);
            callMap_get(cw);
            cw.castType(CommandFunction.class);
        }));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(ResourceLocation.class, (cf, cw, value) -> {
            cw.newObjectCreate(ResourceLocation.class);
            cw.writeString(value.namespace());
            cw.writeString(value.path());
            cw.newObjectInit(ResourceLocation.class, String.class, String.class);
        }));
    }
}
