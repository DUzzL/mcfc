package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_EXPECTED_RESULT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_EXPECTED_SUCCESS;

public class ResultStorage {

    private final DataLocation location;
    public final LocationData<Integer> result;
    public final LocationData<Integer> success;

    public ResultStorage(DataLocation location, LocationData<Integer> result, LocationData<Integer> success) {
        this.location = location;
        this.result = result;
        this.success = success;
    }

    public static ResultStorage empty(DataLocation location) {
        return new ResultStorage(location, null, null);
    }

    private boolean updatedResult = false;
    private boolean updatedSuccess = false;

    public boolean storesResult() {
        return result != null;
    }

    public boolean storesSuccess() {
        return success != null;
    }

    public void setResult(InstructionWriter instructionWriter, CompilationContextData context, LocationData<Integer> data) {
        if(storesResult()) {
            updatedResult = true;
            context.writeInstruction(instructionWriter, Instructions.assign(result, data));
        }
    }

    public void addResult(InstructionWriter instructionWriter, CompilationContextData context, LocationData<Integer> data) {
        if(storesResult()) {
            updatedResult = true;
            context.writeInstruction(instructionWriter, Instructions.valueAdd(result, result, data));
        }
    }

    public void updateSuccess(InstructionWriter instructionWriter, CompilationContextData context, boolean failed) {
        if(storesSuccess()) {
            updatedSuccess = true;
            context.writeInstruction(instructionWriter, Instructions.assign(success, LocationData.ofInteger(failed ? 0 : 1)));
        }
    }

    public void checkRequirements(CommandParams command) {
        if(storesSuccess() && !updatedSuccess) throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_EXPECTED_SUCCESS, location, "ResultStorage requirement failed, requires success value but missing data: " + command));
        if(storesResult() && !updatedResult) throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_EXPECTED_RESULT, location, "ResultStorage requirement failed, requires result value but missing data: " + command));
    }

}
