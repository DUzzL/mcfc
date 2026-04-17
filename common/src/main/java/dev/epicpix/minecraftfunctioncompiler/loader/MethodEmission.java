package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

/**
 * One method that will be written into a batch class: its final method name
 * (already mangled), its optimized bytecode-instruction list, and its descriptor.
 *
 * Sits between the parallel codegen pass (which produces these per-function in
 * parallel without touching {@code ClassBuilder}) and the sequential batch-assembly
 * pass (which walks {@link FunctionEmissionResult#methods()} to call
 * {@code ClassGenerator.addFunction}).
 */
public record MethodEmission(String name, List<BytecodeInstruction> instructions, MethodTypeDesc type) {}
