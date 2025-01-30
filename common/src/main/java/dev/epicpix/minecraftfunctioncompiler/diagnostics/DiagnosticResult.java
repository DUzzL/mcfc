package dev.epicpix.minecraftfunctioncompiler.diagnostics;

public interface DiagnosticResult<T> {

    static <T> DiagnosticResult<T> result(T result) {
        return new Result<>(result);
    }

    static <T> DiagnosticResult<T> error(Diagnostic diagnostic) {
        return new Error<>(diagnostic);
    }

    record Result<T>(T result) implements DiagnosticResult<T> {}
    record Error<T>(Diagnostic diagnostic) implements DiagnosticResult<T> {}

    default boolean isError() {
        return this instanceof Error;
    }

    default T getResult() {
        if(this instanceof Result<T>) {
            return ((Result<T>) this).result;
        }
        return null;
    }

    default Diagnostic getError() {
        if(this instanceof Error<T>) {
            return ((Error<T>) this).diagnostic;
        }
        return null;
    }

}
