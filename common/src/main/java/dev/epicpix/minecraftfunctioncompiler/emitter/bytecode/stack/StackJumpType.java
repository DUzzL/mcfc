package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

public enum StackJumpType {
    GOTO,
    IF_EQUAL,
    IF_NOT_EQUAL,
    IF_LESS_THAN,
    IF_LESS_EQUAL,
    IF_GREATER_THAN,
    IF_GREATER_EQUAL,
    IF_EQUAL_ZERO,
    IF_NOT_EQUAL_ZERO,
    IF_LESS_THAN_ZERO,
    IF_LESS_EQUAL_ZERO,
    IF_GREATER_THAN_ZERO,
    IF_GREATER_EQUAL_ZERO,
    IF_INSTANCEOF,
    IF_NOT_INSTANCEOF,
    IF_NULL,
    IF_NOT_NULL,
}
