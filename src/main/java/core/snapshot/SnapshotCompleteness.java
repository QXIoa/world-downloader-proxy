package core.snapshot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SnapshotCompleteness {
    private final List<SnapshotDiagnostic> diagnostics = new CopyOnWriteArrayList<>();

    public boolean isComplete() {
        return diagnostics.isEmpty();
    }

    public void markIncomplete(SnapshotDiagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public List<SnapshotDiagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    public void merge(SnapshotCompleteness other) {
        diagnostics.addAll(other.diagnostics);
    }
}
