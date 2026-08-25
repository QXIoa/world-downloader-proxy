package schematic;

import config.Config;
import config.PacketFormat;
import game.data.WorldManager;
import game.data.coordinates.Coordinate3D;
import game.data.dimension.Dimension;
import packets.DataTypeProvider;

/**
 * Translates the player's left/right clicks into pos1/pos2 updates on the {@link SelectionState}
 * while selection mode is enabled. Wired into {@code ServerBoundGamePacketHandler}'s
 * "PlayerAction" and "UseItemOn" operators.
 *
 * When selection mode is disabled, both methods return {@code true} immediately without reading
 * anything from the packet, so there is zero behavioural change for normal gameplay. This also
 * bounds the impact of a possibly-incorrect packet-id-to-name mapping for some legacy protocol
 * version: at worst it can only misbehave while a player has explicitly opted into selection mode.
 */
public class SelectionInputInterceptor {
    /** Status value of the "PlayerAction" packet that means "started destroying a block". */
    private static final int START_DESTROY_BLOCK = 0;

    private final SelectionState selectionState;
    private final SelectionFeedback feedback;

    public SelectionInputInterceptor(SelectionState selectionState, SelectionFeedback feedback) {
        this.selectionState = selectionState;
        this.feedback = feedback;
    }

    /**
     * Handle a serverbound "PlayerAction" packet (sent when the player starts/stops/cancels
     * digging a block). Only the packet's leading Status + Location fields are read; that is
     * enough to detect "started digging" and where, and (since this fully consumes the packet
     * without forwarding it) correctness of any trailing fields such as Face/Sequence does not
     * matter here.
     *
     * @return true if the packet should be forwarded to the real server, false if it was consumed
     *         as a pos1 marker (or otherwise swallowed while selecting) and must not reach the server
     */
    public boolean onPlayerAction(DataTypeProvider provider) {
        if (!selectionState.isEnabled()) {
            return true;
        }

        PacketFormat fmt = Config.versionReporter().packetFormat();
        int status = fmt.playerActionStatusIsVarInt() ? provider.readVarInt() : provider.readNext();
        Coordinate3D position = provider.readCoordinates();

        if (status == START_DESTROY_BLOCK) {
            applyCorner(position, true);
        }
        return false;
    }

    /**
     * Handle a serverbound "UseItemOn" packet (right-click on a block).
     *
     * <p>Packet layout differs by version:
     * <ul>
     *   <li><b>1.19+</b>: hand (VarInt), position, face (VarInt), ...</li>
     *   <li><b>1.13–1.18</b>: position, face/direction (VarInt), hand (VarInt), ...</li>
     * </ul>
     *
     * @return true if the packet should be forwarded to the real server (selection mode is off,
     *         nothing was read), false if it was consumed as a pos2 marker and must not reach the
     *         server
     */
    public boolean onUseItemOn(DataTypeProvider provider) {
        if (!selectionState.isEnabled()) {
            return true;
        }

        PacketFormat fmt = Config.versionReporter().packetFormat();
        Coordinate3D position;
        if (fmt.useItemOnHandFirst()) {
            // 1.19+: hand first, then position
            provider.readVarInt(); // hand
            position = provider.readCoordinates();
        } else {
            // 1.13–1.18: position first, then face, then hand
            position = provider.readCoordinates();
            provider.readVarInt(); // face/direction
            provider.readVarInt(); // hand
        }

        applyCorner(position, false);
        return false;
    }

    private void applyCorner(Coordinate3D position, boolean isFirstCorner) {
        Dimension dimension = WorldManager.getInstance().getDimension();

        if (isFirstCorner) {
            selectionState.setPos1(position, dimension);
            feedback.send("pos1 set: " + position);
        } else {
            selectionState.setPos2(position, dimension);
            feedback.send("pos2 set: " + position);
        }

        if (selectionState.hasCompleteSelection()) {
            feedback.send("Selection: " + selectionState.toBoundingBox());
        }
    }
}
