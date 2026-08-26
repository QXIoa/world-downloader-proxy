package core.schematic.export;

import core.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import core.schematic.BoundingBox;
import core.interfaces.ISelectionFeedback;
import core.schematic.SelectionState;
import core.messages.Messages;

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
    private final ISelectionFeedback feedback;
    private final Path outputDirectory;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "schematic-feedback");
            t.setDaemon(true);
            return t;
        });

    public SchematicExportService(SchematicExporter exporter, SchematicFileNamer fileNamer,
                                   ISelectionFeedback feedback, Path outputDirectory) {
        this.exporter = exporter;
        this.fileNamer = fileNamer;
        this.feedback = feedback;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Wires up the default, production configuration: Sponge Schematic v3 output, reading blocks
     * from the live world manager, writing into the configured schematic output directory.
     *
     * @param feedback the feedback channel (e.g. boss bar) used to report export progress/result
     */
    public static SchematicExportService createDefault(ISelectionFeedback feedback) {
        return new SchematicExportService(
            new SpongeSchematicExporter(Config.getVersionModule().createBlockRegionReader()),
            new SchematicFileNamer(),
            feedback,
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
                feedback.send(Messages.server("server.export.no_selection"));
                return;
            }

            if (!state.getDimension().equals(core.config.Config.getVersionModule().getWorldManager().getDimension())) {
                feedback.send(Messages.server("server.export.wrong_dimension"));
                return;
            }

            BoundingBox box = state.toBoundingBox();

            exportToFile(box, state);
        } finally {
            // Clear the corners but keep selection mode enabled so the player can
            // immediately start a new selection. The boss bar is NOT cleared here —
            // the export result message stays visible briefly, then transitions to
            // a "waiting for new selection" prompt.
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

            // Verify the file was actually created and is non-empty — a successful
            // exporter.export() call does not guarantee the file exists on disk if
            // the underlying NBT writer silently failed or was a no-op.
            Path absoluteTarget = target.toAbsolutePath();
            if (!Files.exists(absoluteTarget)) {
                feedback.send(Messages.server("server.export.failed_no_file", target.getFileName()));
                return;
            }
            long size;
            try {
                size = Files.size(absoluteTarget);
            } catch (IOException ioex) {
                feedback.send(Messages.server("server.export.failed_verify", ioex.getMessage()));
                return;
            }
            if (size == 0) {
                feedback.send(Messages.server("server.export.failed_empty_file", target.getFileName()));
                return;
            }

            feedback.send(Messages.server("server.export.success", target.getFileName(), formatSize(size)));
            // After a brief pause, prompt for the next selection — but only if the
            // player hasn't already started making one (e.g. set a new pos1).
            scheduler.schedule(() -> {
                if (!state.hasCompleteSelection() && state.getPos1() == null) {
                    feedback.send(Messages.server("server.selection.waiting"));
                }
            }, 3, TimeUnit.SECONDS);
        } catch (IOException e) {
            feedback.send(Messages.server("server.export.failed_io", e.getMessage()));
        } catch (Exception e) {
            // Catch runtime exceptions too — the file may have been partially or fully written
            // before the error, so the player needs to know something went wrong.
            feedback.send(Messages.server("server.export.failed_generic", e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
