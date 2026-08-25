package schematic.export;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builds schematic file names from a timestamp and the server address the proxy is connected to.
 * A pure function with no filesystem or NBT knowledge, so it is trivial to unit test with a fixed
 * {@link Instant}.
 */
public class SchematicFileNamer {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    public String buildFileName(Instant instant, String serverAddress) {
        return TIMESTAMP_FORMAT.format(instant) + "_" + sanitize(serverAddress) + ".schem";
    }

    private String sanitize(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            return "unknown-server";
        }
        return serverAddress.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
