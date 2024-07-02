package dev.epicpix.minecraftfunctioncompiler.emitter;

import java.util.ArrayList;
import java.util.function.Function;

public final class EmitterInstructions {
    private EmitterInstructions() {}

    private static final ArrayList<Function<EmitterContext, Boolean>> instructionEmitters = new ArrayList<>();

    public static void registerEmitterInstruction(Function<EmitterContext, Boolean> exec) {
        instructionEmitters.add(exec);
    }

    public static void emitInstruction(EmitterContext context) {
        for(var emitter : instructionEmitters) {
            if(emitter.apply(context)) {
                return;
            }
        }
    }

}
