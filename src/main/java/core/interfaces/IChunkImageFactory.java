package core.interfaces;

import core.coordinates.CoordinateDim2D;
import java.util.Map;
import javafx.scene.image.Image;
import java.util.function.BiConsumer;

/**
 * Core seam interface for the per-version chunk image factory.
 *
 * <p>Core GUI code uses this interface to request chunk rendering without
 * depending on a concrete per-version class.
 */
public interface IChunkImageFactory {
    void onComplete(BiConsumer<Map<core.gui.images.ImageMode, Image>, Boolean> onComplete);
    void onSaved(Runnable onSaved);
    void requestImage();
    void markSaved();
}
