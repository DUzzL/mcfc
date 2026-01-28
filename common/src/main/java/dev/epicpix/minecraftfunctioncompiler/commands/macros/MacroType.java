package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MacroType<T> {

    private final Class<T> type;

    private MacroType(Class<T> type) {
        this.type = type;
    }

    public static final MacroType<String> STRING_MACRO_TYPE = new MacroType<>(String.class);
    public static final MacroType<Integer> INT_MACRO_TYPE = new MacroType<>(int.class);
    public static final MacroType<Float> FLOAT_MACRO_TYPE = new MacroType<>(float.class);
    public static final MacroType<Double> DOUBLE_MACRO_TYPE = new MacroType<>(double.class);

    public Class<T> getType() {
        return type;
    }

    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        MacroType<?> macroType = (MacroType<?>) o;
        return Objects.equals(type, macroType.type);
    }

    public String toString() {
        return "MacroType{type='" + type + "'}";
    }
}
