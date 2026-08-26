package core.interfaces;

import java.util.Collection;

/**
 * Core seam interface for the per-version entity registry.
 *
 * <p>Core GUI code uses this interface to access the set of known players
 * without depending on a concrete per-version class.
 */
public interface IEntityRegistry {
    Collection<IPlayerEntity> getPlayerSet();
}
