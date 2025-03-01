package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;

import java.util.ArrayList;
import java.util.function.Function;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.EMITTER_NOT_REGISTERED_INSTRUCTION;

public final class EmitterInstructions {
    private EmitterInstructions() {}

    private static final ArrayList<Function<EmitterContext, Boolean>> instructionEmitters = new ArrayList<>();

    public static void registerEmitterInstruction(Function<EmitterContext, Boolean> exec) {
        instructionEmitters.add(exec);
    }

    public static void emitInstruction(EmitterContext context) {
        if(context.instruction().type() == InstructionTypes.LABEL) {
            context.codeWriter().label(context.mapLabel(context.instruction().<LabelData>get("label")));
            return;
        }
        for(var emitter : instructionEmitters) {
            if(emitter.apply(context)) {
                return;
            }
        }
        throw new DiagnosticException(Diagnostic.emitterError(EMITTER_NOT_REGISTERED_INSTRUCTION, context.location(), "Failed to write instruction for type: " + context.instruction().type()));
    }

}
