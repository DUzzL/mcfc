package dev.epicpix.minecraftfunctioncompiler.v1_20_4;

import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.MappedUsageDefinitionsExtensions.callCompoundTag_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.MappedUsageDefinitionsExtensions.callTag_stringify;

public class FunctionClassLoader extends FunctionCompilerClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public FunctionClassLoader(ClassLoader parent, Object minecraftServer, SourceFileProvider sourceFileProvider) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), CommandSourceStack.class, minecraftServer, LOGGER::info, LOGGER::warn, LOGGER::error, sourceFileProvider.mcfc$getSourceFiles());
    }

    public void loadFunctions(ServerFunctionManager serverFunctionManager) {
        if(totalFunctionCount() == 0) return;
        if(compileInBackground(() -> {
            try {
                RegistryMapping.updateRegistryMaps();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            loadFunctions(serverFunctionManager);
        })) return;
        logInfo.accept("Starting compilation");
        resetHandles();
        var lookup = new IArgumentLookup() {
            public List<DataLocation> lookupFunctions(TaggableLocation functions) {
                if(functions.isTag()) {
                    return serverFunctionManager.getTag(new ResourceLocation(functions.location().toString())).stream().map(it -> new DataLocation(it.id().getNamespace(), it.id().getPath())).toList();
                }else if(serverFunctionManager.library.functions.containsKey(new ResourceLocation(functions.location().toString()))) {
                    return List.of(functions.location());
                }else {
                    return List.of();
                }
            }
        };

        loadFunctionClasses(lookup, createCodeFieldStorage(), serverFunctionManager);
    }

    public boolean hasFunctionLoaded(ResourceLocation location) {
        return handles.containsKey(location);
    }

    public void setHandle(DataLocation key, MethodHandle handle) {
        handles.put(new ResourceLocation(key.namespace(), key.path()), handle);
    }

    protected void nbtTagToString(CodeWriter writer, String key, Label errorLabel) {
        writer.loadLocal("static$FunctionCompound");
        writer.writeString(key);
        callCompoundTag_get(writer);
        if(errorLabel == null) {
            callTag_stringify(writer);
        }else {
            var tag = writer.createLocal(writer.generateUniqueLocal(), Tag.class.descriptorString());
            writer.storeLocal(tag.name());
            writer.loadLocal(tag.name());
            writer.methodVisitor().visitJumpInsn(Opcodes.IFNULL, errorLabel);
            writer.loadLocal(tag.name());
            callTag_stringify(writer);
            writer.destroyLocal(tag);
        }
    }

    public long runFunction(ResourceLocation location, CommandSourceStack stack, CompoundTag compound) {
        try {
            return (long) handles.get(location).invoke(stack, compound);
        } catch (Throwable e) {
            logError.accept("Failed to run function " + location, e);
            return 3L << 32;
        }
    }


    private CodeFieldStorage createCodeFieldStorage() {
        return new CodeFieldStorage(ServerFunctionManager.class, MinecraftServer.class, CodeFieldStorageMapImpl.INSTANCE);
    }
}
