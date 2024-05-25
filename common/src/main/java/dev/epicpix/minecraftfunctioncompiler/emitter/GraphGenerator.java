package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.util.Pair;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;

public final class GraphGenerator {
    private GraphGenerator() {}

    private static void visitInstructionFunc(Instruction instruction, StringBuilder stringBuilder, DataLocation base, ArrayList<Pair<DataLocation, DataLocation>> calls) {
        if(!DEBUG) return;

        for(var it : instruction.children()) {
            visitInstructionFunc(it, stringBuilder, base, calls);
        }
        if(instruction.type() == InstructionTypes.CALL_FUNCTIONS) {
            var functions = instruction.<List<DataLocation>>get("functions");
            for(var function : functions) {
                if(!calls.contains(new Pair<>(base, function))) {
                    stringBuilder.append("  func_").append(base.namespace()).append("_").append(base.path().replace('.', '_').replace('-', '_').replace('/', '_')).append(" -> func_").append(function.namespace()).append("_").append(function.path().replace('.', '_').replace('-', '_').replace('/', '_')).append("\n");
                    calls.add(new Pair<>(base, function));
                }
            }
        }
    }

    private static void generateFunctionGraphFunc(List<Instruction> instructions, DataLocation funcId, StringBuilder builder, ArrayList<Pair<DataLocation, DataLocation>> calls) {
        if(!DEBUG) return;

        var base = "func_" + funcId.namespace() + "_" + funcId.path().replace('.', '_').replace('/', '_').replace('-', '_');
        builder.append(base).append("[label=\"").append(funcId).append("\"]\n");
        for(var it : instructions) {
            visitInstructionFunc(it, builder, funcId, calls);
        }
    }

    private static int visitInstruction(Instruction instruction, StringBuilder stringBuilder, StringBuilder post, int uid) {
        if(!DEBUG) return 0;

        var instrDescription = instruction.data().entrySet().stream().map(it -> it.getKey() + "=" + Objects.toString(it.getValue()).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).collect(Collectors.joining("\n"));
        stringBuilder.append("    x").append(uid).append("[label=\"").append(instruction.type()).append("\n").append(instrDescription).append("\",shape=box];\n");
        var curUid = uid + 1;
        for(var child : instruction.children()) {
            stringBuilder.append("    x").append(uid).append(" -> x").append(curUid).append(";\n");
            curUid = visitInstruction(child, stringBuilder, post, curUid);
        }
        if(instruction.type() == InstructionTypes.CALL_FUNCTIONS) {
            var functions = instruction.<List<DataLocation>>get("functions");
            for(var function : functions) {
                post.append("  x").append(uid).append(" -> x").append(function.namespace()).append("_").append(function.path().replace('.', '_').replace('-', '_').replace('/', '_')).append("\n");
            }
        }
        return curUid;
    }

    private static int generateFunctionGraph(List<Instruction> instructions, DataLocation funcId, StringBuilder builder, StringBuilder post, int uid) {
        if(!DEBUG) return 0;

        var safeName = funcId.namespace() + "_" + funcId.path().replace('.', '_').replace('/', '_').replace('-', '_');

        builder.append("  subgraph cluster_").append(safeName).append(" {\n");
        builder.append("    color=black;\n");
        builder.append("    label=\"").append(funcId).append("\";\n");
        builder.append("    x").append(safeName).append("[label=\"<root>\"]\n");
        var curUid = uid;
        for(var child : instructions) {
            builder.append("    x").append(safeName).append(" -> x").append(curUid).append(";\n");
            curUid = visitInstruction(child, builder, post, curUid);
        }
        builder.append("  }\n");

        return curUid;
    }

    private static void generateFullGraph(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG) return;

        var builder = new StringBuilder();
        var post = new StringBuilder();
        builder.append("digraph file {\n");
        var curUid = 0;
        for(var entry : functions.entrySet()) {
            curUid = generateFunctionGraph(entry.getValue(), entry.getKey(), builder, post, curUid);
        }
        builder.append(post);
        builder.append("}\n");

        var dotFile = resultDir.resolve("full_graph.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("full_graph.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    private static void generateCallGraph(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG) return;

        var calls = new ArrayList<Pair<DataLocation, DataLocation>>();
        var builder = new StringBuilder();
        builder.append("digraph file {\n");
        builder.append("  node[shape=box];\n");
        for(var entry : functions.entrySet()) {
            generateFunctionGraphFunc(entry.getValue(), entry.getKey(), builder, calls);
        }
        builder.append("}\n");

        var dotFile = resultDir.resolve("call_graph.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("call_graph.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    public static void generateGraphs(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG) return;
        generateFullGraph(functions, resultDir);
        generateCallGraph(functions, resultDir);
    }
}