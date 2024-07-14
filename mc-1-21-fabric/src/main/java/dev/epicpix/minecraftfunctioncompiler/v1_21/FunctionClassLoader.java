package dev.epicpix.minecraftfunctioncompiler.v1_21;

import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.*;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
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
import java.util.*;

public class FunctionClassLoader extends FunctionCompilerClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private final Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions;

    public FunctionClassLoader(ClassLoader parent, Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions, Object minecraftServer) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), CommandSourceStack.class, MinecraftServer.class, ServerFunctionManager.class, minecraftServer, LOGGER::info, LOGGER::warn, LOGGER::error);
        this.functions = functions;
    }

    public void loadFunctions(ServerFunctionManager serverFunctionManager) {
        if(functions.isEmpty()) return;
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
                    return serverFunctionManager.getTag(ResourceLocation.parse(functions.location().toString())).stream().map(it -> new DataLocation(it.id().getNamespace(), it.id().getPath())).toList();
                }else if(serverFunctionManager.library.functions.containsKey(ResourceLocation.parse(functions.location().toString()))) {
                    return List.of(functions.location());
                }else {
                    return List.of();
                }
            }
        };

        HashMap<DataLocation, List<Command>> definedFunctions = new HashMap<>();
        ArrayList<CommandError> commandErrors = new ArrayList<>();

        var startParse = System.nanoTime();
        funcLoop: for(var entry : functions.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            String currentCommand = null;
            try {
                if(value instanceof PlainTextFunction<?> plainFunction) {
                    ArrayList<Command> parsed = new ArrayList<>();
                    for (var cmd : plainFunction.entries()) {
                        if (!(cmd instanceof BuildContexts.Unbound<?> commandEntry)) {
                            continue funcLoop;
                        }
                        currentCommand = commandEntry.commandInput;
                        parsed.add(CommandParser.parseCommand(currentCommand, lookup));
                    }
                    definedFunctions.put(new DataLocation(key.getNamespace(), key.getPath()), parsed);
                }else {
                    logWarn.accept("Unable to parse function " + key + " / Functions with macros are not yet supported");
                }
            } catch (CommandErrorException e) {
                logWarn.accept("Unable to parse function " + key + " / " + e.getMessage());
                commandErrors.add(e.error);
            } catch (StringCommandReaderException e) {
                logWarn.accept("Unable to parse function " + key + " / " + e.getMessage() + " / " + currentCommand);
                FunctionCompilerReporter.reportExceptionWithCommand("parse_command", e, currentCommand);
            } catch (Throwable e) {
                logError.accept("Unable to parse function " + key, e);
                FunctionCompilerReporter.reportExceptionWithCommand("parse_command", e, currentCommand);
            }
        }
        var parseTime = (System.nanoTime()-startParse)/1000000000d;
        loadFunctionClasses(functions.size(), parseTime, definedFunctions, commandErrors, createCodeFieldStorage(), serverFunctionManager);
    }

    public boolean hasFunctionLoaded(ResourceLocation location) {
        return handles.containsKey(location);
    }

    public int totalFunctionCount() {
        return functions.size();
    }

    public void setHandle(DataLocation key, MethodHandle handle) {
        handles.put(ResourceLocation.fromNamespaceAndPath(key.namespace(), key.path()), handle);
    }

    public long runFunction(ResourceLocation location, CommandSourceStack stack) {
        try {
            return (long) handles.get(location).invoke(stack);
        } catch (Throwable e) {
            logError.accept("Failed to run function " + location, e);
            return 3L << 32;
        }
    }

    private CodeFieldStorage createCodeFieldStorage() {
        return new CodeFieldStorage(ServerFunctionManager.class, MinecraftServer.class, CodeFieldStorageMapImpl.INSTANCE);
    }
}
