package core.schematic;

import core.config.Config;
import core.coordinates.Coordinate2D;
import core.coordinates.CoordinateDouble3D;
import core.interfaces.IWorldManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Core-side guard that limits how many chunks are retained in memory during
 * schematic mode. When the player moves, chunks farther than the configured
 * radius (in chunks, Chebyshev distance) are evicted from the per-version
 * {@link IWorldManager} and removed from the preview map.
 *
 * <p>Eviction runs on a dedicated single-thread executor so that teleporting
 * across a large distance (which suddenly puts thousands of chunks outside the
 * radius) does not block the proxy's packet-processing thread and cause the
 * player to be disconnected for lag.
 *
 * <p>A radius of 0 means unlimited — no eviction is performed, matching the
 * original schematic-mode behaviour where every visited chunk stays in memory.
 */
public final class SchematicRadiusGuard implements BiConsumer<CoordinateDouble3D, Double> {

    private final IWorldManager worldManager;
    private final ExecutorService executor;
    private final AtomicBoolean evictionInProgress = new AtomicBoolean(false);
    private volatile Coordinate2D pendingCenter;

    public SchematicRadiusGuard(IWorldManager worldManager) {
        this.worldManager = worldManager;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "schematic-radius-guard");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void accept(CoordinateDouble3D pos, Double rotation) {
        if (!Config.isSchematicMode()) {
            return;
        }
        int radius = Config.getSchematicRadius();
        if (radius <= 0) {
            return;
        }

        Coordinate2D currentChunk = pos.discretize().globalToChunk();

        // Update the latest center regardless — if an eviction is already
        // running, the next one will use the most recent position.
        pendingCenter = currentChunk;

        // Skip if an eviction is already in progress (e.g. player is moving
        // fast or teleporting). The next position update will pick it up.
        if (!evictionInProgress.compareAndSet(false, true)) {
            return;
        }

        executor.execute(() -> {
            try {
                Coordinate2D center = pendingCenter;
                if (center != null) {
                    worldManager.unloadChunksOutsideRadius(center, radius);
                }
            } finally {
                evictionInProgress.set(false);
            }
        });
    }
}
