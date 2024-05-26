package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import org.objectweb.asm.Label;

public record Local(
        String name,
        String descriptor,
        Label start,
        Label end,
        int index
) {}
