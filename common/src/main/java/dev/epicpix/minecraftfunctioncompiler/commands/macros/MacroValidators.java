package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import org.objectweb.asm.Opcodes;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.*;

public final class MacroValidators {
    private MacroValidators() {}

    public static final MacroValidator NO_SPACE = checkIfNotContains(" ");
    public static final MacroValidator NO_STAR = checkIfNotContains("*");
    public static final MacroValidator NOT_EMPTY = (source, cw, cf, failureLabel) -> {
        cw.loadLocal(source);
        callString_isEmpty(cw);
        cw.methodVisitor().visitJumpInsn(Opcodes.IFEQ, failureLabel);
    };
    public static final MacroValidator IS_WORD = (source, cw, cf, failureLabel) -> {
        cf.loadRegexPattern(cw, "^[0-9a-zA-Z_.+-]*$");
        cw.loadLocal(source);
        callPattern_matcher(cw);
        callMatcher_matches(cw);
        cw.methodVisitor().visitJumpInsn(Opcodes.IFEQ, failureLabel);
    };

    public static MacroValidator checkIfNotContains(String check) {
        return (source, cw, cf, failureLabel) -> {
            cw.loadLocal(source);
            cw.writeString(check);
            callString_contains(cw);
            cw.methodVisitor().visitJumpInsn(Opcodes.IFNE, failureLabel);
        };
    }
}
