package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

public record BytecodeRegister(int reg) implements BytecodeValue {
    public int cost() {
        return 3;
    }
}
