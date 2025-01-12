package dev.epicpix.minecraftfunctioncompiler.il;

public record InstructionType(String name, String[] argumentNames, int flags) {
    public InstructionType(String name, String[] argumentNames) {
        this(name, argumentNames, 0);
    }

    public static final int CONDITIONAL_JUMP = 1 >> 0;

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

}
