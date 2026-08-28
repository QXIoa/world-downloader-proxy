package core.interfaces;

import core.coordinates.CoordinateDouble3D;

/**
 * Core seam interface for the per-version player entity.
 *
 * <p>Core GUI code uses this interface to render other players on the map
 * without depending on a concrete per-version class.
 */
public interface IPlayerEntity {
    CoordinateDouble3D getPosition();
    String getName();

    /**
     * Returns the player's UUID in dashed format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).
     * Used for head skin lookups — more reliable than name, which may contain
     * formatting codes or be changed by the server.
     */
    String getUUIDString();
}
