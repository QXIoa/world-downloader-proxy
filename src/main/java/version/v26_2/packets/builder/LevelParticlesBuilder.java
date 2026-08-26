package version.v26_2.packets.builder;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

/**
 * Builds {@code LevelParticles} packets for a single particle at an exact position.
 * 26.x layout: longDistance, alwaysShow, Double position, offsets, VarInt particleId at end.
 */
public final class LevelParticlesBuilder {
    private LevelParticlesBuilder() { }

    /**
     * Build a LevelParticles packet for a single particle with no extra data payload
     * (e.g. flame) at an exact position.
     *
     * @param particleId the numeric particle ID (from the server registry or fallback)
     * @param x          exact world X
     * @param y          exact world Y
     * @param z          exact world Z
     */
    public static PacketBuilder buildSingle(int particleId, double x, double y, double z) {
        PacketBuilder pb = new PacketBuilder("LevelParticles");

        // 26.x: longDistance, alwaysShow, Double position, offsets, VarInt particleId at end
        pb.writeBoolean(true);   // longDistance
        pb.writeBoolean(false);  // alwaysShow
        pb.writeDouble(x);
        pb.writeDouble(y);
        pb.writeDouble(z);
        writeOffsetsAndCount(pb);
        pb.writeVarInt(particleId);

        return pb;
    }

    private static void writeOffsetsAndCount(PacketBuilder pb) {
        pb.writeFloat(0f);   // offsetX
        pb.writeFloat(0f);   // offsetY
        pb.writeFloat(0f);   // offsetZ
        pb.writeFloat(0f);   // particleData / velocityOffset
        pb.writeInt(0);      // particles / amount = 0 → exact position
    }
}
