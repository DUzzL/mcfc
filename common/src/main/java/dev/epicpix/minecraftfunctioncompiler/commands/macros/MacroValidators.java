package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import org.objectweb.asm.Opcodes;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callString_contains;

public final class MacroValidators {
    private MacroValidators() {}

    public static final MacroValidator NO_SPACE = checkIfNotContains(" ");
    public static final MacroValidator NO_STAR = checkIfNotContains("*");

    public static MacroValidator checkIfNotContains(String check) {
        return (source, cw, failureLabel) -> {
            cw.loadLocal(source);
            cw.writeString(check);
            callString_contains(cw);
            cw.methodVisitor().visitJumpInsn(Opcodes.IFNE, failureLabel);
        };
    }
}
