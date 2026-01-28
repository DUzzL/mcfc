package dev.epicpix.minecraftfunctioncompiler.diagnostics;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import org.jspecify.annotations.Nullable;

public record Diagnostic(DiagnosticKind kind, String diagnosticId, String message, @Nullable String extra, @Nullable DataLocation source, @Nullable DiagnosticLocation location, @Nullable Throwable cause) {
    public DiagnosticSeverity severity() {
        return kind.getSeverity();
    }

    public static Diagnostic parseError(String diagnosticId, DataLocation source, String command, String errorMessage) {
        return parseError(diagnosticId, source, command, errorMessage, null);
    }

    public static Diagnostic parseError(String diagnosticId, DataLocation source, String command, String errorMessage, @Nullable Throwable cause) {
        return new Diagnostic(DiagnosticKind.PARSE_ERROR, diagnosticId, errorMessage, command, source, null, cause);
    }

    public static Diagnostic compileError(String diagnosticId, DataLocation source, String errorMessage) {
        return compileError(diagnosticId, source, errorMessage, null);
    }

    public static Diagnostic compileError(String diagnosticId, DataLocation source, String errorMessage, @Nullable Throwable cause) {
        return new Diagnostic(DiagnosticKind.COMPILE_ERROR, diagnosticId, errorMessage, null, source, null, cause);
    }

    public static Diagnostic emitterError(String diagnosticId, DataLocation source, String errorMessage) {
        return new Diagnostic(DiagnosticKind.EMITTER_ERROR, diagnosticId, errorMessage, null, source, null, null);
    }

}
