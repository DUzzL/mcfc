package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import org.objectweb.asm.Type;

public sealed interface BytecodeValue permits BytecodeRegister, BytecodeValue.DoubleValue, BytecodeValue.FloatValue, BytecodeValue.IntegerValue, BytecodeValue.LongValue, BytecodeValue.NullValue, BytecodeValue.Parameter, BytecodeValue.StringValue {
    int cost();

    record IntegerValue(int value) implements BytecodeValue {
        public int cost() {
            if(value >= -1 && value <= 5) return 1;
            if(value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return 2;
            return 3;
        }
    }

    record LongValue(long value) implements BytecodeValue {
        public int cost() {
            if(value == 0 || value == 1) return 1;
            return 3;
        }
    }

    record FloatValue(float value) implements BytecodeValue {
        public int cost() {
            if(value == 0 || value == 1 || value == 2) return 1;
            return 3;
        }
    }

    record DoubleValue(double value) implements BytecodeValue {
        public int cost() {
            if(value == 0 || value == 1) return 1;
            return 3;
        }
    }

    record StringValue(String value) implements BytecodeValue {
        public int cost() {
            return 3;
        }
    }

    record NullValue() implements BytecodeValue {
        public int cost() {
            return 1;
        }
    }

    record Parameter(int index, Type type) implements BytecodeValue {
        public int cost() {
            return 3;
        }
    }
}
