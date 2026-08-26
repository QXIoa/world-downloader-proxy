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
}
