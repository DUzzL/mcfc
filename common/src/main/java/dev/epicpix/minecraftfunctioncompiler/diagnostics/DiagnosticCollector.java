package dev.epicpix.minecraftfunctioncompiler.diagnostics;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticCollector {

    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();

    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }
}
