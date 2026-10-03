package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

import java.util.List;

/**
 * Every method of one function, ready to be written into its batch class.
 *
 * @param chunks   command ranges {@code [from, to)} of the body chunks, in order;
 *                 {@link MethodEmission#chunkIndex()} indexes this list
 * @param stub     true if the function could not be compiled and {@code methods} is a
 *                 same-descriptor entry point that dispatches to vanilla
 */
public record FunctionMethods(DataLocation id, boolean hasMacros, boolean stub, List<MethodEmission> methods, List<CommandRange> chunks) {

    public record CommandRange(int from, int to) {
        public int size() {
            return to - from;
        }
    }
}
