package core.gui;


import core.config.Config;
import core.coordinates.Coordinate2D;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IChunk;
import core.interfaces.IDimension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;

/**
 * Dispatcher that routes chunk/map callbacks to the active GUI bridge
 * (Compose Desktop). Previously also managed the JavaFX Application lifecycle;
 * JavaFX has been removed in favour of Compose Desktop.
 */
public class GuiManager {
    private static boolean hasErrors;
    private static boolean authenticationFailed;
    private static GuiBridge guiBridge;
    private static Config config;

    public static void setConfig(Config config) {
        GuiManager.config = config;
    }

    public static void setGuiBridge(GuiBridge bridge) {
        guiBridge = bridge;
    }

    public static boolean isStarted() {
        return guiBridge != null;
    }

    public static void loadSceneMap() {
        // No-op: Compose GUI manages its own scenes.
    }

    public static void loadSceneSettings() {
        // No-op: Compose GUI manages its own scenes.
    }

    public static void loadWindowSettings() {
        if (guiBridge != null) {
            guiBridge.hideErrorMessage();
        }
    }

    public static void setDimension(IDimension dimension) {
        if (guiBridge != null) {
            guiBridge.setDimension(dimension);
        }
    }

    public static void redirectErrorOutput() {
        System.setErr(new PrintStream(new ByteArrayOutputStream() {
            @Override
            public synchronized void write(byte[] b, int off, int len) {
                notifyNewError();
                messages.add(this.toString());
                this.reset();
                super.write(b, off, len);
            }
        }));
    }

    public static void setStatusMessage(String str) {
        if (guiBridge != null) {
            guiBridge.setStatusMessage(str);
        }
    }

    private static void notifyNewError() {
        if (!GuiManager.hasErrors) {
            GuiManager.hasErrors = true;
        }
        // Pass the last error message to the GUI bridge
        if (guiBridge != null && !messages.isEmpty()) {
            guiBridge.addError(messages.get(messages.size() - 1));
        }
        if (guiBridge != null) {
            guiBridge.showErrorMessage();
        }
    }

    public static boolean hasErrors() {
        return hasErrors;
    }

    public static boolean clearAuthentiationStatus() {
        return authenticationFailed;
    }

    public static void resetRegion(Coordinate2D regionLocation) {
        if (guiBridge != null) {
            guiBridge.resetRegion(regionLocation);
        }
    }

    public static void setAuthenticationFailed() {
        authenticationFailed = true;
    }

    public static void clearAuthenticationFailed() {
        authenticationFailed = false;
    }

    public static void setChunkState(Coordinate2D coords, ChunkImageState state) {
        if (guiBridge != null) {
            guiBridge.setChunkState(coords, state);
        }
    }

    public static void clearChunk(Coordinate2D coords) {
        if (guiBridge != null) {
            guiBridge.clearChunk(coords);
        }
    }

    public static void clearRegionsOutsideRadius(Coordinate2D center, int radius) {
        if (guiBridge != null) {
            guiBridge.clearRegionsOutsideRadius(center, radius);
        }
    }

    /**
     * Add a custom message to the GUI error log (e.g. schematic diagnostics).
     * Unlike {@link #notifyNewError()}, this does not set the global error flag
     * or show the error popup — it only appends to the error list.
     */
    public static void addError(String message) {
        if (guiBridge != null) {
            guiBridge.addError(message);
        }
    }

    public static boolean openWebLink(String text) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(text));
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean openFileLink(String text) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(new File(text));
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    static Config getConfig() {
        return config;
    }

    public static void saveAndExit() {
        try {
            Config.getVersionModule().getWorldManager().shutdown();
            Config.getVersionModule().getWorldManager().save();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    /**
     * Set a chunk to being loaded.
     * @param coord the chunk coordinates
     * @param chunk the chunk object
     */
    public static void setChunkLoaded(CoordinateDim2D coord, IChunk chunk) {
        if (guiBridge != null) {
            guiBridge.setChunkLoaded(coord, chunk);
        }
    }

    public static void clearChunks() {
        if (guiBridge != null) {
            guiBridge.clearChunks();
        }
    }

    static java.util.List<String> getMessages() {
        return messages;
    }

    private static final java.util.List<String> messages = new java.util.ArrayList<>();
}
