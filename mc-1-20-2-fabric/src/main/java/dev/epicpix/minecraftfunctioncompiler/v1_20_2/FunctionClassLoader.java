package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.commands.*;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.*;

public class FunctionClassLoader extends FunctionCompilerClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private final Map<ResourceLocation, CommandFunction> functions;

    public FunctionClassLoader(ClassLoader parent, Map<ResourceLocation, CommandFunction> functions, Object minecraftServer) {
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
                    return serverFunctionManager.getTag(new ResourceLocation(functions.location().toString())).stream().map(it -> new DataLocation(it.getId().getNamespace(), it.getId().getPath())).toList();
                }else if(serverFunctionManager.library.functions.containsKey(new ResourceLocation(functions.location().toString()))) {
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
                ArrayList<Command> parsed = new ArrayList<>();
                for(var cmd : value.getEntries()) {
                    if(!(cmd instanceof CommandFunction.CommandEntry commandEntry)) {
                        logWarn.accept("Unable to parse function " + key + " / Functions with macros are not yet supported");
                        continue funcLoop;
                    }
                    currentCommand = commandEntry.parse.getReader().getString();
                    parsed.add(CommandParser.parseCommand(currentCommand, lookup));
                }
                definedFunctions.put(new DataLocation(key.getNamespace(), key.getPath()), parsed);
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
        handles.put(new ResourceLocation(key.namespace(), key.path()), handle);
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
