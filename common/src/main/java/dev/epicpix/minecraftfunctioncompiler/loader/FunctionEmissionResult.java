package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

import java.util.List;

/**
 * Result of emitting one compiled function as a collection of methods that
 * will be added to its assigned batch class. All methods for a single function
 * always live in the same batch class (chunk dispatch and the macro trampoline
 * assume same-class invokestatic).
 *
 * @param funcId    source DataLocation of this function
 * @param batchIndex which batch class this function was planned into
 * @param methods    every method to be added (the primary `run` entrypoint, any
 *                  {@code __chunkN} split methods, optionally an
 *                  {@code __instanced} variant for macro functions)
 */
public record FunctionEmissionResult(DataLocation funcId, int batchIndex, List<MethodEmission> methods) {}
