package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import org.objectweb.asm.Type;

import java.util.Objects;

public final class MacroType<T> {

    private final Type type;

    private MacroType(Type type) {
        this.type = type;
    }

    public static final MacroType<String> STRING_MACRO_TYPE = new MacroType<>(Type.getType(String.class));
    public static final MacroType<Integer> INT_MACRO_TYPE = new MacroType<>(Type.getType(int.class));
    public static final MacroType<Float> FLOAT_MACRO_TYPE = new MacroType<>(Type.getType(float.class));
    public static final MacroType<Double> DOUBLE_MACRO_TYPE = new MacroType<>(Type.getType(double.class));

    public Type getType() {
        return type;
    }

    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        MacroType<?> macroType = (MacroType<?>) o;
        return Objects.equals(type, macroType.type);
    }

    public String toString() {
        return "MacroType{type='" + type + "'}";
    }
}
