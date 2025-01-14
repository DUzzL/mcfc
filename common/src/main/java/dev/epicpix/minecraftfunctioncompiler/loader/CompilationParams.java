package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import org.objectweb.asm.Type;

public record CompilationParams(CodeFieldStorage codeFieldStorage, FunctionCodeGenerator.NbtTagBytecodeStringGenerator stringGenerator, Type commandSourceStackType) {}
