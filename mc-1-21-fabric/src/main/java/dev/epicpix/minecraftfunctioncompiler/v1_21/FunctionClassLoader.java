package dev.epicpix.minecraftfunctioncompiler.v1_21;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
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
import static dev.epicpix.minecraftfunctioncompiler.v1_21.emitter.MappedUsageDefinitionsExtensions.callCompoundTag_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_21.emitter.MappedUsageDefinitionsExtensions.callTag_stringify;

public class FunctionClassLoader extends FunctionCompilerClassLoader<ResourceLocation, CommandSourceStack, CompoundTag> {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public FunctionClassLoader(ClassLoader parent, Object minecraftServer, SourceFileProvider sourceFileProvider) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), CommandSourceStack.class, minecraftServer, sourceFileProvider.mcfc$getSourceFiles());
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
                }else if(serverFunctionManager.library.functions.containsKey(ResourceLocation.parse(functions.location().toString()))) {
                    return List.of(functions.location());
                }else {
                    return List.of();
                }
            }
        };

        loadFunctionClasses(lookup, serverFunctionManager);
    }

    public void setHandle(DataLocation key, MethodHandle handle) {
        setHandle(ResourceLocation.fromNamespaceAndPath(key.namespace(), key.path()), handle);
    }

    public BytecodeValue nbtTagToBytecodeString(EmitterContext emitter, String key, BytecodeLabel errorLabel) {
        var cw = emitter.codeWriter();
        var tag = callCompoundTag_get(cw, emitter.getStatic("FunctionCompound"), new BytecodeValue.StringValue(key));
        if(errorLabel != null) {
            cw.conditionalJump(JumpCondition.EQ, tag, new BytecodeValue.NullValue(), errorLabel);
        }
        return callTag_stringify(cw, tag);
    }

    public CodeFieldStorage createCodeFieldStorage() {
        return new CodeFieldStorage(Type.getType(ServerFunctionManager.class), Type.getType(MinecraftServer.class), CodeFieldStorageMapImpl.INSTANCE);
    }
}
