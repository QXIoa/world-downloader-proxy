package core.schematic.export;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.interfaces.ISelectionFeedback;
import core.schematic.SelectionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import version.v26_1.dimension.Dimension;
import version.v26_1.module.VersionModuleImpl;
import version.v26_1.world.WorldManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SchematicExportServiceTest {
    @TempDir
    Path tempDir;

    private CapturingFeedback feedback;

    @BeforeEach
    void setUp() {
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        // Reset the WorldManager singleton so the dimension check in
        // exportAndClear sees OVERWORLD (the default). Other tests in the
        // full suite may have changed the singleton's dimension.
        WorldManager.setInstance(new WorldManager());
        feedback = new CapturingFeedback();
    }

    @Test
    void exportRunsAsynchronouslyAndDoesNotBlockTheCaller() throws Exception {
        // The exporter blocks until the latch is released; if the export ran
        // synchronously (on the calling thread), the test would time out.
        CountDownLatch exportStarted = new CountDownLatch(1);
        CountDownLatch releaseExport = new CountDownLatch(1);

        SchematicExporter slowExporter = (box, dimension, targetFile) -> {
            exportStarted.countDown();
            try {
                releaseExport.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ExportResult(box.volume(), 0, 0, 0);
        };

        SchematicExportService service = new SchematicExportService(
            slowExporter, new SchematicFileNamer(), feedback, tempDir
        );

        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), Dimension.OVERWORLD);
        state.setPos2(new Coordinate3D(1, 0, 0), Dimension.OVERWORLD);

        long t0 = System.nanoTime();
        service.exportAndClear(state);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        // exportAndClear should return almost immediately (the heavy work is
        // on the background thread). 500ms is generous but well under the
        // 5s the exporter would block for.
        assertThat(elapsedMs).isLessThan(500);

        // The selection is cleared synchronously, even before the export finishes.
        assertThat(state.hasCompleteSelection()).isFalse();
        assertThat(state.getPos1()).isNull();

        // Let the export finish.
        releaseExport.countDown();
        assertThat(exportStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // Wait for the "started" and "success" feedback messages.
        feedback.awaitMessages(2, 5, TimeUnit.SECONDS);
        assertThat(feedback.containsMessageContaining("Exporting")).isTrue();
    }

    @Test
    void secondExportWhileOneIsInProgressIsRejected() throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger exportCount = new AtomicInteger(0);

        SchematicExporter exporter = (box, dimension, targetFile) -> {
            exportCount.incrementAndGet();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ExportResult(box.volume(), 0, 0, 0);
        };

        SchematicExportService service = new SchematicExportService(
            exporter, new SchematicFileNamer(), feedback, tempDir
        );

        SelectionState state1 = new SelectionState();
        state1.setPos1(new Coordinate3D(0, 0, 0), Dimension.OVERWORLD);
        state1.setPos2(new Coordinate3D(1, 0, 0), Dimension.OVERWORLD);

        service.exportAndClear(state1);

        // Try a second export while the first is still running.
        SelectionState state2 = new SelectionState();
        state2.setPos1(new Coordinate3D(10, 0, 0), Dimension.OVERWORLD);
        state2.setPos2(new Coordinate3D(11, 0, 0), Dimension.OVERWORLD);

        service.exportAndClear(state2);

        // The second export should have been rejected — the feedback should
        // contain the "busy" message (sent synchronously, so it's already there).
        assertThat(feedback.containsMessageContaining("already in progress")).isTrue();

        // Release the first export and wait for it to finish.
        releaseFirst.countDown();
        Thread.sleep(500); // give the background thread time to complete

        // Only one export should have actually run.
        assertThat(exportCount.get()).isEqualTo(1);
    }

    @Test
    void reportsIncompleteDataAfterExport() throws Exception {
        SchematicExporter exporter = (box, dimension, targetFile) -> {
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, "dummy");
            return new ExportResult(box.volume(), 42, 3, 7);
        };

        SchematicExportService service = new SchematicExportService(
            exporter, new SchematicFileNamer(), feedback, tempDir
        );

        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), Dimension.OVERWORLD);
        state.setPos2(new Coordinate3D(1, 0, 0), Dimension.OVERWORLD);

        service.exportAndClear(state);

        // Wait for: "started" + "success" + "incomplete" = 3 messages.
        feedback.awaitMessages(3, 5, TimeUnit.SECONDS);
        assertThat(feedback.containsMessageContaining("unloaded chunks")).isTrue();
    }

    @Test
    void doesNotReportIncompleteDataWhenExportIsComplete() throws Exception {
        SchematicExporter exporter = (box, dimension, targetFile) -> {
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, "dummy");
            return new ExportResult(box.volume(), 0, 2, 0);
        };

        SchematicExportService service = new SchematicExportService(
            exporter, new SchematicFileNamer(), feedback, tempDir
        );

        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), Dimension.OVERWORLD);
        state.setPos2(new Coordinate3D(1, 0, 0), Dimension.OVERWORLD);

        service.exportAndClear(state);

        // Wait for: "started" + "success" = 2 messages (no "incomplete").
        feedback.awaitMessages(2, 5, TimeUnit.SECONDS);
        assertThat(feedback.containsMessageContaining("unloaded chunks")).isFalse();
    }

    // --- Helpers ---

    /** A feedback that captures all messages so tests can assert on them. */
    private static final class CapturingFeedback implements ISelectionFeedback {
        final List<String> messages = new ArrayList<>();

        @Override
        public synchronized void send(String message) {
            messages.add(message);
            notifyAll();
        }

        @Override
        public void clear() { }

        synchronized void awaitMessages(int expected, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (messages.size() < expected) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                wait(remaining / 1_000_000, (int) (remaining % 1_000_000));
            }
        }

        synchronized boolean containsMessageContaining(String substring) {
            return messages.stream().anyMatch(m -> m.contains(substring));
        }
    }
}
