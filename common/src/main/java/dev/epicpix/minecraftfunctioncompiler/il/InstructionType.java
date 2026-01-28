package dev.epicpix.minecraftfunctioncompiler.il;

import org.jetbrains.annotations.Nullable;

public record InstructionType(String name, String[] argumentNames, int flags, int id) {

    private static int NEXT_ID;

    public InstructionType {
        if(id != NEXT_ID) throw new IllegalArgumentException("Don't provide the id argument manually");
    }

    public InstructionType(String name, String[] argumentNames, int flags) {
        this(name, argumentNames, flags, ++NEXT_ID);
    }

    public InstructionType(String name, String[] argumentNames) {
        this(name, argumentNames, 0);
    }

    public boolean equals(@Nullable Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        return id == ((InstructionType) o).id;
    }

    public int hashCode() {
        return id;
    }

    public static final int CONDITIONAL_JUMP = 1 << 0;
    public static final int PURE = 1 << 1;

    public String toString() {
        return name;
    }

    public int getArgumentIndex(String argumentName) {
        for(int i = 0; i < argumentNames.length; i++) {
            if(argumentName.equals(argumentNames[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid argument name: " + argumentName);
    }

    public boolean isConditionJump() {
        return (flags & CONDITIONAL_JUMP) != 0;
    }

    public boolean isPure() {
        return (flags & PURE) != 0;
    }

    public boolean returnsValue() {
        return argumentNames.length >= 1 && argumentNames[0].equals("result");
    }

}
