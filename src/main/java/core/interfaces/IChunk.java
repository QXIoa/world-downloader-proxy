package core.interfaces;

import core.coordinates.CoordinateDim2D;

/**
 * Core seam interface for the per-version chunk.
 *
 * <p>Core GUI code uses this interface to interact with chunks without depending
 * on a concrete per-version class.
 */
public interface IChunk {
    /**
     * The chunk's dimensional coordinates.
     */
    CoordinateDim2D getLocation();

    /**
     * The factory that produces rendered images of this chunk.
     */
    IChunkImageFactory getChunkImageFactory();

    /**
     * Run the given callback when the chunk has been fully parsed.
     */
    void whenParsed(Runnable r);
}
