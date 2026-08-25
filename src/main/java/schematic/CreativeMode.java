package schematic;

import config.Config;
import packets.builder.GameEventBuilder;
import packets.builder.PacketBuilder;
import proxy.EncryptionManager;
import proxy.PacketInjector;

/**
 * Local-only fly mode: tells the client to switch to creative (so the player can fly
 * freely to inspect high builds for schematic selection) while the server still thinks the
 * player is at their original position. Movement packets from the client are intercepted and
 * NOT forwarded to the server while this mode is active.
 *
 * <p>Toggle with {@code /world-downloader-proxy fly}.
 *
 * <p>Packet construction is delegated to {@link GameEventBuilder}; this class only manages
 * the fly-mode state and delivery.
 */
public class CreativeMode {
    private boolean active = false;
    private int savedGamemode = GameEventBuilder.GAMEMODE_SURVIVAL;

    public boolean isActive() {
        return active;
    }

    /**
     * Track the player's current game mode from the server's GameEvent packets so we can
     * restore it when fly mode is disabled.
     */
    public void onServerGameEvent(int eventId, float value) {
        if (eventId == GameEventBuilder.EVENT_CHANGE_GAMEMODE) {
            savedGamemode = (int) value;
        }
    }

    /**
     * Toggle fly mode on/off.
     * @return true if now active, false if now disabled.
     */
    public boolean toggle() {
        if (active) {
            disable();
        } else {
            enable();
        }
        return active;
    }

    public void enable() {
        if (active) {
            return;
        }
        active = true;
        sendGameEvent(GameEventBuilder.EVENT_CHANGE_GAMEMODE, GameEventBuilder.GAMEMODE_CREATIVE);
    }

    public void disable() {
        if (!active) {
            return;
        }
        active = false;
        sendGameEvent(GameEventBuilder.EVENT_CHANGE_GAMEMODE, savedGamemode);
    }

    /**
     * @return true if the given serverbound packet should be swallowed (not forwarded to the
     *         server) because the player is flying locally and the server shouldn't see the movement.
     */
    public boolean shouldInterceptMovement() {
        return active;
    }

    private void sendGameEvent(int event, float value) {
        PacketBuilder pb = GameEventBuilder.build(event, value);
        EncryptionManager em = Config.getEncryptionManager();
        if (em != null) {
            try {
                em.streamToClientDirect(pb);
                return;
            } catch (Exception e) {
                // fall through to injector
            }
        }
        PacketInjector injector = Config.getPacketInjector();
        if (injector != null) {
            injector.enqueuePacket(pb);
        }
    }

    /**
     * Send an AcceptTeleportation packet to the server so it doesn't kick the player
     * while we're swallowing its position sync packets.
     */
    public void sendAcceptTeleportation(int teleportId) {
        EncryptionManager em = Config.getEncryptionManager();
        if (em == null) {
            return;
        }
        int packetId = Config.versionReporter().getProtocol().serverBound("AcceptTeleportation");
        if (packetId < 0) {
            return;
        }
        PacketBuilder pb = new PacketBuilder(packetId);
        pb.writeVarInt(teleportId);
        try {
            em.streamToServer(pb);
        } catch (Exception e) {
            // ignore — best effort
        }
    }
}
