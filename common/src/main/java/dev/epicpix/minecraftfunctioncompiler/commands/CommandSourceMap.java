package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;

import java.util.IdentityHashMap;

/**
 * Per-line side table mapping every parsed {@link CommandParams} node (by identity) to
 * the offset in the source line where its command text starts. Used to hand vanilla
 * fallbacks the verbatim source text instead of re-serializing parsed arguments.
 *
 * <p>Every node is produced by some {@code parseCommand_<root>} and starts right after
 * its root keyword, so its full text is {@code root + " " + line.substring(start)}.
 * The command argument is always the last argument of a grammar rule, so a node's own
 * prefix (without its child command) is {@code line.substring(start, child.start)}.
 */
public final class CommandSourceMap {
    private record Span(String root, int start) {}

    private final String line;
    private final boolean macroLine;
    private final IdentityHashMap<CommandParams, Span> spans = new IdentityHashMap<>(4);

    public CommandSourceMap(String line, boolean macroLine) {
        this.line = line;
        this.macroLine = macroLine;
    }

    /** The source line (without the leading {@code $} of macro lines). */
    public String line() {
        return line;
    }

    /** Whether the line is a macro line ({@code $...}); only then are {@code $(x)} placeholders substituted. */
    public boolean isMacroLine() {
        return macroLine;
    }

    public <T extends CommandParams> T record(T node, String root, int start) {
        spans.put(node, new Span(root, start));
        return node;
    }

    /** Full verbatim text of {@code node} including its child command, or null if unknown. */
    public String text(CommandParams node) {
        var span = spans.get(node);
        if(span == null) return null;
        return join(span.root, line.substring(span.start));
    }

    /** Verbatim text of {@code node} up to (excluding) its child command {@code child}, or null if unknown. */
    public String prefix(CommandParams node, CommandParams child) {
        var span = spans.get(node);
        var childSpan = spans.get(child);
        if(span == null || childSpan == null || childSpan.start < span.start) return null;
        return join(span.root, line.substring(span.start, childSpan.start).stripTrailing());
    }

    private static String join(String root, String rest) {
        return rest.isEmpty() ? root : root + " " + rest;
    }
}
