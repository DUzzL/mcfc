package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;

import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Parsed commands of one function together with the per-line {@link CommandSourceMap}
 * (verbatim source text used by vanilla fallbacks). Behaves as a plain immutable list
 * for every consumer that only needs the commands; {@link #subList} keeps the sources.
 */
public final class ParsedCommandList extends AbstractList<CommandParams> implements RandomAccess {
    private final CommandParams[] commands;
    private final CommandSourceMap[] sources;
    private final int offset;
    private final int size;

    public ParsedCommandList(CommandParams[] commands, CommandSourceMap[] sources) {
        this(commands, sources, 0, commands.length);
        if(commands.length != sources.length) throw new IllegalArgumentException("commands/sources length mismatch");
    }

    private ParsedCommandList(CommandParams[] commands, CommandSourceMap[] sources, int offset, int size) {
        this.commands = commands;
        this.sources = sources;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public CommandParams get(int index) {
        return commands[offset + Objects.checkIndex(index, size)];
    }

    /** Source side table of the line at {@code index}. */
    public CommandSourceMap sourceOf(int index) {
        return sources[offset + Objects.checkIndex(index, size)];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public ParsedCommandList subList(int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size);
        return new ParsedCommandList(commands, sources, offset + fromIndex, toIndex - fromIndex);
    }
}
