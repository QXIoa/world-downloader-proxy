package core.schematic;

import core.config.Config;
import core.coordinates.Coordinate2D;
import core.coordinates.CoordinateDouble3D;
import core.interfaces.IWorldManager;
import java.util.function.BiConsumer;

/**
 * Core-side guard that limits how many chunks are retained in memory during
 * schematic mode. When the player moves, chunks farther than the configured
 * radius (in chunks, Chebyshev distance) are evicted from the per-version
 * {@link IWorldManager} and greyed out on the preview map.
 *
 * <p>The guard is installed as a {@code playerPosListener} on the world manager
 * via {@link IWorldManager#setPlayerPosListener}. Core controls the policy
 * (radius value from {@link core.config.Config}); the per-version world manager
 * only executes the eviction.
 *
 * <p>A radius of 0 means unlimited — no eviction is performed, matching the
 * original schematic-mode behaviour where every visited chunk stays in memory.
 */
public final class SchematicRadiusGuard implements BiConsumer<CoordinateDouble3D, Double> {

    private final IWorldManager worldManager;
    private Coordinate2D lastEvictedChunk;

    public SchematicRadiusGuard(IWorldManager worldManager) {
        this.worldManager = worldManager;
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
        // Only evict when the player crosses a chunk boundary — doing it every
        // position update would be wasteful.
        if (currentChunk.equals(lastEvictedChunk)) {
            return;
        }
        lastEvictedChunk = currentChunk;

        worldManager.unloadChunksOutsideRadius(currentChunk, radius);
    }
}
