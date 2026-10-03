package dev.epicpix.minecraftfunctioncompiler.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroType;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators;
import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.Difficulty;
import dev.epicpix.minecraftfunctioncompiler.data.DoubleRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode;
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;
import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ResultStoreLocationType;
import dev.epicpix.minecraftfunctioncompiler.data.ResultStoreType;
import dev.epicpix.minecraftfunctioncompiler.data.RotationCoordinates;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreAccessorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCompareOperation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardOperation;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorSort;
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_EXPECTED_MACRO;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_ATTRIBUTE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_BOOLEAN;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_DATA_LOCATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_DIFFICULTY;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_DOUBLE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_FLOAT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_GAMEMODE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_INTEGER;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_JSON;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_LOCATION_ANCHOR;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_MACRO_NAME;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_MOB_EFFECT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_NBT_DATA_LOCATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_RANGE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_RESULT_STORE_LOCATION_TYPE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_RESULT_STORE_TYPE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_ROTATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SCOREBOARD_COMPARE_OPERATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SCOREBOARD_OPERATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SCORE_ACCESSOR;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SELECTOR_SORT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_STRING_QUOTE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SWIZZLE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_TIME_KIND;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_VEC3;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_VEC3_LOCAL;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_MISSING_RANGE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_INVALID_SLOT_SOURCE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_UNSUPPORTED_MACRO_LOCATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.NBT_INVALID_SYNTAX;

public class StringCommandReader {
    private static final Gson GSON = new Gson();

    private final String command;
    private final DataLocation source;
    private final boolean allowMacros;
    private int index;
    private final CommandSourceMap sources;

    public StringCommandReader(String command, DataLocation source, boolean allowMacros) {
        this.command = command;
        this.source = source;
        this.allowMacros = allowMacros;
        this.sources = new CommandSourceMap(command, allowMacros);
    }

    /** Side table of verbatim source spans for every node parsed from this line. */
    public CommandSourceMap getSources() {
        return sources;
    }

    /** Records that {@code node} was parsed by {@code parseCommand_<root>} starting at {@code start}. */
    public <T extends dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams> T recordSource(T node, String root, int start) {
        return sources.record(node, root, start);
    }

    private DiagnosticException createError(String diagnosticId, String message) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, source, command, message));
    }

    private DiagnosticException createError(String diagnosticId, String message, Throwable cause) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, source, command, message, cause));
    }

    public boolean allowsMacros() {
        return allowMacros;
    }

    public String getString() {
        return (allowMacros ? "$" : "") + command;
    }

    public DataLocation getSource() {
        return source;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int newIndex) {
        index = newIndex;
    }

    public boolean canRead(int n) {
        return index + n <= command.length();
    }

    public boolean canRead() {
        return index + 1 <= command.length();
    }

    private boolean isMacro() {
        return allowMacros && canRead(2) && command.charAt(index) == '$' && command.charAt(index + 1) == '(';
    }

    public boolean checkMacro(boolean wantMacro) {
        if(!wantMacro && isMacro()) throw createError(COMMAND_UNSUPPORTED_MACRO_LOCATION, "Macros are not supported in this location");
        if(wantMacro && !isMacro()) throw createError(COMMAND_EXPECTED_MACRO, "Expected a macro in this location");
        return true;
    }

    public void skip() {
        checkMacro(false);
        index++;
    }

    public char peek(int n) {
        return command.charAt(index + n);
    }

    public char peek() {
        return command.charAt(index);
    }

    public char read() {
        checkMacro(false);
        return command.charAt(index++);
    }

    private static boolean isMacroCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public MacroTemplate.MacroKind readMacro(List<MacroValidator> validators) {
        checkMacro(true);
        index += 2;
        for(int i = index; i<command.length(); i++) {
            if(command.charAt(i) == ')') {
                if(index == i) break;
                String name = command.substring(index, i);
                index = i + 1;
                return new MacroTemplate.MacroKind(name, validators);
            }else if(!isMacroCharacter(command.charAt(i))) {
                break;
            }
        }
        throw createError(COMMAND_INVALID_MACRO_NAME, "Invalid macro name");
    }

    public MacroTemplate.MacroPart readMacroChar(List<MacroValidator> validators) {
        if(isMacro()) {
            return readMacro(validators);
        }
        return new MacroTemplate.LiteralKind<>(Character.toString(read()));
    }

    public boolean skipIfNext(char c) {
        if(canRead() && peek() == c) {
            skip();
            return true;
        }
        return false;
    }

    public boolean skipWhitespace() {
        boolean skipped = false;
        while(canRead() && Character.isWhitespace(peek())) {
            skip();
            skipped = true;
        }
        return skipped;
    }

    public boolean skipArgumentWhitespace() {
        if(canRead() && peek() == ' ') {
            skip();
            return true;
        }
        return !canRead();
    }

    /** Raw text of the line between two reader indices. */
    public String substring(int start, int end) {
        return command.substring(start, end);
    }

    /** Consumes {@code literal} if it is next and followed by a space or the end (brigadier literal
     * matching; works for non-word literals such as {@code *}, PAR-17). Never throws. */
    public boolean readLiteral(String literal) {
        if(!command.startsWith(literal, index)) return false;
        int end = index + literal.length();
        if(end < command.length() && command.charAt(end) != ' ') return false;
        index = end;
        return true;
    }

    /** Reads up to the next space. Macro placeholders are rejected (PAR-07): the text is used natively. */
    public String readUntilSpace() {
        return readUntilSpace(false);
    }

    /** Reads up to the next space; {@code allowMacroPlaceholders} keeps {@code $(x)} verbatim, for
     * arguments that only ever reach vanilla through the (macro-substituting) source-text fallback. */
    public String readUntilSpace(boolean allowMacroPlaceholders) {
        int start = index;
        while(canRead() && peek() != ' ') {
            if(allowMacroPlaceholders && allowMacros && canRead(2) && peek(0) == '$' && peek(1) == '(') {
                index += 2;
                while(canRead() && peek() != ')') index++;
                if(canRead()) index++;
            } else {
                skip();
            }
        }
        return command.substring(start, index);
    }

    // Читает block predicate: #namespace:tag, namespace:block[states]{nbt}
    // Обрабатывает вложенные [] и {} чтобы не обрезать block states
    public String readBlockPredicate() {
        return readBracketBalancedToken();
    }

    /**
     * Reads an item descriptor string: {@code namespace:item[component=value,...]{nbt}}.
     * Balances square and curly brackets so component values containing spaces
     * (e.g. quoted custom_name) don't terminate the read early. Stops at the
     * first space outside any bracket group. Returned string is fed to the
     * per-version ItemParser downstream.
     */
    public String readItemStackString() {
        return readBracketBalancedToken();
    }

    /** Reads up to the first space outside {@code []}/{@code {}}. Inside brackets, quoted SNBT
     * strings are skipped as a unit so a quoted bracket or space does not end the token. */
    private String readBracketBalancedToken() {
        int start = index;
        int depth = 0;
        while(canRead()) {
            char c = peek();
            if(c == '[' || c == '{') { depth++; skip(); }
            else if(c == ']' || c == '}') { depth--; skip(); }
            else if(depth > 0 && (c == '"' || c == '\'')) skipQuoted(false);
            else if(c == ' ' && depth == 0) break;
            else skip();
        }
        return command.substring(start, index);
    }

    /** Skips a quoted SNBT string starting at the current quote character; backslash escapes the next character.
     * {@code macroPlaceholders}: keep {@code $(name)} verbatim (the text only reaches the vanilla fallback, which substitutes it)
     * instead of rejecting it like {@link #read()} does. */
    private void skipQuoted(boolean macroPlaceholders) {
        char quote = read();
        while(canRead()) {
            char c = macroPlaceholders ? command.charAt(index++) : read();
            if(c == '\\') {
                if(canRead()) index++;
            }else if(c == quote) {
                return;
            }
        }
        throw createError(NBT_INVALID_SYNTAX, "Unterminated quoted string");
    }

    /**
     * Reads a 26.3 slot source argument ({@code SlotSourceArgument}) verbatim, for vanilla to re-parse.
     * Vanilla first tries a slot-range name read up to the first space ({@code SlotRanges.tryRead}),
     * then a {@code ResourceOrId} of {@code minecraft:slot_source}: an identifier, or an inline SNBT
     * value that may contain spaces ({@code {type:"minecraft:slot_range", slots:"hotbar.4"}}).
     * Only an SNBT literal can start with a bracket or quote, so those are read as one balanced value.
     */
    public String readSlotSource() {
        if(!canRead() || peek() == ' ') throw createError(COMMAND_INVALID_SLOT_SOURCE, "Expected a slot source");
        char first = peek();
        if(first != '{' && first != '[' && first != '"' && first != '\'') return readUntilSpace(true);
        int start = index;
        int depth = 0;
        do {
            if(!canRead()) throw createError(COMMAND_INVALID_SLOT_SOURCE, "Unterminated SNBT slot source");
            if(allowMacros && canRead(2) && peek(0) == '$' && peek(1) == '(') {
                index += 2;
                while(canRead() && peek() != ')') index++;
                if(canRead()) index++;
                continue;
            }
            char c = peek();
            if(c == '"' || c == '\'') skipQuoted(allowMacros);
            else {
                index++;
                if(c == '{' || c == '[') depth++;
                else if(c == '}' || c == ']') depth--;
            }
        } while(depth > 0);
        return command.substring(start, index);
    }

    public MacroTemplate<String> readUntilSpaceWithTemplate(List<MacroValidator> validators) {
        if(!allowMacros) return MacroTemplate.fromStringLiteral(readUntilSpace());
        ArrayList<MacroTemplate.MacroPart> parts = new ArrayList<>();
        ArrayList<MacroValidator> inputValidators = new ArrayList<>(validators);
        inputValidators.add(MacroValidators.NO_SPACE);
        while(canRead() && peek() != ' ') {
            parts.add(readMacroChar(inputValidators));
        }
        return new MacroTemplate<>(MacroTemplate.mergeParts(parts), MacroType.STRING_MACRO_TYPE);
    }

    private static boolean isWordCharacter(char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '_' || c == '-'
                || c == '.' || c == '+';
    }

    public MacroTemplate<String> readMacroWord(List<MacroValidator> validators) {
        if(!allowMacros) return MacroTemplate.fromStringLiteral(readWord());
        ArrayList<MacroTemplate.MacroPart> parts = new ArrayList<>();
        ArrayList<MacroValidator> inputValidators = new ArrayList<>(validators);
        inputValidators.add(MacroValidators.IS_WORD);
        while(canRead() && (isWordCharacter(peek()) || isMacro())) {
            parts.add(readMacroChar(inputValidators));
        }
        return new MacroTemplate<>(MacroTemplate.mergeParts(parts), MacroType.STRING_MACRO_TYPE);
    }

    public String readWord() {
        int start = index;
        while (canRead() && checkMacro(false) && isWordCharacter(peek())) {
            skip();
        }
        return command.substring(start, index);
    }

    private static boolean isNbtWordCharacter(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    public String readNbtWord() {
        int start = index;
        while (canRead() && checkMacro(false) && isNbtWordCharacter(peek())) {
            skip();
        }
        return command.substring(start, index);
    }

    public String readString() {
        return readString(false);
    }

    private String readString(boolean ignoreInvalidEscapeSequences) {
        if(!canRead()) return "";
        char quoteChar = peek();
        if(quoteChar == '"' || quoteChar == '\'') {
            skip();
            StringBuilder builder = new StringBuilder();
            boolean escaping = false;
            while(canRead()) {
                char c = read();
                if(c == quoteChar && !escaping) {
                    return builder.toString();
                }else if(c == '\\' && !escaping) {
                    escaping = true;
                    continue;
                }else if(escaping) {
                    if(!ignoreInvalidEscapeSequences && c != quoteChar && c != '\\') {
                        throw createError(COMMAND_INVALID_STRING_QUOTE, "Expected string quote (" + quoteChar + ") in escape sequence, but got '" + c + "'");
                    }
                }
                builder.append(c);
                escaping = false;
            }
            throw createError(COMMAND_INVALID_STRING_QUOTE, "Unclosed quoted string");
        }else {
            return readWord();
        }
    }

    /** Reads the rest of the line. Macro placeholders are rejected (PAR-07): the text is used natively. */
    public String readRemainder() {
        return readRemainder(false);
    }

    /** Reads the rest of the line; see {@link #readUntilSpace(boolean)} for {@code allowMacroPlaceholders}. */
    public String readRemainder(boolean allowMacroPlaceholders) {
        int start = index;
        while(canRead()) {
            if(allowMacroPlaceholders && allowMacros && canRead(2) && peek(0) == '$' && peek(1) == '(') {
                index += 2;
                while(canRead() && peek() != ')') index++;
                if(canRead()) index++;
            } else {
                skip();
            }
        }
        return command.substring(start, index);
    }

    private String readNumberBase() {
        int start = index;
        while(canRead()) {
            if(canRead(2) && peek(0) == '.' && peek(1) == '.') {
                break;
            }
            checkMacro(false);
            char peeked = peek();
            if((peeked >= '0' && peeked <= '9') || peeked == '-' || peeked == '.') {
                skip();
            }else {
                break;
            }
        }
        return command.substring(start, index);
    }

    public boolean readBoolean() {
        String str = readString();
        if(str.equals("true")) {
            return true;
        }else if(str.equals("false")) {
            return false;
        }else {
            throw createError(COMMAND_INVALID_BOOLEAN, "Invalid boolean");
        }
    }

    public MacroTemplate<Integer> readMacroInt(List<MacroValidator> validators) {
        if(isMacro()) {
            var reqValidators = new ArrayList<>(validators);
            reqValidators.addFirst(MacroValidators.IS_INTEGER);
            var macro = readMacro(reqValidators);
            return new MacroTemplate<>(List.of(macro), MacroType.INT_MACRO_TYPE);
        }else {
            return MacroTemplate.fromInteger(readInt());
        }
    }

    public int readInt() {
        try {
            return Integer.parseInt(readNumberBase());
        }catch(NumberFormatException e) {
            throw createError(COMMAND_INVALID_INTEGER, "Invalid integer");
        }
    }

    public float readFloat() {
        try {
            return Float.parseFloat(readNumberBase());
        }catch(NumberFormatException e) {
            throw createError(COMMAND_INVALID_FLOAT, "Invalid float");
        }
    }

    private double parseDouble(String str) {
        try {
            return Double.parseDouble(str);
        }catch(NumberFormatException e) {
            throw createError(COMMAND_INVALID_DOUBLE, "Invalid double");
        }
    }

    public double readDouble() {
        return parseDouble(readNumberBase());
    }

    public DoubleRangeArgument readDoubleRange() {
        var base1 = readNumberBase();
        Double min = null;
        if(!base1.isEmpty()) {
            try {
                min = Double.parseDouble(base1);
            }catch(NumberFormatException e) {
                throw createError(COMMAND_INVALID_DOUBLE, "Invalid double");
            }
        }
        if(canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            var base2 = readNumberBase();
            Double max = null;
            if(!base2.isEmpty()) {
                try {
                    max = Double.parseDouble(base2);
                    if(min != null && min > max) {
                        throw createError(COMMAND_INVALID_RANGE, "Invalid range, the minimum is bigger than than maximum");
                    }
                }catch(NumberFormatException e) {
                    throw createError(COMMAND_INVALID_DOUBLE, "Invalid double");
                }
            }else {
                if(min == null) {
                    throw createError(COMMAND_MISSING_RANGE, "Missing range");
                }
            }
            return new DoubleRangeArgument(min, max);
        }else {
            if(min == null) throw createError(COMMAND_MISSING_RANGE, "Missing range");
            return new DoubleRangeArgument(min, min);
        }
    }

    /** MinMaxBounds.FloatDegrees syntax (x_rotation / y_rotation): unlike the other ranges, min > max is allowed. */
    public FloatRangeArgument readFloatRange() {
        var base1 = readNumberBase();
        Float min = null;
        if(!base1.isEmpty()) {
            try {
                min = Float.parseFloat(base1);
            }catch(NumberFormatException e) {
                throw createError(COMMAND_INVALID_FLOAT, "Invalid float");
            }
        }
        if(canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            var base2 = readNumberBase();
            Float max = null;
            if(!base2.isEmpty()) {
                try {
                    max = Float.parseFloat(base2);
                }catch(NumberFormatException e) {
                    throw createError(COMMAND_INVALID_FLOAT, "Invalid float");
                }
            }else {
                if(min == null) {
                    throw createError(COMMAND_MISSING_RANGE, "Missing range");
                }
            }
            return new FloatRangeArgument(min, max);
        }else {
            return new FloatRangeArgument(min, min);
        }
    }

    public IntRangeArgument readIntRange() {
        var base1 = readNumberBase();
        Integer min = null;
        if(!base1.isEmpty()) {
            try {
                min = Integer.parseInt(base1);
            }catch(NumberFormatException e) {
                throw createError(COMMAND_INVALID_INTEGER, "Invalid integer");
            }
        }
        if(canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            var base2 = readNumberBase();
            Integer max = null;
            if(!base2.isEmpty()) {
                try {
                    max = Integer.parseInt(base2);
                    if(min != null && min > max) {
                        throw createError(COMMAND_INVALID_RANGE, "Invalid range, the minimum is bigger than than maximum");
                    }
                }catch(NumberFormatException e) {
                    throw createError(COMMAND_INVALID_INTEGER, "Invalid integer");
                }
            }else {
                if(min == null) {
                    throw createError(COMMAND_MISSING_RANGE, "Missing range");
                }
            }
            return new IntRangeArgument(min, max);
        }else {
            if(min == null) throw createError(COMMAND_MISSING_RANGE, "Missing range");
            return new IntRangeArgument(min, min);
        }
    }

    // Particle name with an optional inline `{NBT}` tail — vanilla's particle
    // command parses this as one token (e.g. `dust{color:[1,0,0],scale:1}`).
    // We keep the entire form as a String so ExtensionFunctions.parseParticle
    // (which delegates to vanilla's ParticleArgument) can hand it back the
    // exact syntax it understands. Without this, laser-style particles with
    // NBT options fell back to vanilla command dispatch, which silently ate
    // them when invoked via the compiled raycast's executeVanillaCommand.
    public String readParticleOptions() {
        int start = index;
        // namespace:path scan — same char set as readDataLocation
        while(canRead()) {
            char c = peek();
            if ((c < '0' || c > '9') && (c < 'a' || c > 'z') && c != '_' && c != ':' && c != '/' && c != '.' && c != '-') {
                break;
            }
            skip();
        }
        // Optional NBT tail directly attached (no space).
        if(canRead() && peek() == '{') {
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            while(canRead()) {
                char c = read();
                if(escape) { escape = false; continue; }
                if(c == '\\' && inString) { escape = true; continue; }
                if(c == '"') { inString = !inString; continue; }
                if(inString) continue;
                if(c == '{') depth++;
                else if(c == '}') {
                    depth--;
                    if(depth == 0) break;
                }
            }
        }
        return command.substring(start, index);
    }

    public DataLocation readDataLocation() {
        int start = index;
        while(canRead()) {
            char c = peek();
            if ((c < '0' || c > '9') && (c < 'a' || c > 'z') && c != '_' && c != ':' && c != '/' && c != '.' && c != '-') {
                break;
            }
            skip();
        }
        String content = command.substring(start, index);
        if(content.indexOf(':') == -1) {
            return new DataLocation("minecraft", content);
        }
        String namespace = content.split(":", 2)[0];
        String path = content.split(":", 2)[1];
        if(namespace.contains("/")) throw createError(COMMAND_INVALID_DATA_LOCATION, "Invalid location namespace");
        if(path.contains(":")) throw createError(COMMAND_INVALID_DATA_LOCATION, "Invalid location path");
        return new DataLocation(namespace, path);
    }

    public SwizzleArgument readSwizzle() {
        boolean x = false;
        boolean y = false;
        boolean z = false;
        while(canRead() && peek() != ' ') {
            char c = read();
            if(c == 'x') {
                if(x) throw createError(COMMAND_INVALID_SWIZZLE, "Invalid swizzle, 'x' can only be written once");
                x = true;
            }else if(c == 'y') {
                if(y) throw createError(COMMAND_INVALID_SWIZZLE, "Invalid swizzle, 'y' can only be written once");
                y = true;
            }else if(c == 'z') {
                if(z) throw createError(COMMAND_INVALID_SWIZZLE, "Invalid swizzle, 'z' can only be written once");
                z = true;
            }else {
                throw createError(COMMAND_INVALID_SWIZZLE, "Invalid swizzle, expected combination of 'x', 'y' and 'z'");
            }
        }
        if(!x && !y && !z) throw createError(COMMAND_INVALID_SWIZZLE, "Invalid swizzle, expected combination of 'x', 'y' and 'z'");
        return new SwizzleArgument(x, y, z);
    }

    // Coordinate readers mirror vanilla WorldCoordinate / LocalCoordinates / RotationArgument.

    /** Vec3Argument.vec3(false): no centering (particle delta etc.). */
    public WorldCoordinates readVec3Uncentered() {
        return readCoordinates(false, false, COMMAND_INVALID_VEC3);
    }

    /** Vec3Argument.vec3(): absolute integer X/Z (written without '.') are centered by +0.5; Y never (PAR-01). */
    public WorldCoordinates readVec3() {
        return readCoordinates(true, false, COMMAND_INVALID_VEC3);
    }

    /** BlockPosArgument: absolute values must be integers, relative/local values may be decimals,
     * no centering (PAR-02). Consumers floor the resolved position (BlockPos.containing). */
    public WorldCoordinates readBlockPos() {
        return readCoordinates(false, true, COMMAND_INVALID_VEC3);
    }

    private WorldCoordinates readCoordinates(boolean centerXZ, boolean blockPos, String errorId) {
        if(canRead() && peek() == '^') {
            double x = readLocalCoordinate();
            if(!canRead() || peek() != ' ') throw createError(COMMAND_INVALID_VEC3_LOCAL, "Invalid vec3, expected space for next argument");
            skip();
            double y = readLocalCoordinate();
            if(!canRead() || peek() != ' ') throw createError(COMMAND_INVALID_VEC3_LOCAL, "Invalid vec3, expected space for next argument");
            skip();
            double z = readLocalCoordinate();
            return new WorldCoordinates(x, true, y, true, z, true, true);
        }
        boolean relativeX = canRead() && peek() == '~';
        double x = readWorldCoordinate(centerXZ, blockPos, errorId);
        if(!canRead() || peek() != ' ') throw createError(errorId, "Invalid vec3, expected space for next argument");
        skip();
        boolean relativeY = canRead() && peek() == '~';
        double y = readWorldCoordinate(false, blockPos, errorId);
        if(!canRead() || peek() != ' ') throw createError(errorId, "Invalid vec3, expected space for next argument");
        skip();
        boolean relativeZ = canRead() && peek() == '~';
        double z = readWorldCoordinate(centerXZ, blockPos, errorId);
        return new WorldCoordinates(x, relativeX, y, relativeY, z, relativeZ, false);
    }

    /** LocalCoordinates.readDouble: '^' then an optional double. */
    private double readLocalCoordinate() {
        if(!canRead() || peek() != '^') throw createError(COMMAND_INVALID_VEC3_LOCAL, "Invalid vec3, cannot mix world & local coordinates");
        skip();
        if(!canRead() || peek() == ' ') return 0;
        return parseDouble(readNumberBase());
    }

    /** WorldCoordinate.parseDouble / parseInt. */
    private double readWorldCoordinate(boolean center, boolean integer, String errorId) {
        if(canRead() && peek() == '^') throw createError(COMMAND_INVALID_VEC3_LOCAL, "Invalid vec3, cannot mix world & local coordinates");
        if(!canRead()) throw createError(errorId, "Expected a coordinate");
        if(peek() == '~') {
            skip();
            if(!canRead() || peek() == ' ') return 0;
            return parseDouble(readNumberBase());
        }
        if(integer) return readInt();
        String number = readNumberBase();
        double value = parseDouble(number);
        return center && number.indexOf('.') < 0 ? value + 0.5 : value;
    }

    /** RotationArgument: two WorldCoordinate.parseDouble(reader, false); never centered (PAR-03). */
    public RotationCoordinates readRotation() {
        boolean relativeYaw = canRead() && peek() == '~';
        double yaw = readWorldCoordinate(false, false, COMMAND_INVALID_ROTATION);
        if(!canRead() || peek() != ' ') throw createError(COMMAND_INVALID_ROTATION, "Invalid rotation, expected space for next argument");
        skip();
        boolean relativePitch = canRead() && peek() == '~';
        double pitch = readWorldCoordinate(false, false, COMMAND_INVALID_ROTATION);
        return new RotationCoordinates(yaw, relativeYaw, pitch, relativePitch);
    }

    public Gamemode readGamemode() {
        String gamemode = readWord();
        return switch (gamemode) {
            case "creative" -> Gamemode.CREATIVE;
            case "survival" -> Gamemode.SURVIVAL;
            case "adventure" -> Gamemode.ADVENTURE;
            case "spectator" -> Gamemode.SPECTATOR;
            default -> throw createError(COMMAND_INVALID_GAMEMODE, "Invalid gamemode: " + gamemode);
        };
    }

    public TaggableLocation readTag() {
        boolean isTag = skipIfNext('#');
        DataLocation location = readDataLocation();
        return new TaggableLocation(location, isTag);
    }

    public ScoreboardCompareOperation readScoreboardCompareOperation() {
        String str = readUntilSpace();
        return switch (str) {
            case ">" -> ScoreboardCompareOperation.GT;
            case ">=" -> ScoreboardCompareOperation.GE;
            case "=" -> ScoreboardCompareOperation.EQ;
            case "<" -> ScoreboardCompareOperation.LT;
            case "<=" -> ScoreboardCompareOperation.LE;
            default -> throw createError(COMMAND_INVALID_SCOREBOARD_COMPARE_OPERATION, "Invalid scoreboard compare operation: " + str);
        };
    }

    public ScoreboardOperation readScoreboardOperation() {
        String str = readUntilSpace();
        return switch (str) {
            case "=" -> ScoreboardOperation.SET;
            case "+=" -> ScoreboardOperation.ADD;
            case "-=" -> ScoreboardOperation.SUB;
            case "*=" -> ScoreboardOperation.MUL;
            case "/=" -> ScoreboardOperation.DIV;
            case "%=" -> ScoreboardOperation.MOD;
            case "<" -> ScoreboardOperation.MIN;
            case ">" -> ScoreboardOperation.MAX;
            case "><" -> ScoreboardOperation.SWAP;
            default -> throw createError(COMMAND_INVALID_SCOREBOARD_OPERATION, "Invalid scoreboard operation: " + str);
        };
    }

    public AttributeData readAttribute() {
        DataLocation location = readDataLocation();
        var attribute = DataRegistries.ATTRIBUTE.getElement(location);
        if(attribute.isEmpty()) {
            throw createError(COMMAND_INVALID_ATTRIBUTE, "Unknown attribute: " + location);
        }
        return attribute.get();
    }

    public int readTime() {
        float length = readFloat();
        String unit = readUntilSpace();
        switch(unit) {
            case "", "t" -> {}
            case "s" -> length *= 20;
            case "d" -> length *= 24000;
            default -> throw createError(COMMAND_INVALID_TIME_KIND, "Invalid time unit: " + unit);
        }
        return Math.round(length);
    }

    public Difficulty readDifficulty() {
        String str = readUntilSpace();
        return switch (str) {
            case "peaceful" -> Difficulty.PEACEFUL;
            case "easy" -> Difficulty.EASY;
            case "normal" -> Difficulty.NORMAL;
            case "hard" -> Difficulty.HARD;
            default -> throw createError(COMMAND_INVALID_DIFFICULTY, "Invalid difficulty: " + str);
        };
    }

    public MobEffectData readMobEffect() {
        DataLocation location = readDataLocation();
        var effect = DataRegistries.MOB_EFFECT.getElement(location);
        if(effect.isEmpty()) {
            throw createError(COMMAND_INVALID_MOB_EFFECT, "Unknown effect: " + location);
        }
        return effect.get();
    }

    public ResultStoreType readResultStoreType() {
        String str = readWord();
        return switch (str) {
            case "byte" -> ResultStoreType.BYTE;
            case "double" -> ResultStoreType.DOUBLE;
            case "float" -> ResultStoreType.FLOAT;
            case "int" -> ResultStoreType.INT;
            case "long" -> ResultStoreType.LONG;
            case "short" -> ResultStoreType.SHORT;
            default -> throw createError(COMMAND_INVALID_RESULT_STORE_TYPE, "Invalid result store type: " + str);
        };
    }

    public ResultStoreLocationType readResultStoreLocationType() {
        String str = readWord();
        return switch (str) {
            case "result" -> ResultStoreLocationType.RESULT;
            case "success" -> ResultStoreLocationType.SUCCESS;
            default -> throw createError(COMMAND_INVALID_RESULT_STORE_LOCATION_TYPE, "Invalid result store location type: " + str);
        };
    }

    public SelectorSort readSelectorSort() {
        String str = readWord();
        return switch (str) {
            case "arbitrary" -> SelectorSort.ARBITRARY;
            case "furthest" -> SelectorSort.FURTHEST;
            case "nearest" -> SelectorSort.NEAREST;
            case "random" -> SelectorSort.RANDOM;
            default -> throw createError(COMMAND_INVALID_SELECTOR_SORT, "Invalid sort: " + str);
        };
    }

    public LocationAnchor readLocationAnchor() {
        String str = readWord();
        return switch (str) {
            case "eyes" -> LocationAnchor.EYES;
            case "feet" -> LocationAnchor.FEET;
            default -> throw createError(COMMAND_INVALID_LOCATION_ANCHOR, "Invalid location anchor: " + str);
        };
    }

    private int estimateJsonLength() {
        if(!canRead()) return 0;
        int start = index;
        char peeked = peek();
        if(peeked == '"') {
            readString(true);
        }else if(peeked == '[') {
            skip();
            readJsonUntil(']');
        }else if(peeked == '{') {
            skip();
            readJsonUntil('}');
        }else if((peeked == '-' || peeked == '+') || (peeked >= '0' && peeked <= '9')) {
            skipJsonNumber();
        }
        int end = index;
        index = start;
        return end - start;
    }

    private void readJsonUntil(char c) {
        while(canRead()) {
            char peeked = peek();
            if(peeked == '"') {
                readString(true);
            }else if(peeked == '[') {
                skip();
                readJsonUntil(']');
            }else if(peeked == '{') {
                skip();
                readJsonUntil('}');
            }else if((peeked == '-' || peeked == '+') || (peeked >= '0' && peeked <= '9')) {
                skipJsonNumber();
            }else if(peeked == c) {
                skip();
                return;
            }else {
                skip();
            }
        }
    }

    private void skipJsonNumber() {
        while(canRead()) {
            checkMacro(false);
            char peeked = peek();
            if((peeked >= '0' && peeked <= '9') || peeked == '-' || peeked == '+' || peeked == '.' || peeked == 'e' || peeked == 'E') {
                skip();
            }else {
                break;
            }
        }
    }

    public JsonElement readJson() {
        if(allowMacros) throw createError(COMMAND_UNSUPPORTED_MACRO_LOCATION, "Macro commands are not supported for json arguments");
        int length = estimateJsonLength();
        String jsonString = command.substring(index, index + length);
        index += length;
        try {
            return GSON.fromJson(jsonString, JsonElement.class);
        }catch(JsonSyntaxException e) {
            throw createError(COMMAND_INVALID_JSON, "Tried to parse JSON, but failed", e);
        }
    }

    public ScoreAccessorArgument readScoreAccessor(boolean allowMultiple) {
        ScoreHolderArgument scoreHolder = SelectorParser.parseScoreHolder(this, allowMultiple);
        if(!skipArgumentWhitespace()) throw createError(COMMAND_INVALID_SCORE_ACCESSOR, "Invalid score accessor, expected space for next argument");
        String objective = readWord();
        return new ScoreAccessorArgument(objective, scoreHolder);
    }

    public NbtDataLocation readNbtDataLocation() {
        var kind = readWord();
        if(!skipArgumentWhitespace()) throw createError(COMMAND_INVALID_NBT_DATA_LOCATION, "Invalid nbt data location, expected space for next argument");
        switch(kind) {
            case "block" -> {
                var coords = readBlockPos();
                return new NbtDataLocation.Block(coords);
            }
            case "entity" -> {
                var entity = SelectorParser.parseSelector(this, true, false);
                return new NbtDataLocation.Entity(entity);
            }
            case "storage" -> {
                var location = readDataLocation();
                return new NbtDataLocation.Storage(location);
            }
            default -> throw createError(COMMAND_INVALID_NBT_DATA_LOCATION, "Invalid nbt data location source, expected block/entity/storage, got " + kind);
        }
    }

}
