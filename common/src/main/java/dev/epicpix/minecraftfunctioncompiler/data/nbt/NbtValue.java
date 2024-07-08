package dev.epicpix.minecraftfunctioncompiler.data.nbt;

import java.util.List;
import java.util.Map;

public interface NbtValue {

    interface ArrayKind extends NbtValue {}
    interface TypedArray extends ArrayKind {}

    record Array(List<NbtValue> values) implements ArrayKind {}
    record ByteArray(byte[] values) implements TypedArray {}
    record ByteLiteral(byte value) implements NbtValue {}
    record Compound(Map<String, NbtValue> values) implements NbtValue {}
    record DoubleLiteral(double value) implements NbtValue {}
    record FloatLiteral(float value) implements NbtValue {}
    record IntegerArray(int[] values) implements TypedArray { }
    record IntegerLiteral(int value) implements NbtValue {}
    record LongArray(long[] values) implements TypedArray {}
    record LongLiteral(long value) implements NbtValue {}
    record ShortLiteral(short value) implements NbtValue { }
    record StringLiteral(String value) implements NbtValue {}

}
