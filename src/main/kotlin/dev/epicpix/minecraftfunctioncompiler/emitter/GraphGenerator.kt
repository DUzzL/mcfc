package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import kotlin.io.path.absolutePathString

private fun visitInstructionFunc(instruction: Instruction, stringBuilder: StringBuilder, base: ResourceLocation, calls: MutableList<Pair<ResourceLocation, ResourceLocation>>) {
    if(!DEBUG) return

    instruction.children.forEach { visitInstructionFunc(it, stringBuilder, base, calls) }
    if(instruction.type == InstructionType.CALL_FUNCTIONS) {
        val functions = instruction.get<List<ResourceLocation>>("functions")
        for(function in functions) {
            if(!calls.contains(base to function)) {
                stringBuilder.append("  func_${base.namespace}_${base.path.replace('.', '_').replace('-', '_').replace('/', '_')} -> func_${function.namespace}_${function.path.replace('.', '_').replace('-', '_').replace('/', '_')}\n")
                calls.add(Pair(base, function))
            }
        }
    }
}

private fun generateFunctionGraphFunc(instructions: List<Instruction>, funcId: ResourceLocation, builder: StringBuilder, calls: MutableList<Pair<ResourceLocation, ResourceLocation>>) {
    if(!DEBUG) return

    val base = "func_${funcId.namespace}_${funcId.path.replace('.', '_').replace('/', '_').replace('-', '_')}"
    builder.append("$base[label=\"$funcId\"]\n")
    instructions.forEach { visitInstructionFunc(it, builder, funcId, calls) }
}

private fun visitInstruction(instruction: Instruction, stringBuilder: StringBuilder, post: StringBuilder, uid: Int): Int {
    if(!DEBUG) return 0

    val instrDescription = instruction.data.map { "${it.key}=${it.value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}" }.joinToString("\n")
    stringBuilder.append("    x$uid[label=\"${instruction.type}\n$instrDescription\",shape=box];\n")
    var curUid = uid + 1
    for(child in instruction.children) {
        stringBuilder.append("    x$uid -> x$curUid;\n")
        curUid = visitInstruction(child, stringBuilder, post, curUid)
    }
    if(instruction.type == InstructionType.CALL_FUNCTIONS) {
        val functions = instruction.get<List<ResourceLocation>>("functions")
        for(function in functions) {
            post.append("  x$uid -> cluster_${function.namespace}_${function.path.replace('.', '_').replace('-', '_').replace('/', '_')}\n")
        }
    }
    return curUid
}

private fun generateFunctionGraph(instructions: List<Instruction>, funcId: ResourceLocation, builder: StringBuilder, post: StringBuilder, uid: Int): Int {
    if(!DEBUG) return 0

    builder.append("  subgraph cluster_${funcId.namespace}_${funcId.path.replace('.', '_').replace('/', '_').replace('-', '_')} {\n")
    builder.append("    color=black;\n")
    builder.append("    label=\"$funcId\";\n")
    builder.append("    x${uid+1}[label=\"<root>\"]\n")
    var curUid = uid + 2
    for(child in instructions) {
        builder.append("    x${uid+1} -> x$curUid;\n")
        curUid = visitInstruction(child, builder, post, curUid)
    }
    builder.append("  }\n")

    return curUid
}

private fun generateFullGraph(functions: Map<ResourceLocation, List<Instruction>>) {
    if(!DEBUG) return

    val builder = StringBuilder()
    val post = StringBuilder()
    builder.append("digraph file {\n")
    var curUid = 0
    for((key, value) in functions) {
        curUid = generateFunctionGraph(value, key, builder, post, curUid)
    }
    builder.append(post)
    builder.append("}\n")

    val fileName = "full_graph"
    runCatching { Files.createDirectory(FabricLoader.getInstance().gameDir.resolve("generated")) }
    val dotFile = FabricLoader.getInstance().gameDir.resolve("generated").resolve("$fileName.dot")
    Files.write(dotFile, builder.toString().toByteArray())
    val format = "svg"
    val imgFile = FabricLoader.getInstance().gameDir.resolve("generated").resolve("$fileName.$format")
    ProcessBuilder("dot", "-T$format", dotFile.absolutePathString(), "-o", imgFile.absolutePathString()).start().waitFor()
    Files.delete(dotFile)
}

private fun generateCallGraph(functions: Map<ResourceLocation, List<Instruction>>) {
    if(!DEBUG) return

    val calls = mutableListOf<Pair<ResourceLocation, ResourceLocation>>()
    val builder = StringBuilder()
    builder.append("digraph file {\n")
    builder.append("  node[shape=box];\n")
    for((key, value) in functions) {
        generateFunctionGraphFunc(value, key, builder, calls)
    }
    builder.append("}\n")

    val fileName = "call_graph"
    runCatching { Files.createDirectory(FabricLoader.getInstance().gameDir.resolve("generated")) }
    val dotFile = FabricLoader.getInstance().gameDir.resolve("generated").resolve("$fileName.dot")
    Files.write(dotFile, builder.toString().toByteArray())
    val format = "svg"
    val imgFile = FabricLoader.getInstance().gameDir.resolve("generated").resolve("$fileName.$format")
    ProcessBuilder("dot", "-T$format", dotFile.absolutePathString(), "-o", imgFile.absolutePathString()).start().waitFor()
    Files.delete(dotFile)
}

fun generateGraphs(functions: Map<ResourceLocation, List<Instruction>>) {
    if(!DEBUG) return
    generateFullGraph(functions)
    generateCallGraph(functions)
}