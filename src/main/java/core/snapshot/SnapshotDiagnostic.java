package core.snapshot;

public record SnapshotDiagnostic(
        String code,
        String protocolVersion,
        String source,
        Integer numericId,
        String resourceLocation,
        int bufferPosition,
        String detail
) { }
