package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_GENERATE_GRAPH;

public class BytecodeGraphGenerator {

    private static void generateFunctionGraph(List<BytecodeInstruction> instructions, String functionName, StringBuilder builder, StringBuilder post, AtomicInteger uniqId) {
        if(!DEBUG_GENERATE_GRAPH) return;

        var safeName = functionName.replace('.', '_').replace('/', '_').replace('-', '_').replace('#', '_');

        builder.append("  subgraph cluster_").append(safeName).append(" {\n");
        builder.append("    color=black;\n");
        builder.append("    label=\"").append(functionName).append("\";\n");
        HashMap<Integer, Integer> indexToId = new HashMap<>();
        {
            int index = 0;
            for(var instr : instructions) {
                var instrDescription = BytecodeFormatter.formatInstruction(instr).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                builder.append("    x").append(uniqId).append("[label=\"").append(instrDescription).append("\nCost: ").append(instr.cost()).append("\",shape=box];\n");
                indexToId.put(index++, uniqId.getAndIncrement());
            }
        }
        {
            int index = 0;
            for(var instr : instructions) {
                int selfId = indexToId.get(index);

                if(instr instanceof BytecodeInstruction.Jump jump) {
                    int jumpId = indexToId.get(instructions.indexOf(new BytecodeInstruction.Label(jump.label())));
                    builder.append("    x").append(selfId).append(" -> x").append(jumpId).append(" [color=red]").append("\n");
                    index++;
                    continue;
                }else if(instr instanceof BytecodeInstruction.ConditionalJump cJump) {
                    int jumpId = indexToId.get(instructions.indexOf(new BytecodeInstruction.Label(cJump.label())));
                    builder.append("    x").append(selfId).append(" -> x").append(jumpId).append(" [color=blue]").append("\n");
                }else if(instr instanceof BytecodeInstruction.InstanceCheckJump cJump) {
                    int jumpId = indexToId.get(instructions.indexOf(new BytecodeInstruction.Label(cJump.label())));
                    builder.append("    x").append(selfId).append(" -> x").append(jumpId).append(" [color=blue]").append("\n");
                }else if(instr instanceof BytecodeInstruction.ReturnVoid || instr instanceof BytecodeInstruction.Return) {
                    index++;
                    continue;
                }

                int nextId = indexToId.get(index + 1);
                builder.append("    x").append(selfId).append(" -> x").append(nextId).append("\n");

                index++;
            }
        }
        builder.append("  }\n");
    }

    private static void generateFullGraph(Map<String, List<BytecodeInstruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG_GENERATE_GRAPH) return;

        var builder = new StringBuilder();
        var post = new StringBuilder();
        builder.append("digraph file {\n");
        var curUid = new AtomicInteger(0);
        for(var entry : functions.entrySet()) {
            generateFunctionGraph(entry.getValue(), entry.getKey(), builder, post, curUid);
        }
        builder.append(post);
        builder.append("}\n");

        var dotFile = resultDir.resolve("full_graph_low_level.dot");
        Files.writeString(dotFile, builder.toString());
        new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", resultDir.resolve("full_graph_low_level.svg").toAbsolutePath().toString()).start().waitFor();
        Files.delete(dotFile);
    }

    public static void generateBytecodeGraphs(Map<String, List<BytecodeInstruction>> functions, Path resultDir) throws IOException, InterruptedException {
        if(!DEBUG_GENERATE_GRAPH) return;
        generateFullGraph(functions, resultDir);
    }

}
