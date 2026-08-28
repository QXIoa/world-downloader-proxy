package core.interfaces;

import core.coordinates.CoordinateDim2D;

import java.io.Serializable;

/**
 * Core seam interface for the per-version chunk binary (serialized chunk data
 * as stored in MCA files).
 *
 * <p>Core GUI code uses this interface to serialize chunk data to disk via
 * {@code ObjectOutputStream.writeObject()} without depending on a concrete
 * per-version class. The interface extends {@link Serializable} so that
 * implementations can be serialized directly.
 */
public interface IChunkBinary extends Serializable {
    /**
     * Deserialize this binary back into a chunk at the given coordinates.
     * The returned object is per-version; core code treats it as {@code Object}.
     */
    Object toChunk(CoordinateDim2D coordinate2D);
}
