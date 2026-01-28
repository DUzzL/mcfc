package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;

import java.lang.constant.ClassDesc;

public record CompilationParams(CodeFieldStorage codeFieldStorage, FunctionCodeGenerator.NbtTagBytecodeStringGenerator stringGenerator, ClassDesc commandSourceStackType) {}
