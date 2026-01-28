package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticCollector;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeFormatter;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ALLOW_EXTERNAL_DATA_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.INCLUDE_SIMPLE_COMPILER;

public class SimpleFunctionCompiler {

    public static void loadCommonData(MinecraftVersion version, JsonObject registryData) {
        if(!INCLUDE_SIMPLE_COMPILER) throw new UnsupportedOperationException("This build doesn't include simple compiler code");
        if(!ALLOW_EXTERNAL_DATA_ACCESS) throw new UnsupportedOperationException("This build doesn't include simple compiler loader code (Cannot load registries from external sources)");
        MinecraftVersion.setCurrentVersion(version);
        EmitterBaseOptions.registerInstructionEmitters();
        SelectorParser.initBaseSelectorOptions();
        DataRegistries.insertRegistries(registryData);
    }

    public static JsonObject compile(Path generatedPath, HashMap<DataLocation, List<String>> rawSources, CompilationParams params) {
        if(!INCLUDE_SIMPLE_COMPILER) throw new UnsupportedOperationException("This build doesn't include simple compiler code");
        try {
            JsonObject results = new JsonObject();
            results.addProperty("success", true);
            results.add("errors", new JsonObject());
            results.add("function_sources", new JsonObject());
            results.add("function_commands", new JsonObject());
            results.add("function_high_level_instructions_json", new JsonObject());
            results.add("function_high_level_instructions_txt", new JsonObject());
            results.add("function_low_level_instructions_json", new JsonObject());
            results.add("function_low_level_instructions_txt", new JsonObject());
            results.add("class_files", new JsonArray());

            var sources = FunctionCodeGenerator.loadFunctionSources(rawSources);
            for(var source : sources.entrySet()) {
                var lines = jsonData(results, "function_sources", source.getKey(), JsonArray::new);
                for(var line : source.getValue()) lines.add(line);
            }
            var diagnostics = new DiagnosticCollector();
            var parsed = FunctionCodeGenerator.parseFunctions(sources, functions -> List.of(), diagnostics);
            for(var parseRes : parsed.entrySet()) {
                var commands = jsonData(results, "function_commands", parseRes.getKey(), JsonArray::new);
                for(var command : parseRes.getValue()) {
                    var tree = (JsonObject) new Gson().toJsonTree(command);
                    tree.addProperty("command_type", command.getClass().getSimpleName());
                    commands.add(tree);
                }
            }
            var highLevel = FunctionCodeGenerator.compileFunctionsHighLevel(parsed, diagnostics, null, false);
            var optimizedHighLevel = FunctionCodeGenerator.optimizeFunctionsHighLevel(highLevel, diagnostics, null, true);
            for(var compRes : optimizedHighLevel.entrySet()) {
                var instructionsJson = jsonData(results, "function_high_level_instructions_json", compRes.getKey(), JsonArray::new);
                instructionsJson.addAll(Instruction.generateInstructionsJson(compRes.getValue()));
                var instructionsText = jsonData(results, "function_high_level_instructions_txt", compRes.getKey(), JsonArray::new);
                Instruction.printInstructions(compRes.getValue(), instructionsText::add);
            }
            var llFunctions = new HashMap<String, List<BytecodeInstruction>>();
            var classBytes = new ArrayList<byte[]>();
            for(var key : optimizedHighLevel.keySet()) {
                classBytes.add(FunctionCodeGenerator.generateFunctionClass(
                    params,
                    key,
                    optimizedHighLevel.get(key),
                    optimizedHighLevel.keySet(),
                    llFunctions
                ));
            }
            classBytes.add(params.codeFieldStorage().generate(llFunctions));
            for(var f : llFunctions.entrySet()) {
                var instructionsJson = jsonData(results, "function_low_level_instructions_json", f.getKey(), JsonArray::new);
                var instructionsText = jsonData(results, "function_low_level_instructions_txt", f.getKey(), JsonArray::new);
                for(var instr : f.getValue()) {
                    instructionsJson.add(BytecodeFormatter.instructionToJson(instr));
                    instructionsText.add(BytecodeFormatter.formatInstruction(instr));
                }
            }
            for(var diagnostic : diagnostics.getDiagnostics()) {
                JsonObject eObj = new JsonObject();
                eObj.addProperty("id", diagnostic.diagnosticId());
                eObj.addProperty("severity", diagnostic.severity().toString().toLowerCase(Locale.ROOT));
                eObj.addProperty("kind", diagnostic.kind().toString().toLowerCase(Locale.ROOT));
                eObj.addProperty("message", diagnostic.message());

                if(diagnostic.extra() != null) eObj.addProperty("extra", diagnostic.extra());
                if(diagnostic.cause() != null) eObj.add("cause", exceptionJson(diagnostic.cause()));
                jsonData(results, "errors", diagnostic.location(), () -> eObj);
            }
            Files.createDirectories(generatedPath);
            for(var _ : classBytes) {
                throw new UnsupportedOperationException();
            }
            return results;
        }catch(Exception e) {
            JsonObject results = new JsonObject();
            results.addProperty("success", false);
            results.add("caused_by", exceptionJson(e));
            LoggerFactory.getLogger("minecraftfunctioncompiler").error("An exception occurred while trying to compile code", e);
            return results;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends JsonElement> T jsonData(JsonObject in, String dataName, Object loc, Supplier<T> defaultValueSupplier) {
        if(!INCLUDE_SIMPLE_COMPILER) throw new UnsupportedOperationException();
        var part = in.getAsJsonObject(dataName);
        var obj = in.get(loc.toString());
        if(obj == null) {
            obj = defaultValueSupplier.get();
            part.add(loc.toString(), obj);
        }
        return (T) obj;
    }

    private static JsonObject exceptionJson(Throwable exception) {
        if(!INCLUDE_SIMPLE_COMPILER) throw new UnsupportedOperationException();
        JsonObject json = new JsonObject();
        json.addProperty("exception_type", exception.getClass().getName());
        json.addProperty("exception_message", exception.getMessage());
        var trace = new StringBuilder();
        for(var element : exception.getStackTrace()) {
            trace.append(element).append("\n");
        }
        json.addProperty("exception_stack_trace", trace.toString());
        if(exception.getCause() != null) {
            var cause = exceptionJson(exception.getCause());
            json.add("caused_by", cause);
        }
        return json;
    }

}
