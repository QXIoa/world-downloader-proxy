package schematic;

import config.Config;
import packets.builder.BossBarBuilder;
import packets.builder.MessageTarget;
import packets.builder.PacketBuilder;
import packets.UUID;
import proxy.EncryptionManager;
import proxy.PacketInjector;

/**
 * Sends feedback about the schematic selection workflow to the connected client via
 * a boss bar overlay. Handles only message formatting (prefix) and delivery — the
 * actual packet construction is delegated to {@link BossBarBuilder}.
 */
public class SelectionFeedback {
    private static final String PREFIX = "[WD] ";
    private static final UUID BOSS_BAR_UUID = new UUID(0L, 42L);
    private static final byte BOSS_FLAGS = 0;

    public void send(String message) {
        String text = PREFIX + message;
        EncryptionManager em = Config.getEncryptionManager();
        if (em != null) {
            PacketBuilder pb = BossBarBuilder.buildAdd(BOSS_BAR_UUID, text,
                    BossBarBuilder.COLOR_YELLOW, BossBarBuilder.DIVISION_NONE, BOSS_FLAGS);
            try {
                em.streamToClientDirect(pb);
                return;
            } catch (Exception e) {
                // fall through to injector
            }
        }
        // Fallback: use the injector queue with action bar
        PacketInjector injector = Config.getPacketInjector();
        if (injector != null) {
            injector.enqueuePacket(PacketBuilder.constructClientMessage(text, MessageTarget.GAMEINFO));
        }
    }

    /**
     * Remove the boss bar from the client's screen.
     */
    public void clear() {
        EncryptionManager em = Config.getEncryptionManager();
        if (em == null) {
            return;
        }
        PacketBuilder pb = BossBarBuilder.buildRemove(BOSS_BAR_UUID);
        try {
            em.streamToClientDirect(pb);
        } catch (Exception e) {
            // best effort
        }
    }
}
