package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;

import java.util.ArrayList;
import java.util.function.Function;

public final class EmitterInstructions {
    private EmitterInstructions() {}

    private static final ArrayList<Function<EmitterContext, Boolean>> instructionEmitters = new ArrayList<>();

    public static void registerEmitterInstruction(Function<EmitterContext, Boolean> exec) {
        instructionEmitters.add(exec);
    }

    public static void emitInstruction(EmitterContext context) {
        if(context.instruction().type() == InstructionTypes2.LABEL) {
            context.codeWriter().label(context.mapLabel(context.instruction().<LabelData>get("label")));
            return;
        }
        for(var emitter : instructionEmitters) {
            if(emitter.apply(context)) {
                return;
            }
        }
        throw new IllegalArgumentException("Failed to write instruction for type: " + context.instruction().type());
    }

}
