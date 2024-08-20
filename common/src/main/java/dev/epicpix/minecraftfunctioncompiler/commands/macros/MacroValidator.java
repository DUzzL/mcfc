package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import org.objectweb.asm.Label;

public interface MacroValidator {
    void validateGenerated(String source, CodeWriter cw, CodeFieldStorage cf, Label failureLabel);
}