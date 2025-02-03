package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import it.unimi.dsi.fastutil.Pair;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_GENERATE_GRAPH;

public final class GraphGenerator {
    private GraphGenerator() {}

    private static void visitInstructionFunc(Instruction instruction, StringBuilder stringBuilder, DataLocation base, ArrayList<Pair<DataLocation, DataLocation>> calls) {
        if(!DEBUG_GENERATE_GRAPH) return;

        if(instruction.type() == InstructionTypes.CALL_FUNCTIONS) {
            var functions = instruction.<List<DataLocation>>get("functions");
            for(var function : functions) {
                if(!calls.contains(Pair.of(base, function))) {
                    stringBuilder.append("  func_").append(base.namespace()).append("_").append(base.path().replace('.', '_').replace('-', '_').replace('/', '_')).append(" -> func_").append(function.namespace()).append("_").append(function.path().replace('.', '_').replace('-', '_').replace('/', '_')).append("\n");
                    calls.add(Pair.of(base, function));
                }
            }
        }
    }

    private static void generateFunctionGraphFunc(List<Instruction> instructions, DataLocation funcId, StringBuilder builder, ArrayList<Pair<DataLocation, DataLocation>> calls) {
        if(!DEBUG_GENERATE_GRAPH) return;

        var base = "func_" + funcId.namespace() + "_" + funcId.path().replace('.', '_').replace('/', '_').replace('-', '_');
        builder.append(base).append("[label=\"").append(funcId).append("\"];\n");
        for(var it : instructions) {
            visitInstructionFunc(it, builder, funcId, calls);
        }
    }

    private static void visitInstruction(Instruction instruction, StringBuilder stringBuilder, StringBuilder post, HashMap<Integer, String> labels, int uid) {
        if(!DEBUG_GENERATE_GRAPH) return;

        var instrDescription = instruction.namedEntries().stream().map(it -> it.first() + "=" + Objects.toString(it.second()).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).collect(Collectors.joining("\n"));
        stringBuilder.append("    x").append(uid).append("[label=\"").append(instruction.type()).append("\n").append(instrDescription).append("\",shape=box];\n");
        if(instruction.type() == InstructionTypes.CALL_FUNCTIONS) {
            var functions = instruction.<List<DataLocation>>get("functions");
            for(var function : functions) {
                post.append("  x").append(uid).append(" -> x").append(function.namespace()).append("_").append(function.path().replace('.', '_').replace('-', '_').replace('/', '_')).append("\n");
            }
        }

        if(instruction.type() == InstructionTypes2.RETURN_FAIL || instruction.type() == InstructionTypes2.RETURN_SUCCESS) {
            return;
        }

        if(instruction.type() == InstructionTypes2.JUMP) {
            var label = instruction.<LabelData>get("label");
            stringBuilder.append("    x").append(uid).append(" -> ").append(labels.get(label.id())).append(" [color=red];\n");
        }else {
            stringBuilder.append("    x").append(uid).append(" -> x").append(uid + 1).append(";\n");
            if(instruction.type() != InstructionTypes2.LABEL) {
                for(var value : instruction.data()) {
                    if(value instanceof LabelData label) {
                        stringBuilder.append("    x").append(uid).append(" -> ").append(labels.get(label.id())).append(" [color=blue];\n");
                    }
                }
            }
        }
    }

    private static int getFlowLabel(int index, List<Instruction> instructions) {
        if(!DEBUG_GENERATE_GRAPH) return -1;

        for(int i = index - 1; i >= 0; i--) {
            var instr = instructions.get(i);
            if(instr.type() == InstructionTypes2.LABEL) {
                return instr.<LabelData>get("label").id();
            }
        }
        return 0;
    }

    private static void visitFlowInstruction(int index, List<Instruction> instructions, HashMap<Integer, HashSet<Integer>> edges) {
        if(!DEBUG_GENERATE_GRAPH) return;

        var instruction = instructions.get(index);

        for(var value : instruction.data()) {
            if(value instanceof LabelData label) {
                edges.computeIfAbsent(getFlowLabel(index, instructions), k -> new HashSet<>()).add(label.id());
            }
        }
    }

    private static int generateFunctionGraph(List<Instruction> instructions, DataLocation funcId, StringBuilder builder, StringBuilder post, int uid) {
        if(!DEBUG_GENERATE_GRAPH) return 0;

        var safeName = funcId.namespace() + "_" + funcId.path().replace('.', '_').replace('/', '_').replace('-', '_');

        builder.append("  subgraph cluster_").append(safeName).append(" {\n");
        builder.append("    color=black;\n");
        builder.append("    label=\"").append(funcId).append("\";\n");
        builder.append("    x").append(safeName).append("[label=\"<root>\"];\n");
        builder.append("    x").append(safeName).append(" -> x").append(uid).append(";\n");
        HashMap<Integer, String> labels = new HashMap<>();
        int jIndex = 0;
        for(var child : instructions) {
            if(child.type() == InstructionTypes2.LABEL) {
                labels.put(child.<LabelData>get("label").id(), "x" + (uid + jIndex));
            }
            jIndex++;
        }
        var index = 0;
        for(var child : instructions) {
            visitInstruction(child, builder, post, labels, uid + index);
            index++;
        }
        builder.append("    x").append(uid + index).append("[label=\"<return>\"];\n");
        builder.append("  }\n");

        return uid + instructions.size() + 1;
    }

    private static int generateFunctionFlowGraph(List<Instruction> instructions, DataLocation funcId, StringBuilder builder, int uid) {
        if(!DEBUG_GENERATE_GRAPH) return 0;

        var safeName = funcId.namespace() + "_" + funcId.path().replace('.', '_').replace('/', '_').replace('-', '_');

        builder.append("  subgraph cluster_").append(safeName).append(" {\n");
        builder.append("    color=black;\n");
        builder.append("    label=\"").append(funcId).append("\";\n");
        builder.append("    x").append(safeName).append("[label=\"<root>\"];\n");
        HashMap<Integer, String> labels = new HashMap<>();
        int jIndex = 0;
        for(var child : instructions) {
            if(child.type() == InstructionTypes2.LABEL) {
                labels.put(child.<LabelData>get("label").id(), "x" + (uid + jIndex));
                builder.append("    x").append(uid + jIndex).append("[label=\"" + child.get("label") + "\"];\n");
            }
            jIndex++;
        }
        labels.put(0, "x" + safeName);
        HashMap<Integer, HashSet<Integer>> edges = new HashMap<>();
        for(int index = 0; index<instructions.size(); index++) {
            visitFlowInstruction(index, instructions, edges);
        }
        for(var edge : edges.entrySet()) {
            var edgeKey = labels.get(edge.getKey());
            for(var out : edge.getValue()) {
                builder.append("    ").append(edgeKey).append(" -> ").append(labels.get(out)).append(";\n");
            }
        }
        builder.append("  }\n");

        return uid + instructions.size() + 1;
    }

    private static void generateFullGraph(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG_GENERATE_GRAPH) return;

        var builder = new StringBuilder();
        var post = new StringBuilder();
        builder.append("digraph file {\n");
        var curUid = 0;
        for(var entry : functions.entrySet()) {
            curUid = generateFunctionGraph(entry.getValue(), entry.getKey(), builder, post, curUid);
        }
        builder.append(post);
        builder.append("}\n");

        var dotFile = resultDir.resolve("full_graph_high_level.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("full_graph_high_level.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    private static void generateFullFlowGraph(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG_GENERATE_GRAPH) return;

        var builder = new StringBuilder();
        builder.append("digraph file {\n");
        var curUid = 0;
        for(var entry : functions.entrySet()) {
            curUid = generateFunctionFlowGraph(entry.getValue(), entry.getKey(), builder, curUid);
        }
        builder.append("}\n");

        var dotFile = resultDir.resolve("full_flow_graph_high_level.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("full_flow_graph_high_level.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    private static void generateCallGraph(Map<DataLocation, List<Instruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG_GENERATE_GRAPH) return;

        var calls = new ArrayList<Pair<DataLocation, DataLocation>>();
        var builder = new StringBuilder();
        builder.append("digraph file {\n");
        builder.append("  node[shape=box];\n");
        for(var entry : functions.entrySet()) {
            generateFunctionGraphFunc(entry.getValue(), entry.getKey(), builder, calls);
        }
        builder.append("}\n");

        var dotFile = resultDir.resolve("call_graph_high_level.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("call_graph_high_level.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    public static void generateGraphs(Map<DataLocation, List<Instruction>> functions, Path resultDir) {
        if(!DEBUG_GENERATE_GRAPH) return;
        new Thread(() -> {
            try {
                long start = System.nanoTime();
                generateFullGraph(functions, resultDir);
                LoggerFactory.getLogger("minecraftfunctioncompiler").info("Generated graph (full_graph_high_level) in {}ms", (System.nanoTime() - start) / 1000000);
            } catch(IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        new Thread(() -> {
            try {
                long start = System.nanoTime();
                generateFullFlowGraph(functions, resultDir);
                LoggerFactory.getLogger("minecraftfunctioncompiler").info("Generated graph (full_flow_graph_high_level) in {}ms", (System.nanoTime() - start) / 1000000);
            } catch(IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        new Thread(() -> {
            try {
                long start = System.nanoTime();
                generateCallGraph(functions, resultDir);
                LoggerFactory.getLogger("minecraftfunctioncompiler").info("Generated graph (call_graph_high_level) in {}ms", (System.nanoTime() - start) / 1000000);
            } catch(IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}