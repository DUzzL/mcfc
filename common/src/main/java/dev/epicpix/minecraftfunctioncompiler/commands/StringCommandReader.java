package dev.epicpix.minecraftfunctioncompiler.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.ArrayList;
import java.util.List;

public class StringCommandReader {
    private static final Gson GSON = new Gson();

    private final String command;
    private final boolean allowMacros;
    private int index;

    public StringCommandReader(String command, boolean allowMacros) {
        this.command = command;
        this.allowMacros = allowMacros;
    }

    public String getString() {
        return command;
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

    public void checkMacro(boolean wantMacro) {
        if(!wantMacro && isMacro()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.UNSUPPORTED_MACRO_LOCATION);
        if(wantMacro && !isMacro()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.NOT_MACRO);
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
        throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_MACRO_NAME);
    }

    public MacroTemplate.MacroPart readMacroChar(List<MacroValidator> validators) {
        if(isMacro()) {
            return readMacro(validators);
        }
        return new MacroTemplate.LiteralKind(Character.toString(read()));
    }

    public boolean skipIfNext(char c) {
        if(peek() == c) {
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
        if(canRead() && Character.isWhitespace(peek())) {
            skip();
            return true;
        }
        return !canRead();
    }

    public String readUntilSpace() {
        int start = index;
        while(canRead() && peek() != ' ') {
            skip();
        }
        return command.substring(start, index);
    }

    public MacroTemplate<String> readUntilSpaceWithTemplate(List<MacroValidator> validators) {
        if(!allowMacros) return MacroTemplate.fromLiteral(readUntilSpace());
        ArrayList<MacroTemplate.MacroPart> parts = new ArrayList<>();
        ArrayList<MacroValidator> inputValidators = new ArrayList<>(validators);
        inputValidators.add(MacroValidators.NO_SPACE);
        while(canRead() && peek() != ' ') {
            parts.add(readMacroChar(inputValidators));
        }
        return new MacroTemplate<>(MacroTemplate.mergeParts(parts));
    }

    private static boolean isWordCharacter(char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '_' || c == '-'
                || c == '.' || c == '+';
    }

    public String readWord() {
        int start = index;
        while (canRead() && isWordCharacter(peek())) {
            skip();
        }
        return command.substring(start, index);
    }

    private static boolean isNbtWordCharacter(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    public String readNbtWord() {
        int start = index;
        while (canRead() && isNbtWordCharacter(peek())) {
            skip();
        }
        return command.substring(start, index);
    }

    public String readString() {
        if(!canRead()) return "";
        char quoteChar = peek();
        if(quoteChar == '"' || quoteChar == '\'') {
            skip();
            StringBuilder builder = new StringBuilder();
            boolean escaping = false;
            while(canRead()) {
                char c = read();
                if(c == quoteChar && !escaping) {
                    break;
                }else if(c == '\\' && !escaping) {
                    escaping = true;
                    continue;
                }else if(escaping) {
                    if(c != quoteChar && c != '\\') {
                        throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_STRING_QUOTE);
                    }
                }
                builder.append(c);
                escaping = false;
            }
            return builder.toString();
        }else {
            return readWord();
        }
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
        }else if(str.equalsIgnoreCase("false")) {
            return false;
        }else {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_BOOLEAN);
        }
    }

    public int readInt() {
        try {
            return Integer.parseInt(readNumberBase());
        }catch(NumberFormatException e) {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_INTEGER);
        }
    }

    public float readFloat() {
        try {
            return Float.parseFloat(readNumberBase());
        }catch(NumberFormatException e) {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_FLOAT);
        }
    }

    private double parseDouble(String str) {
        try {
            return Double.parseDouble(str);
        }catch(NumberFormatException e) {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DOUBLE);
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
                throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DOUBLE);
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
                        throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_RANGE);
                    }
                }catch(NumberFormatException e) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DOUBLE);
                }
            }else {
                if(min == null) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.NO_RANGE);
                }
            }
            return new DoubleRangeArgument(min, max);
        }else {
            return new DoubleRangeArgument(min, min);
        }
    }

    public FloatRangeArgument readFloatRange() {
        var base1 = readNumberBase();
        Float min = null;
        if(!base1.isEmpty()) {
            try {
                min = Float.parseFloat(base1);
            }catch(NumberFormatException e) {
                throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_FLOAT);
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
                    if(min != null && min > max) {
                        throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_RANGE);
                    }
                }catch(NumberFormatException e) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_FLOAT);
                }
            }else {
                if(min == null) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.NO_RANGE);
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
                throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_INTEGER);
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
                        throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_RANGE);
                    }
                }catch(NumberFormatException e) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_INTEGER);
                }
            }else {
                if(min == null) {
                    throw new StringCommandReaderException(this, StringCommandReaderExceptionType.NO_RANGE);
                }
            }
            return new IntRangeArgument(min, max);
        }else {
            return new IntRangeArgument(min, min);
        }
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
        if(namespace.contains("/")) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DATA_LOCATION);
        if(path.contains(":")) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DATA_LOCATION);
        return new DataLocation(namespace, path);
    }

    public SwizzleArgument readSwizzle() {
        boolean x = false;
        boolean y = false;
        boolean z = false;
        while(canRead() && peek() != ' ') {
            char c = read();
            if(c == 'x') {
                if(x) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SWIZZLE);
                x = true;
            }else if(c == 'y') {
                if(y) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SWIZZLE);
                y = true;
            }else if(c == 'z') {
                if(z) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SWIZZLE);
                z = true;
            }
        }
        return new SwizzleArgument(x, y, z);
    }

    public WorldCoordinates readVec3() {
        if(peek() == '^') {
            if(!skipIfNext('^')) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3_LOCAL);
            String x = readNumberBase();
            if(x.isEmpty()) x = "0";
            if(!skipArgumentWhitespace()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3_LOCAL);
            if(!skipIfNext('^')) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3_LOCAL);
            String y = readNumberBase();
            if(y.isEmpty()) y = "0";
            if(!skipArgumentWhitespace()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3_LOCAL);
            if(!skipIfNext('^')) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3_LOCAL);
            String z = readNumberBase();
            if(z.isEmpty()) z = "0";
            return new WorldCoordinates(parseDouble(x), true, parseDouble(y), true, parseDouble(z), true, true);
        }
        boolean relativeX = skipIfNext('~');
        String x = readNumberBase();
        if(!relativeX && !x.contains(".")) x += ".5";
        else if(relativeX && x.isEmpty()) x = "0";
        if(!skipArgumentWhitespace()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3);
        boolean relativeY = skipIfNext('~');
        String y = readNumberBase();
        if(!relativeY && !y.contains(".")) y += ".5";
        else if(relativeY && y.isEmpty()) y = "0";
        if(!skipArgumentWhitespace()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_VEC3);
        boolean relativeZ = skipIfNext('~');
        String z = readNumberBase();
        if(!relativeZ && !z.contains(".")) z += ".5";
        else if(relativeZ && z.isEmpty()) z = "0";
        return new WorldCoordinates(parseDouble(x), relativeX, parseDouble(y), relativeY, parseDouble(z), relativeZ, false);
    }

    public RotationCoordinates readRotation() {
        boolean relativeYaw = skipIfNext('~');
        String yaw = readNumberBase();
        if(!relativeYaw && !yaw.contains(".")) yaw += ".5";
        else if(relativeYaw && yaw.isEmpty()) yaw = "0";
        if(!skipArgumentWhitespace()) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_ROTATION);
        boolean relativePitch = skipIfNext('~');
        String pitch = readNumberBase();
        if(!relativePitch && !pitch.contains(".")) pitch += ".5";
        else if(relativePitch && pitch.isEmpty()) pitch = "0";
        return new RotationCoordinates(parseDouble(yaw), relativeYaw, parseDouble(pitch), relativePitch);
    }

    public Gamemode readGamemode() {
        String gamemode = readWord();
        return switch (gamemode) {
            case "creative" -> Gamemode.CREATIVE;
            case "survival" -> Gamemode.SURVIVAL;
            case "adventure" -> Gamemode.ADVENTURE;
            case "spectator" -> Gamemode.SPECTATOR;
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_GAMEMODE);
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
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SCOREBOARD_COMPARE_OPERATION);
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
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SCOREBOARD_OPERATION);
        };
    }

    public AttributeData readAttribute() {
        DataLocation location = readDataLocation();
        AttributeData attribute = DataRegistries.ATTRIBUTE.get(location);
        if(attribute == null) {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_ATTRIBUTE);
        }
        return attribute;
    }

    public int readTime() {
        float length = readFloat();
        String unit = readUntilSpace();
        switch(unit) {
            case "", "t" -> {}
            case "s" -> length *= 20;
            case "d" -> length *= 24000;
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_TIME_KIND);
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
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_DIFFICULTY);
        };
    }

    public MobEffectData readMobEffect() {
        DataLocation location = readDataLocation();
        MobEffectData attribute = DataRegistries.MOB_EFFECT.get(location);
        if(attribute == null) {
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_MOB_EFFECT);
        }
        return attribute;
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
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_RESULT_STORE_TYPE);
        };
    }

    public SelectorSort readSelectorSort() {
        String str = readWord();
        return switch (str) {
            case "arbitrary" -> SelectorSort.ARBITRARY;
            case "furthest" -> SelectorSort.FURTHEST;
            case "nearest" -> SelectorSort.NEAREST;
            case "random" -> SelectorSort.RANDOM;
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_SELECTOR_SORT);
        };
    }

    public LocationAnchor readLocationAnchor() {
        String str = readWord();
        return switch (str) {
            case "eyes" -> LocationAnchor.EYES;
            case "feet" -> LocationAnchor.FEET;
            default -> throw new StringCommandReaderException(this, StringCommandReaderExceptionType.INVALID_LOCATION_ANCHOR);
        };
    }

    private int estimateJsonLength() {
        if(!canRead()) return 0;
        int start = index;
        char peeked = peek();
        if(peeked == '"') {
            readString();
        }else if(peeked == '[') {
            skip();
            readJsonUntil(']');
        }else if(peeked == '{') {
            skip();
            readJsonUntil('}');
        }else if((peeked == '-' || peeked == '+') || (peeked >= '0' && peeked <= '9')) {
            readNumberBase();
        }
        int end = index;
        index = start;
        return end - start;
    }

    private void readJsonUntil(char c) {
        while(canRead()) {
            char peeked = peek();
            if(peeked == '"') {
                readString();
            }else if(peeked == '[') {
                skip();
                readJsonUntil(']');
            }else if(peeked == '{') {
                skip();
                readJsonUntil('}');
            }else if((peeked == '-' || peeked == '+') || (peeked >= '0' && peeked <= '9')) {
                readNumberBase();
            }else if(peeked == c) {
                skip();
                return;
            }else {
                skip();
            }
        }
    }

    public JsonElement readJson() {
        if(allowMacros) throw new StringCommandReaderException(this, StringCommandReaderExceptionType.UNSUPPORTED_MACRO_LOCATION);
        int length = estimateJsonLength();
        String jsonString = command.substring(index, index + length);
        index += length;
        try {
            return GSON.fromJson(jsonString, JsonElement.class);
        }catch(JsonSyntaxException e) {
            throw new CommandErrorException(new CommandError(command, "Tried to parse JSON, but failed", CommandError.Step.PARSE, e));
        }
    }

}
