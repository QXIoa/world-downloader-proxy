package core.gui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core-level cache for player head images, keyed by UUID string.
 *
 * <p>Heads are fetched from mc-heads.net ({@code https://mc-heads.net/avatar/<uuid>/16})
 * on a background thread. While a head is loading (or if the fetch fails),
 * {@link #getHead(String)} returns {@code null} and the GUI should fall back
 * to the default Steve head.
 *
 * <p>UUID is used instead of username because servers may change display names
 * with formatting codes (§a§b§c...) which are not valid for API lookups.
 *
 * <p>This is core logic (external API, not Minecraft protocol) and does not
 * belong in per-version packages.
 */
public final class PlayerHeadCache {

    private static final String AVATAR_URL = "https://mc-heads.net/avatar/";
    private static final int HEAD_SIZE = 16;

    private static final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> requested = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private PlayerHeadCache() {}

    /**
     * Returns the cached head for the given UUID (dashed format), or {@code null}
     * if it hasn't been fetched yet. Triggers an async fetch if this is the first
     * request for the UUID.
     */
    public static BufferedImage getHead(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        BufferedImage head = cache.get(uuid);
        if (head != null) return head;

        // Trigger async fetch if not already requested
        if (requested.putIfAbsent(uuid, true) == null) {
            executor.submit(() -> fetchHead(uuid));
        }
        return null;
    }

    private static void fetchHead(String uuid) {
        try {
            BufferedImage head = ImageIO.read(new URL(AVATAR_URL + uuid + "/" + HEAD_SIZE));
            if (head != null) {
                // Ensure ARGB format for consistent rendering
                if (head.getType() != BufferedImage.TYPE_INT_ARGB) {
                    BufferedImage converted = new BufferedImage(
                        HEAD_SIZE, HEAD_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = converted.createGraphics();
                    g.drawImage(head, 0, 0, HEAD_SIZE, HEAD_SIZE, null);
                    g.dispose();
                    head = converted;
                }
                cache.put(uuid, head);
            }
        } catch (Exception e) {
            // Non-fatal — GUI falls back to Steve head
        }
    }

    /**
     * Clears the cache (e.g. on disconnect).
     */
    public static void clear() {
        cache.clear();
        requested.clear();
    }
}
