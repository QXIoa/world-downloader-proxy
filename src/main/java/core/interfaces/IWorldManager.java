package core.interfaces;

import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDouble3D;
import java.util.function.BiConsumer;

/**
 * Core seam interface for the per-version world manager.
 *
 * <p>Core GUI code uses this interface to interact with the world manager without
 * depending on a concrete per-version class.
 */
public interface IWorldManager {
    void shutdown();
    void save();
    boolean isPaused();
    void resumeSaving();
    void pauseSaving();
    void deleteAllExisting();
    void drawExistingChunks(core.coordinates.Coordinate2D region);
    void drawExistingRegion(core.coordinates.Coordinate2D region);
    int countActiveRegions();
    int countActiveBinaryChunks();
    int countQueuedChunks();
    int countActiveChunks();
    int countExtendedChunks();
    IDimension getDimension();
    boolean isBelowGround();
    Coordinate3D getPlayerPosition();
    CoordinateDouble3D getPlayerPositionDouble();
    IEntityRegistry getEntityRegistry();
    void setPlayerPosListener(BiConsumer<CoordinateDouble3D, Double> playerPosListener);
    int countActiveEntities();
    int countActivePlayers();
    int countActiveMaps();
}
