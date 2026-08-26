package core.interfaces;

/**
 * Core seam interface for sending feedback messages to the connected client
 * during the schematic selection workflow.
 *
 * <p>Core schematic code ({@link core.schematic.export.SchematicExportService})
 * uses this interface to send status messages without depending on per-version
 * packet construction classes.
 */
public interface ISelectionFeedback {
    /**
     * Send a feedback message to the client (e.g. via boss bar or action bar).
     */
    void send(String message);

    /**
     * Remove any feedback overlay from the client's screen.
     */
    void clear();
}
