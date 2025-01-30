package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import java.util.Objects;

public final class MacroType<T> {

    private final String type;

    private MacroType(String type) {
        this.type = type;
    }

    public static final MacroType<String> STRING_MACRO_TYPE = new MacroType<>("String");

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
