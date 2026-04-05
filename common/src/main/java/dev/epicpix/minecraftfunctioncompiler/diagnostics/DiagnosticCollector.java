package dev.epicpix.minecraftfunctioncompiler.diagnostics;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticCollector {

    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();

    public synchronized void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    /** Thread-safe alias for parallel compilation phases. */
    public void addDiagnosticSafe(Diagnostic diagnostic) {
        addDiagnostic(diagnostic);
    }

    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }
}
