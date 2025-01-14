package dev.epicpix.minecraftfunctioncompiler.v1_20_1;

import dev.epicpix.minecraftfunctioncompiler.loader.CompilationParams;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.BLOCKING_COMPILATION;

public class FunctionClassLoader extends FunctionCompilerClassLoader<ResourceLocation, CommandSourceStack, CompoundTag> {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public FunctionClassLoader(ClassLoader parent, Object minecraftServer, SourceFileProvider sourceFileProvider) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), minecraftServer, sourceFileProvider.mcfc$getSourceFiles());
    }

    public void loadFunctions(ServerFunctionManager serverFunctionManager) {
        if(totalFunctionCount() == 0) return;
        if(!BLOCKING_COMPILATION) {
            if(compileInBackground(() -> loadFunctions(serverFunctionManager))) return;
        }
        try {
            RegistryMapping.updateRegistryMaps(serverFunctionManager);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Starting compilation");
        resetHandles();
        var lookup = new IArgumentLookup() {
            public List<DataLocation> lookupFunctions(TaggableLocation functions) {
                if(functions.isTag()) {
                    var tags = DataRegistries.FUNCTION_TAGS.get(functions.location());
                    if(tags == null) return List.of();
                    return tags;
                }else if(serverFunctionManager.library.functions.containsKey(new ResourceLocation(functions.location().toString()))) {
                    return List.of(functions.location());
                }else {
                    return List.of();
                }
            }
        };

        loadFunctionClasses(lookup, createCompilationParams(), serverFunctionManager);
    }

    public void setHandle(DataLocation key, MethodHandle handle) {
        setHandle(new ResourceLocation(key.namespace(), key.path()), handle);
    }

    public static CompilationParams createCompilationParams() {
        return new CompilationParams(
            new CodeFieldStorage(Type.getType(ServerFunctionManager.class), Type.getType(MinecraftServer.class), CodeFieldStorageMapImpl.INSTANCE),
            (emitter, key, errorLabel) -> {
                throw new UnsupportedOperationException("Cannot convert nbt tag to string in this version");
            },
            Type.getType(CommandSourceStack.class)
        );
    }
}
