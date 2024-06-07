package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.Command;
import dev.epicpix.minecraftfunctioncompiler.commands.CommandParser;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import dev.epicpix.minecraftfunctioncompiler.v1_20_6.data.RegistryMapping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMap_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionLibrary_functions;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionManager_library;

public class FunctionClassLoader extends FunctionCompilerClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private final Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions;

    public FunctionClassLoader(ClassLoader parent, Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions, Object minecraftServer) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), CommandSourceStack.class, MinecraftServer.class, ServerFunctionManager.class, minecraftServer, LOGGER::info, LOGGER::warn, LOGGER::error);
        this.functions = functions;
    }

    public void loadFunctions(ServerFunctionManager serverFunctionManager) {
        if(functions.isEmpty()) return;
        try {
            RegistryMapping.updateRegistryMaps();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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

        HashMap<DataLocation, List<Command>> definedFunctions = new HashMap<>();

        var startParse = System.nanoTime();
        funcLoop: for(var entry : functions.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            try {
                if(value instanceof PlainTextFunction<?> plainFunction) {
                    ArrayList<Command> parsed = new ArrayList<>();
                    for (var cmd : plainFunction.entries()) {
                        if (!(cmd instanceof BuildContexts.Unbound<?> commandEntry)) {
                            continue funcLoop;
                        }
                        parsed.add(CommandParser.parseCommand(commandEntry.commandInput, lookup));
                    }
                    definedFunctions.put(new DataLocation(key.getNamespace(), key.getPath()), parsed);
                }
            } catch (UnsupportedOperationException e) {
                logWarn.accept("Unable to parse function " + key + " / " + e.getMessage());
            } catch (Throwable e) {
                logError.accept("Unable to parse function " + key);
                e.printStackTrace();
                FunctionCompilerReporter.reportException("parse_command", e, new JsonObject());
            }
        }
        var parseTime = (System.nanoTime()-startParse)/1000000000d;
        loadFunctionClasses(functions.size(), parseTime, definedFunctions, createCodeFieldStorage(), serverFunctionManager);
    }

    public boolean hasFunctionLoaded(ResourceLocation location) {
        return handles.containsKey(location);
    }

    public void setHandle(DataLocation key, MethodHandle handle) {
        handles.put(new ResourceLocation(key.namespace(), key.path()), handle);
    }

    public int runFunction(ResourceLocation location, CommandSourceStack stack) {
        try {
            return (Integer) handles.get(location).invoke(stack);
        } catch (Throwable e) {
            logError.accept("Failed to run function " + location);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private CodeFieldStorage createCodeFieldStorage() {
        return new CodeFieldStorage(
                ResourceLocation.class,
                CommandFunction.class,
                ServerFunctionManager.class,
                MinecraftServer.class,
                (cf, cw, value) -> {
                        cf.loadServerFunctionManager(cw);
                        loadServerFunctionManager_library(cw);
                        loadServerFunctionLibrary_functions(cw);
                        cf.loadResourceLocation(cw, value);
                        callMap_get(cw);
                        cw.castType(CommandFunction.class);
                }
        );
    }
}
