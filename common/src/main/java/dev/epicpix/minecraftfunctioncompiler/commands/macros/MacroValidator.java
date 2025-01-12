package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;

public interface MacroValidator {
    void validateGenerated(BytecodeValue source, CodeWriter cw, CodeFieldStorage cf, BytecodeLabel failureLabel);
}