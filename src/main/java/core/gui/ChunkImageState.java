package core.gui;

import core.config.Config;
import java.awt.Color;
import java.util.function.BooleanSupplier;

public enum ChunkImageState {
    SAVED(new Color(0, 0, 0, 0)),
    DEBUG(new Color(0, 0, 255, 77)),
    UNSAVED(new Color(255, 0, 0, 89), Config::markUnsavedChunks),
    EXTENDED(new Color(0, 255, 0, 77), Config::drawExtendedChunks),
    OUTDATED(new Color(41, 41, 41, 115), Config::markOldChunks);

    private final Color color;
    private final BooleanSupplier condition;

    ChunkImageState(Color color, BooleanSupplier condition) {
        this.color = color;
        this.condition = condition;
    }
    ChunkImageState(Color color) {
        this(color, () -> true);
    }


    public Color getColor() {
        if (!condition.getAsBoolean()) {
            return new Color(0, 0, 0, 0);
        }
        return color;
    }

    public static ChunkImageState isSaved(Boolean isSaved) {
        if (isSaved) {
            return SAVED;
        } else {
            return UNSAVED;
        }
    }
}
