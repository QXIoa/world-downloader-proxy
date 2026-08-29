package core.gui;

import core.coordinates.Coordinate2D;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IChunk;
import core.interfaces.IDimension;

/**
 * Bridge interface allowing non-JavaFX GUIs (e.g. Compose Desktop) to receive
 * chunk and map callbacks from the backend without launching JavaFX.
 *
 * <p>When set via {@link GuiManager#setGuiBridge(GuiBridge)}, GuiManager delegates
 * to this bridge whenever the JavaFX {@code chunkGraphicsHandler} is null.
 */
public interface GuiBridge {
    void setChunkLoaded(CoordinateDim2D coord, IChunk chunk);
    void setDimension(IDimension dimension);
    void clearChunks();
    void resetRegion(Coordinate2D regionLocation);
    void setChunkState(Coordinate2D coords, ChunkImageState state);
    void clearChunk(Coordinate2D coords);
    void setStatusMessage(String str);
    void showErrorMessage();
    void hideErrorMessage();
    void addError(String message);
}
