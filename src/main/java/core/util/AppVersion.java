package core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Application version, read from {@code /version.txt} on the classpath. The file is produced
 * by Maven resource filtering from {@code ${project.version}} in {@code pom.xml}, so the version
 * is controlled entirely by the {@code <version>} tag in the POM.
 */
public final class AppVersion {
    private static final String VERSION = readVersion();

    private AppVersion() {}

    private static String readVersion() {
        try (InputStream in = AppVersion.class.getResourceAsStream("/version.txt")) {
            if (in == null) {
                return "unknown";
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = r.readLine();
                return (line == null || line.isBlank()) ? "unknown" : line.trim();
            }
        } catch (IOException e) {
            return "unknown";
        }
    }

    public static String get() {
        return VERSION;
    }
}
