package packets.builder;

import config.Config;
import config.PacketFormat;

/**
 * Builds {@code LevelParticles} packets for a single particle at an exact position.
 * Knows about the version-specific field layout (Int vs VarInt for particleId,
 * Float vs Double for position, particleId at start vs end, alwaysShow flag) but
 * does not know anything about what the particles are used for.
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
        PacketFormat fmt = Config.versionReporter().packetFormat();

        if (!fmt.particleIdAtEnd()) {
            // 1.12 – 1.20.4: particleId comes first
            if (fmt.particleIdIsVarInt()) {
                pb.writeVarInt(particleId);
            } else {
                pb.writeInt(particleId);
            }
            pb.writeBoolean(true);   // longDistance
            writePosition(pb, fmt, x, y, z);
            writeOffsetsAndCount(pb);
        } else {
            // 1.20.5+: longDistance first, particleId at end
            pb.writeBoolean(true);   // longDistance
            if (fmt.particleHasAlwaysShow()) {
                pb.writeBoolean(false);  // alwaysShow
            }
            writePosition(pb, fmt, x, y, z);
            writeOffsetsAndCount(pb);
            pb.writeVarInt(particleId);
        }

        return pb;
    }

    private static void writePosition(PacketBuilder pb, PacketFormat fmt, double x, double y, double z) {
        if (fmt.particlePositionIsDouble()) {
            pb.writeDouble(x);
            pb.writeDouble(y);
            pb.writeDouble(z);
        } else {
            pb.writeFloat((float) x);
            pb.writeFloat((float) y);
            pb.writeFloat((float) z);
        }
    }

    private static void writeOffsetsAndCount(PacketBuilder pb) {
        pb.writeFloat(0f);   // offsetX
        pb.writeFloat(0f);   // offsetY
        pb.writeFloat(0f);   // offsetZ
        pb.writeFloat(0f);   // particleData / velocityOffset
        pb.writeInt(0);      // particles / amount = 0 → exact position
    }
}
