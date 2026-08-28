package core.interfaces;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Core seam interface for the per-version chunk image factory.
 *
 * <p>Core GUI code uses this interface to request chunk rendering without
 * depending on a concrete per-version class.
 */
public interface IChunkImageFactory {
    void onComplete(BiConsumer<Map<core.gui.images.ImageMode, BufferedImage>, Boolean> onComplete);
    void onSaved(Runnable onSaved);
    void requestImage();
    void markSaved();
}
