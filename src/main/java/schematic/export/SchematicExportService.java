package schematic.export;

import config.Config;
import game.data.WorldManager;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import schematic.BoundingBox;
import schematic.SelectionFeedback;
import schematic.SelectionState;

/**
 * Orchestrates exporting the current selection: validates it, picks an output file name, delegates
 * the actual encoding to a {@link SchematicExporter}, and always clears the selection afterwards so
 * that accidentally repeating the export command cannot silently duplicate/overwrite a file with
 * the same selection. This is the only class in the schematic feature where "export" turns into a
 * side effect; every other class it uses is either pure data or reusable across many exports.
 */
public class SchematicExportService {
    private final SchematicExporter exporter;
    private final SchematicFileNamer fileNamer;
    private final SelectionFeedback feedback;
    private final Path outputDirectory;

    public SchematicExportService(SchematicExporter exporter, SchematicFileNamer fileNamer,
                                   SelectionFeedback feedback, Path outputDirectory) {
        this.exporter = exporter;
        this.fileNamer = fileNamer;
        this.feedback = feedback;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Wires up the default, production configuration: Sponge Schematic v3 output, reading blocks
     * from the live {@link WorldManager}, writing into the configured schematic output directory.
     */
    public static SchematicExportService createDefault() {
        return new SchematicExportService(
            new SpongeSchematicExporter(new WorldManagerBlockRegionReader(WorldManager.getInstance())),
            new SchematicFileNamer(),
            new SelectionFeedback(),
            Path.of(Config.getSchematicOutputDir())
        );
    }

    /**
     * Exports the given selection (if valid) and clears it, regardless of whether the export
     * succeeded - a failed export should not be silently retried by accident, the player should
     * make a fresh selection.
     */
    public void exportAndClear(SelectionState state) {
        try {
            if (!state.hasCompleteSelection()) {
                feedback.send("No selection to export - set both pos1 and pos2 first.");
                return;
            }

            if (!state.getDimension().equals(WorldManager.getInstance().getDimension())) {
                feedback.send("Selection was made in a different dimension. Switch back or make a new selection.");
                return;
            }

            BoundingBox box = state.toBoundingBox();

            exportToFile(box, state);
        } finally {
            state.clear();
        }
    }

    private void exportToFile(BoundingBox box, SelectionState state) {
        String serverAddress = Config.getConnectionDetails() != null
            ? Config.getConnectionDetails().getFriendlyHost()
            : null;
        Path target = outputDirectory.resolve(fileNamer.buildFileName(Instant.now(), serverAddress));

        try {
            exporter.export(box, state.getDimension(), target);
            feedback.send("Exported " + box + " to " + target);
        } catch (IOException e) {
            feedback.send("Export failed: " + e.getMessage());
        } catch (Exception e) {
            // Catch runtime exceptions too — the file may have been partially or fully written
            // before the error, so the player needs to know something went wrong.
            feedback.send("Export failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
