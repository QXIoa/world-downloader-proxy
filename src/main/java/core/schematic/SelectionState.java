package core.schematic;

import core.coordinates.Coordinate3D;
import core.interfaces.IDimension;

/**
 * Holds the in-game block selection (pos1/pos2) and whether selection mode is currently active.
 * Pure state: knows nothing about packets, chat commands or exporting, which makes it trivial to
 * unit test and to reason about independently of the rest of the feature.
 *
 * There is one {@code SelectionState} per proxy session (analogous to {@code WorldManager}), since
 * the proxy only ever tracks a single connected player at a time.
 */
public class SelectionState {
    private boolean enabled = false;
    private Coordinate3D pos1;
    private Coordinate3D pos2;
    private IDimension dimension;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return true if this call actually changed the state (was disabled, now enabled)
     */
    public boolean enable() {
        if (enabled) {
            return false;
        }
        enabled = true;
        return true;
    }

    /**
     * @return true if this call actually changed the state (was enabled, now disabled)
     */
    public boolean disable() {
        if (!enabled) {
            return false;
        }
        enabled = false;
        return true;
    }

    /**
     * @return the new enabled state after toggling
     */
    public boolean toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
        return enabled;
    }

    public void setPos1(Coordinate3D pos, IDimension dim) {
        setCorner(pos, dim, true);
    }

    public void setPos2(Coordinate3D pos, IDimension dim) {
        setCorner(pos, dim, false);
    }

    private void setCorner(Coordinate3D pos, IDimension dim, boolean isFirstCorner) {
        if (dimension != null && !dimension.equals(dim)) {
            // a selection spanning two dimensions doesn't make sense; start over in the new one
            clear();
        }
        dimension = dim;

        if (isFirstCorner) {
            // Setting a new pos1 starts a fresh selection: discard the old pos2 so the
            // renderer shows only the single pos1 block until a new pos2 is set.
            pos1 = pos;
            pos2 = null;
        } else {
            pos2 = pos;
        }
    }

    public boolean hasCompleteSelection() {
        return pos1 != null && pos2 != null;
    }

    public Coordinate3D getPos1() {
        return pos1;
    }

    public Coordinate3D getPos2() {
        return pos2;
    }

    public IDimension getDimension() {
        return dimension;
    }

    public BoundingBox toBoundingBox() {
        if (!hasCompleteSelection()) {
            throw new IllegalStateException("Selection is not complete, both pos1 and pos2 are required.");
        }
        return new BoundingBox(pos1, pos2);
    }

    /**
     * Clear pos1/pos2 (and the remembered dimension) without touching whether selection mode is
     * enabled. Toggling selection mode off intentionally does not call this, so a player can pause
     * and resume without losing their corners; only an export (or an explicit clear command)
     * should discard the selection.
     */
    public void clear() {
        pos1 = null;
        pos2 = null;
        dimension = null;
    }
}
