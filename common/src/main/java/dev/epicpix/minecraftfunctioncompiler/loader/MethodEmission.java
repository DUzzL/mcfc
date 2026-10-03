package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.StackCompiler;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.StackInstruction;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

/**
 * One method ready to be written into a class: its final (mangled) name, its descriptor
 * and its already stack-compiled body. Stack compilation happens in the parallel
 * per-function pass, so batch assembly only has to encode instructions.
 *
 * @param chunkIndex index of the command chunk this method's body was compiled from,
 *                   or {@link #NO_CHUNK} for entry points, dispatchers and stubs
 */
public record MethodEmission(String name, MethodTypeDesc type, List<StackInstruction> code, boolean usesStringConcat, int chunkIndex) {

    public static final int NO_CHUNK = -1;

    public static MethodEmission of(String name, List<BytecodeInstruction> instructions, MethodTypeDesc type, int chunkIndex) {
        boolean concat = false;
        for(var instr : instructions) {
            if(instr instanceof BytecodeInstruction.StringConcat) {
                concat = true;
                break;
            }
        }
        return new MethodEmission(name, type, StackCompiler.compileInstructions(instructions), concat, chunkIndex);
    }
}
