package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

public class StringCommandReader {
    private final String command;
    private int index;

    public StringCommandReader(String command) {
        this.command = command;
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

    public void skip() {
        index++;
    }

    public char peek(int n) {
        return command.charAt(index + n);
    }

    public char peek() {
        return command.charAt(index);
    }

    public char read() {
        return command.charAt(index++);
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
            throw new StringCommandReaderException(this, StringCommandReaderExceptionType.LOCAL_VEC3_NOT_SUPPORTED);
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
        return new WorldCoordinates(parseDouble(x), relativeX, parseDouble(y), relativeY, parseDouble(z), relativeZ);
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

}
