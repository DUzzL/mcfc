package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMap;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapValue;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMap_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.*;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionLibrary_functions;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl();

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            loadBuiltInRegistries_ATTRIBUTE(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_getHolder(cw);
            callOptional_get(cw);
            cw.castType(Holder.class);
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            loadBuiltInRegistries_MOB_EFFECT(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_getHolder(cw);
            callOptional_get(cw);
            cw.castType(Holder.class);
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
