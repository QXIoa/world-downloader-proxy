package core.schematic.export;

import core.config.Config;
import core.interfaces.IDimension;
import core.interfaces.ISelectionFeedback;
import core.messages.Messages;
import core.schematic.BoundingBox;
import core.schematic.SelectionState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates exporting the current selection: validates it, picks an output file name, delegates
 * the actual encoding to a {@link SchematicExporter}, and always clears the selection afterwards so
 * that accidentally repeating the export command cannot silently duplicate/overwrite a file with
 * the same selection. This is the only class in the schematic feature where "export" turns into a
 * side effect; every other class it uses is either pure data or reusable across many exports.
 *
 * <p>The heavy encoding work (iterating every block, building NBT, writing to disk) runs on a
 * dedicated background thread so it never blocks the proxy's packet-processing thread. Without
 * this, exporting a large selection can take seconds or minutes, during which the client's
 * keep-alive packets are not forwarded to the server and the server disconnects the player.
 */
public class SchematicExportService {
    private final SchematicExporter exporter;
    private final SchematicFileNamer fileNamer;
    private final ISelectionFeedback feedback;
    private final Path outputDirectory;

    /** Runs the "waiting for new selection" reminder after a successful export. */
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "schematic-feedback");
            t.setDaemon(true);
            return t;
        });

    /**
     * Runs the actual export (block iteration, NBT encoding, disk write) off the
     * network thread. Single-threaded: exports are serialised so two concurrent
     * exports don't fight over the same output directory or read the world
     * concurrently.
     */
    private final ExecutorService exportExecutor =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "schematic-export");
            t.setDaemon(true);
            return t;
        });

    /** Guards against a second export being queued while one is already running. */
    private final AtomicBoolean exportInProgress = new AtomicBoolean(false);

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
     *
     * <p>The selection is cleared synchronously (before the background export starts) so the
     * player cannot accidentally trigger a second export with the same corners while the first
     * one is still running.
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

            if (!exportInProgress.compareAndSet(false, true)) {
                feedback.send(Messages.server("server.export.busy"));
                return;
            }

            // Capture immutable snapshots before clearing the selection.
            BoundingBox box = state.toBoundingBox();
            IDimension dimension = state.getDimension();

            feedback.send(Messages.server("server.export.started", box));

            exportExecutor.execute(() -> {
                try {
                    exportToFile(box, dimension, state);
                } finally {
                    exportInProgress.set(false);
                }
            });
        } finally {
            // Clear the corners but keep selection mode enabled so the player can
            // immediately start a new selection. The boss bar is NOT cleared here —
            // the export result message stays visible briefly, then transitions to
            // a "waiting for new selection" prompt.
            state.clear();
        }
    }

    private void exportToFile(BoundingBox box, IDimension dimension, SelectionState state) {
        String serverAddress = Config.getConnectionDetails() != null
            ? Config.getConnectionDetails().getFriendlyHost()
            : null;
        Path target = outputDirectory.resolve(fileNamer.buildFileName(Instant.now(), serverAddress));

        try {
            System.out.println("[schematic-export] Starting export: " + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ()
                + " = " + box.volume() + " blocks");

            ExportResult result = exporter.export(box, dimension, target);

            System.out.println("[schematic-export] Export finished, verifying file...");

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

            // Report any data that was missing from the export — this is the key
            // diagnostic for "some heads had no skins after pasting": if chunks
            // inside the selection were never loaded, their block entities (and
            // thus head profile/skin data) are silently absent.
            if (result.hasMissingData()) {
                feedback.send(Messages.server("server.export.incomplete",
                        result.missingBlocks(),
                        result.missingHeads(),
                        result.exportedBlockEntities()));
            }

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
        } catch (Error e) {
            // OutOfMemoryError and other Errors — for a 144M-block export, OOME is the most
            // likely cause. Without this catch, the error propagates to the executor's uncaught
            // exception handler and the player never sees a failure message (silent hang).
            System.err.println("[schematic-export] FATAL error during export: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            feedback.send(Messages.server("server.export.failed_generic",
                e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "(out of memory?)"));
            throw e;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
