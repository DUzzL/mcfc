package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.emitter.OutlinedCall;
import org.jspecify.annotations.Nullable;

/**
 * Decides which execute-chain tails are compiled as separate methods (see {@link OutlinedCall}).
 *
 * <p>{@link #outline} is asked about every node of a chain, below the top-level command, where cutting
 * preserves semantics: the node is reached with one source at a time (no fork list is being collected)
 * and no result callback ({@code execute store}, {@code return run}) is active, and the tail starting at
 * the node contains no {@code return}. The tail then runs exactly like a top-level command whose source is
 * the chain's current source: skip-jumps to the end of the tail become "the outlined method finished".
 * Nodes are asked about in chain order.
 */
@FunctionalInterface
public interface ChainOutliner {
    /** The method that runs {@code tail}, or null to compile it inline. */
    @Nullable OutlinedCall outline(CommandParams tail);
}
