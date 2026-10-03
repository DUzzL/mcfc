package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callExtensionFunctions_isValidCommandDouble;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callExtensionFunctions_isValidCommandFloat;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callExtensionFunctions_isValidInteger;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callInteger_parseInt;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMatcher_matches;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callPattern_matcher;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callString_contains;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callString_isEmpty;

public final class MacroValidators {
    private MacroValidators() {}

    public static final MacroValidator NO_SPACE = checkIfNotContains(" ");
    public static final MacroValidator NO_STAR = checkIfNotContains("*");
    public static final MacroValidator NOT_EMPTY = (source, cw, cf, failureLabel) -> cw.conditionalJump(JumpCondition.EQ, callString_isEmpty(cw, source), new BytecodeValue.IntegerValue(0), failureLabel);
    public static final MacroValidator IS_WORD = (source, cw, cf, failureLabel) -> {
        var matches = callMatcher_matches(cw, callPattern_matcher(cw, cf.loadRegexPattern(cw, "^[0-9a-zA-Z_.+-]*$"), source));
        cw.conditionalJump(JumpCondition.EQ, matches, new BytecodeValue.IntegerValue(0), failureLabel);
    };
    public static final MacroValidator IS_INTEGER = (source, cw, cf, failureLabel) -> {
        var matches = callExtensionFunctions_isValidInteger(cw, source);
        cw.conditionalJump(JumpCondition.EQ, matches, new BytecodeValue.IntegerValue(0), failureLabel);
    };
    public static final MacroValidator IS_COMMAND_FLOAT = (source, cw, cf, failureLabel) -> {
        var matches = callExtensionFunctions_isValidCommandFloat(cw, source);
        cw.conditionalJump(JumpCondition.EQ, matches, new BytecodeValue.IntegerValue(0), failureLabel);
    };
    public static final MacroValidator IS_COMMAND_DOUBLE = (source, cw, cf, failureLabel) -> {
        var matches = callExtensionFunctions_isValidCommandDouble(cw, source);
        cw.conditionalJump(JumpCondition.EQ, matches, new BytecodeValue.IntegerValue(0), failureLabel);
    };
    /** Macro score holder must resolve to a plain name: selectors ('@…') and UUIDs resolve to entities in vanilla (PAR-08). */
    public static final MacroValidator IS_PLAIN_SCORE_HOLDER_NAME = (source, cw, cf, failureLabel) -> {
        var matches = dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callExtensionFunctions_isPlainScoreHolderName(cw, source);
        cw.conditionalJump(JumpCondition.EQ, matches, new BytecodeValue.IntegerValue(0), failureLabel);
    };

    public static MacroValidator checkIfNotContains(String check) {
        return (source, cw, cf, failureLabel) -> cw.conditionalJump(JumpCondition.NE, callString_contains(cw, source, new BytecodeValue.StringValue(check)), new BytecodeValue.IntegerValue(0), failureLabel);
    }
    public static MacroValidator checkIfIntegerMinimum(int min) {
        return (source, cw, cf, failureLabel) -> cw.conditionalJump(JumpCondition.LT, callInteger_parseInt(cw, source), new BytecodeValue.IntegerValue(min), failureLabel);
    }
    public static MacroValidator checkIfIntegerMaximum(int min) {
        return (source, cw, cf, failureLabel) -> cw.conditionalJump(JumpCondition.GT, callInteger_parseInt(cw, source), new BytecodeValue.IntegerValue(min), failureLabel);
    }
}
