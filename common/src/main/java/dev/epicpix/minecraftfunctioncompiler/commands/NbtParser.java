package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.*;

public final class NbtParser {
    private NbtParser() {}

    public static NbtPath readNbtPath(StringCommandReader reader) {
        ArrayList<NbtPathPart> parts = new ArrayList<>();
        boolean first = true;
        while(reader.canRead() && reader.peek() != ' ') {
            parts.add(readNbtPathNode(reader, first));
            first = false;
        }
        return new NbtPath(List.copyOf(parts));
    }

    private static DiagnosticException createError(StringCommandReader reader, String diagnosticId, String message) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, reader.getSource(), reader.getString(), message));
    }

    public static NbtValue.Compound readNbtCompound(StringCommandReader reader) {
        reader.skipWhitespace();
        if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF while reading nbt list");
        if(!reader.skipIfNext('{')) throw createError(reader, NBT_INVALID_SYNTAX, "Expected '{' while reading nbt compound, but got '" + reader.peek() + "'");
        HashMap<String, NbtValue> valueMap = new HashMap<>();
        while(reader.peek() != '}') {
            reader.skipWhitespace();
            var key = reader.readString();
            if(key.isEmpty()) throw createError(reader, NBT_EMPTY_KEY, "Expected a non-empty nbt compound key while reading nbt compound");
            reader.skipWhitespace();
            if(!reader.skipIfNext(':')) throw createError(reader, NBT_INVALID_SYNTAX, "Expected ':' while reading nbt compound, but got '" + reader.peek() + "'");
            var value = readNbtValue(reader);
            valueMap.put(key, value);
            reader.skipWhitespace();
            if(!reader.skipIfNext(',')) {
                if(reader.peek() != '}') {
                    throw createError(reader, NBT_INVALID_SYNTAX, "Expected ',' or '}' while reading nbt compound, but got '" + reader.peek() + "'");
                }
            }
            reader.skipWhitespace();
        }
        reader.skip();
        return new NbtValue.Compound(Map.copyOf(valueMap));
    }

    public static NbtValue.Array readNbtList(StringCommandReader reader) {
        reader.skipWhitespace();
        if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF while reading nbt list");
        if(!reader.skipIfNext('[')) throw createError(reader, NBT_INVALID_SYNTAX, "Expected '[' while reading nbt list, but got '" + reader.peek() + "'");
        ArrayList<NbtValue> valueList = new ArrayList<>();
        while(reader.peek() != ']') {
            var value = readNbtValue(reader);
            if(!valueList.isEmpty() && value.getClass() != valueList.get(0).getClass()) {
                throw createError(reader, NBT_MISMATCHED_TYPE, "Expected " + valueList.get(0).getClass().getSimpleName() + " nbt value, but got " + value.getClass().getSimpleName());
            }else {
                valueList.add(value);
            }
            reader.skipWhitespace();
            if(!reader.skipIfNext(',')) {
                if(reader.peek() != ']') {
                    throw createError(reader, NBT_INVALID_SYNTAX, "Expected ',' or ']' while reading nbt list, but got '" + reader.peek() + "'");
                }
            }
            reader.skipWhitespace();
        }
        reader.skip();
        return new NbtValue.Array(List.copyOf(valueList));
    }

    private static NbtValue.ArrayKind readNbtArrayKind(StringCommandReader reader) {
        reader.skipWhitespace();
        if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF while reading nbt array kind");
        if(reader.peek() != '[') throw createError(reader, NBT_INVALID_SYNTAX, "Expected '[' while reading nbt array kind, but got '" + reader.peek() + "'");
        if(!reader.canRead(3) || reader.peek(2) != ';') return readNbtList(reader);
        reader.skip();
        char kind = reader.read();
        reader.skip();
        reader.skipWhitespace();
        return switch(kind) {
            case 'B' -> {
                ArrayList<Byte> byteList = new ArrayList<>();
                while(reader.peek() != ']') {
                    var value = readNbtValue(reader);
                    if(value instanceof NbtValue.ByteLiteral literal) {
                        byteList.add(literal.value());
                    }else {
                        throw createError(reader, NBT_MISMATCHED_TYPE, "Expected byte nbt value, but got " + value.getClass().getSimpleName());
                    }
                    reader.skipWhitespace();
                    if(!reader.skipIfNext(',')) {
                        if(reader.peek() != ']') {
                            throw createError(reader, NBT_INVALID_SYNTAX, "Expected ',' or ']' while reading nbt byte array, but got '" + reader.peek() + "'");
                        }
                    }
                    reader.skipWhitespace();
                }
                reader.skip();
                byte[] bytes = new byte[byteList.size()];
                for(int i = 0; i<byteList.size(); i++) bytes[i] = byteList.get(i);
                yield new NbtValue.ByteArray(bytes);
            }
            case 'I' -> {
                IntList intList = new IntArrayList();
                while(reader.peek() != ']') {
                    var value = readNbtValue(reader);
                    if(value instanceof NbtValue.IntegerLiteral literal) {
                        intList.add(literal.value());
                    }else {
                        throw createError(reader, NBT_MISMATCHED_TYPE, "Expected int nbt value, but got " + value.getClass().getSimpleName());
                    }
                    reader.skipWhitespace();
                    if(!reader.skipIfNext(',')) {
                        if(reader.peek() != ']') {
                            throw createError(reader, NBT_INVALID_SYNTAX, "Expected ',' or ']' while reading nbt integer array, but got '" + reader.peek() + "'");
                        }
                    }
                    reader.skipWhitespace();
                }
                reader.skip();
                int[] ints = new int[intList.size()];
                for(int i = 0; i<intList.size(); i++) ints[i] = intList.getInt(i);
                yield new NbtValue.IntegerArray(ints);
            }
            case 'L' -> {
                LongList longList = new LongArrayList();
                while(reader.peek() != ']') {
                    var value = readNbtValue(reader);
                    if(value instanceof NbtValue.LongLiteral literal) {
                        longList.add(literal.value());
                    }else {
                        throw createError(reader, NBT_MISMATCHED_TYPE, "Expected long nbt value, but got " + value.getClass().getSimpleName());
                    }
                    reader.skipWhitespace();
                    if(!reader.skipIfNext(',')) {
                        if(reader.peek() != ']') {
                            throw createError(reader, NBT_INVALID_SYNTAX, "Expected ',' or ']' while reading nbt long array, but got '" + reader.peek() + "'");
                        }
                    }
                    reader.skipWhitespace();
                }
                reader.skip();
                long[] longs = new long[longList.size()];
                for(int i = 0; i<longList.size(); i++) longs[i] = longList.getLong(i);
                yield new NbtValue.LongArray(longs);
            }
            default -> throw createError(reader, NBT_INVALID_ARRAY_TYPE, "Expected 'B', 'I', or 'L' while reading nbt typed array, but got '" + reader.peek() + "'");
        };
    }

    private static boolean isIntegerLike(String str) {
        int index = 0;
        if(str.isEmpty()) return false;
        if(str.charAt(0) == '-' || str.charAt(0) == '+') index++;
        if(str.length() == index) return false;
        if(str.charAt(index) == '0') return str.length() == index + 1;
        while(index < str.length()) {
            char c = str.charAt(index);
            if(c < '0' || c > '9') {
                return false;
            }
            index++;
        }
        return true;
    }

    private static boolean isFloatLike(String str) {
        int index = 0;
        if(str.isEmpty()) return false;
        if(str.charAt(0) == '-' || str.charAt(0) == '+') index++;
        boolean wroteNumber = false;
        boolean foundDot = false;
        while(index < str.length()) {
            char c = str.charAt(index);
            if(c == '.') {
                if(foundDot) return false;
                foundDot = true;
            }else if(c < '0' || c > '9') {
                break;
            }else {
                wroteNumber = true;
            }
            index++;
        }
        if(!wroteNumber) return false;
        if(index < str.length() && (str.charAt(index) == 'e' || str.charAt(index) == 'E')) {
            index++;
            if(index >= str.length() || (str.charAt(index) != '-' && str.charAt(index) != '+')) return false;
            index++;
            while(index < str.length()) {
                char c = str.charAt(index);
                if(c < '0' || c > '9') {
                    return false;
                }
                index++;
            }
        }
        return true;
    }

    public static NbtValue readNbtValue(StringCommandReader reader) {
        reader.skipWhitespace();
        var peek = reader.peek();
        if(peek == '{') {
            return readNbtCompound(reader);
        }else if(peek == '[') {
            return readNbtArrayKind(reader);
        }else {
            if(peek == '\"' || peek == '\'') {
                return new NbtValue.StringLiteral(reader.readString());
            }
            String testCase = reader.readWord();
            if(testCase.isEmpty()) throw createError(reader, NBT_INVALID_SYNTAX, "Empty test case for nbt value");
            if(testCase.equalsIgnoreCase("true")) {
                return new NbtValue.ByteLiteral((byte) 1);
            }else if(testCase.equalsIgnoreCase("false")) {
                return new NbtValue.ByteLiteral((byte) 0);
            }
            char suffix = testCase.charAt(testCase.length() - 1);
            try {
                if(suffix == 'b' || suffix == 'B') {
                    String check = testCase.substring(0, testCase.length() - 1);
                    if(isIntegerLike(check)) {
                        return new NbtValue.ByteLiteral(Byte.parseByte(check));
                    }
                }else if(suffix == 's' || suffix == 'S') {
                    String check = testCase.substring(0, testCase.length() - 1);
                    if(isIntegerLike(check)) {
                        return new NbtValue.ShortLiteral(Short.parseShort(check));
                    }
                }else if(suffix == 'l' || suffix == 'L') {
                    String check = testCase.substring(0, testCase.length() - 1);
                    if(isIntegerLike(check)) {
                        return new NbtValue.LongLiteral(Long.parseLong(check));
                    }
                }else if(isIntegerLike(testCase)) {
                    return new NbtValue.IntegerLiteral(Integer.parseInt(testCase));
                }else if(suffix == 'f' || suffix == 'F') {
                    String check = testCase.substring(0, testCase.length() - 1);
                    if(isFloatLike(check)) {
                        return new NbtValue.FloatLiteral(Float.parseFloat(check));
                    }
                }else if(suffix == 'd' || suffix == 'D') {
                    String check = testCase.substring(0, testCase.length() - 1);
                    if(isFloatLike(check)) {
                        return new NbtValue.DoubleLiteral(Double.parseDouble(check));
                    }
                }else if(isFloatLike(testCase)) {
                    return new NbtValue.DoubleLiteral(Double.parseDouble(testCase));
                }
            }catch(NumberFormatException ignored) {}
            return new NbtValue.StringLiteral(testCase);
        }
    }

    private static NbtPathPart readNbtPathNode(StringCommandReader reader, boolean first) {
        if(first) {
            return switch(reader.peek()) {
                case '{' -> new NbtPathPart.CompoundCheck(readNbtCompound(reader));
                case '\"', '\'' -> new NbtPathPart.Named(reader.readString());
                default -> new NbtPathPart.Named(reader.readNbtWord());
            };
        }else {
            return switch(reader.peek()) {
                case '{' -> new NbtPathPart.CompoundCheck(readNbtCompound(reader));
                case '[' -> {
                    reader.skip();
                    if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF while reading nbt path");
                    if(reader.peek() == '{') {
                        var compound = readNbtCompound(reader);
                        if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF after compound array indexer while reading nbt path");
                        if(reader.read() != ']' && !reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Expected ']' after compound array indexer while reading nbt path");
                        yield new NbtPathPart.ArrayIndexCompound(compound);
                    }else {
                        int index = reader.readInt();
                        if(reader.read() != ']' && !reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Expected ']' after integer array indexer while reading nbt path");
                        yield new NbtPathPart.ArrayIndexInteger(index);
                    }
                }
                case '.' -> {
                    reader.skip();
                    if(!reader.canRead()) throw createError(reader, NBT_INVALID_SYNTAX, "Reached EOF while reading nbt path, expected named accessor after '.'");
                    yield switch(reader.peek()) {
                        case '\"', '\'' -> new NbtPathPart.Named(reader.readString());
                        default -> new NbtPathPart.Named(reader.readNbtWord());
                    };
                }
                default -> throw createError(reader, NBT_INVALID_SYNTAX, "Expected '{', '[' or '.' while reading nbt path, but got '" + reader.peek() + "'");
            };
        }
    }

}
