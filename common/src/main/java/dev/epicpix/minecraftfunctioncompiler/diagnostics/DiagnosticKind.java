package dev.epicpix.minecraftfunctioncompiler.diagnostics;

public enum DiagnosticKind {
    DEBUG(DiagnosticSeverity.DEBUG),
    HINT(DiagnosticSeverity.HINT),
    PARSE_ERROR(DiagnosticSeverity.ERROR),
    COMPILE_ERROR(DiagnosticSeverity.ERROR),
    EMITTER_ERROR(DiagnosticSeverity.FATAL),
    LOAD_ERROR(DiagnosticSeverity.FATAL),
    EXECUTION_ERROR(DiagnosticSeverity.FATAL),
    ;

    private final DiagnosticSeverity severity;

    DiagnosticKind(DiagnosticSeverity severity) {
        this.severity = severity;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }
}
