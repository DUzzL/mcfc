package dev.epicpix.minecraftfunctioncompiler.diagnostics;

@SuppressWarnings("serial")
public class DiagnosticException extends RuntimeException {
    private final Diagnostic diagnostic;

    public DiagnosticException(Diagnostic diagnostic) {
        super(diagnostic.message(), diagnostic.cause());
        this.diagnostic = diagnostic;
    }

    @Override
    public String getMessage() {
        return diagnostic.message();
    }

    public Diagnostic toDiagnostic() {
        return new Diagnostic(diagnostic.kind(), diagnostic.diagnosticId(), diagnostic.message(), diagnostic.extra(), diagnostic.source(), diagnostic.location(), this);
    }
}
