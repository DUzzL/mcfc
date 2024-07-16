package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.commands.Command;

public class ResultStorage {

    public final LocationData<Integer> result;
    public final LocationData<Integer> success;

    private ResultStorage(LocationData<Integer> result, LocationData<Integer> success) {
        this.result = result;
        this.success = success;
    }

    public static ResultStorage withResult(LocationData<Integer> result) {
        return new ResultStorage(result, null);
    }

    public static ResultStorage withSuccess(LocationData<Integer> success) {
        return new ResultStorage(null, success);
    }

    public static ResultStorage empty() {
        return new ResultStorage(null, null);
    }

    private boolean updatedResult = false;
    private boolean updatedSuccess = false;

    public boolean storesResult() {
        return result != null;
    }

    public boolean storesSuccess() {
        return success != null;
    }

    public void setResult(CompilationContextData context, LocationData<Integer> data) {
        if(storesResult()) {
            updatedResult = true;
            context.writeInstruction(Instructions2.setResultStorage(result, data));
        }
    }

    public void addResult(CompilationContextData context, LocationData<Integer> data) {
        if(storesResult()) {
            updatedResult = true;
            context.writeInstruction(Instructions2.addResultStorage(result, data));
        }
    }

    public void updateSuccess(CompilationContextData context, boolean failed) {
        if(storesSuccess()) {
            updatedSuccess = true;
            context.writeInstruction(Instructions2.setResultStorage(success, LocationData.ofInteger(failed ? 0 : 1)));
        }
    }

    public void checkRequirements(Command command) {
        if(storesSuccess() && !updatedSuccess) throw new UnsupportedOperationException("ResultStorage requirement failed, requires success value but missing data: " + command);
        if(storesResult() && !updatedResult) throw new UnsupportedOperationException("ResultStorage requirement failed, requires result value but missing data: " + command);
    }

}
