package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED;

public final class ResultContext implements AutoCloseable {

    private final CompilationContext context;
    private final LocationData<Integer> success;
    private final LocationData<Integer> result;
    private final LabelData errorLabel;
    private final LabelData endErrorLabel;

    private boolean requiresErrorLabelJump;
    private boolean markedImplemented;

    private ResultContext(CompilationContext context, LocationData<Integer> success, LocationData<Integer> result, LabelData errorLabel, LabelData endErrorLabel) {
        this.context = context;
        this.success = success;
        this.result = result;
        this.errorLabel = errorLabel;
        this.endErrorLabel = endErrorLabel;
    }

    public static ResultContext create(CompilationContext context, boolean generateSuccess, boolean generateResult) {
        return create(context, context.current().contextEndLabel(), generateSuccess, generateResult);
    }

    public static ResultContext create(CompilationContext context, LabelData at, boolean generateSuccess, boolean generateResult) {
        var success = generateSuccess ? context.<Integer>createLocal() : null;
        var result = generateResult ? context.<Integer>createLocal() : null;
        if(success != null) context.instructionWriter.addInstructionBefore(Instructions.assign(success, LocationData.ofInteger(1)), at);
        if(result != null) context.instructionWriter.addInstructionBefore(Instructions.assign(result, LocationData.ofInteger(0)), at);
        var endErrorLabel = context.instructionWriter.createDeferredLabel();
        var startErrorLabel = context.instructionWriter.createDeferredLabel();
        return new ResultContext(context, success, result, startErrorLabel, endErrorLabel);
    }

    public void markImplemented() {
        markedImplemented = true;
    }

    public void checkImplemented(CommandParams command) {
        if(!markedImplemented) throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(), "ResultContext success/result not implemented: " + command));
    }

    public LabelData markAndGetErrorLabel() {
        requiresErrorLabelJump = true;
        return errorLabel;
    }

    public void updateResultValue(LocationData<Integer> value) {
        if(result != null) context.writeInstruction(Instructions.valueAdd(result, result, value));
    }

    public boolean generatingSuccess() {
        return success != null;
    }

    public boolean generatingResult() {
        return result != null;
    }

    public LocationData<Integer> getSuccess() {
        if(success == null) throw new RuntimeException("Tried to access \"success\" in result context, but keeping track of it is not enabled");
        return success;
    }

    public LocationData<Integer> getResult() {
        if(result == null) throw new RuntimeException("Tried to access \"result\" in result context, but keeping track of it is not enabled");
        return result;
    }

    public void endGeneration() {
        close();
    }

    public void close() {
        if(requiresErrorLabelJump) {
            if(result != null || success != null) {
                context.writeInstruction(Instructions.jump(endErrorLabel));
                context.writeInstruction(Instructions.label(errorLabel));
                if(result != null) context.writeInstruction(Instructions.assign(result, LocationData.ofInteger(0)));
                if(success != null) context.writeInstruction(Instructions.assign(success, LocationData.ofInteger(0)));
                context.writeInstruction(Instructions.label(endErrorLabel));
            }else {
                context.writeInstruction(Instructions.label(errorLabel));
                context.writeInstruction(Instructions.label(endErrorLabel));
            }
        }
    }
}
