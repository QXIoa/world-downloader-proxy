package core.util;

import core.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static core.util.ExceptionHandling.attemptQuiet;

/**
 * Handle working directory since there is no easy to way to set it in Java.
 */
public class PathUtils {
    private static String workingDirectory = "abc";

    public static Path toPath(String... parts) {
        // if the path is absolute, don't add the working dir
        Path onlyEnd = Paths.get("", parts);
        if (onlyEnd.isAbsolute()) {
            return onlyEnd;
        }

        return Paths.get(workingDirectory, parts).toAbsolutePath();
    }

    public static void setWorkingDirectory(String dir) {
        workingDirectory = dir;
    }

    public static void setWorkingDirectory(Path p) {
        workingDirectory = p.toAbsolutePath().toString();
    }

    /**
     * Fix the working directory so world/cache output lands next to the jar
     * (or user.dir) instead of the "abc" default. Tries jar location, then
     * Minecraft installation dir.
     */
    public static void fixCwd() throws java.net.URISyntaxException {
        String cwd = System.getProperty("user.dir");

        if (Files.isWritable(Paths.get(cwd))) {
            setWorkingDirectory(cwd);
            return;
        }

        // if we can't write to the working directory, try the jar file's location
        Path jarPath = Paths.get(PathUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        if (Files.isWritable(jarPath)) {
            setWorkingDirectory(jarPath);
            return;
        }

        // if we can't write there, try the Minecraft installation dir
        Path mcPath = Paths.get(Config.getDefaultMinecraftPath(), "world-downloader");
        attemptQuiet(() -> Files.createDirectories(mcPath));
        if (Files.isWritable(mcPath)) {
            setWorkingDirectory(mcPath);
            return;
        }

        System.err.println("Unable to write data to any location");
        System.exit(1);
    }
}
