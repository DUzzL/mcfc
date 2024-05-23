package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.compiler.impl.CompilationContext;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.parser.Command;

public class ResultStorage {

    public final LocationData result;
    public final LocationData success;

    private ResultStorage(LocationData result, LocationData success) {
        this.result = result;
        this.success = success;
    }

    public static ResultStorage withResult(LocationData result) {
        return new ResultStorage(result, null);
    }

    public static ResultStorage withSuccess(LocationData success) {
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

    public void setResult(CompilationContext context, LocationData data) {
        if(storesResult()) {
            updatedResult = true;
            context.getAddInstruction().invoke(Instructions2.INSTANCE.setResultStorage(result, data));
        }
    }

    public void addResult(CompilationContext context, LocationData data) {
        if(storesResult()) {
            updatedResult = true;
            context.getAddInstruction().invoke(Instructions2.INSTANCE.addResultStorage(result, data));
        }
    }

    public void updateSuccess(CompilationContext context, boolean failed) {
        if(storesSuccess()) {
            updatedSuccess = true;
            context.getAddInstruction().invoke(Instructions2.INSTANCE.setResultStorage(success, LocationData.ofInteger(failed ? 0 : 1)));
        }
    }

    public void checkRequirements(Command command) {
        if(storesSuccess() && !updatedSuccess) throw new UnsupportedOperationException("ResultStorage requirement failed, requires success value but missing data: " + command);
        if(storesResult() && !updatedResult) throw new UnsupportedOperationException("ResultStorage requirement failed, requires result value but missing data: " + command);
    }

}
