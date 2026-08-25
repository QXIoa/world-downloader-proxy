package config;

/**
 * Centralised knowledge about how packet field layouts differ between Minecraft versions.
 * Every "does this version use X or Y?" question lives here instead of being scattered
 * across the codebase as {@code if (isAtLeast(V1_19))} checks, which historically caused
 * bugs when the {@link Version} enum's protocol numbers didn't match the real protocol
 * number reported by the client (e.g. 1.14.4 is protocol 498 but the enum uses 440).
 *
 * <p>All checks go through {@link VersionReporter#getDataVersion()}, which is resolved
 * from {@code protocol-versions.json} and is always correct per-version.
 */
public final class PacketFormat {
    private final VersionReporter versionReporter;

    public PacketFormat(VersionReporter versionReporter) {
        this.versionReporter = versionReporter;
    }

    // ── LevelParticles ───────────────────────────────────────────────

    /** 1.15+ uses Double for position; 1.12–1.14 use Float. */
    public boolean particlePositionIsDouble() {
        return versionReporter.isAtLeast(Version.V1_15);
    }

    /** 1.19+ uses VarInt for particleId; 1.12–1.18 use Int. */
    public boolean particleIdIsVarInt() {
        return versionReporter.isAtLeast(Version.V1_19);
    }

    /** 1.20.5+ moves particleId to the end of the packet (after amount). */
    public boolean particleIdAtEnd() {
        return versionReporter.isAtLeast(Version.V1_20_6);
    }

    /**
     * 1.21.4+ adds an {@code alwaysShow} boolean after {@code longDistance}.
     * Uses the raw protocol version (not dataVersion) because protocols 768–774
     * are not in protocol-versions.json and would be matched to 767 (1.21),
     * giving the wrong data version.
     */
    public boolean particleHasAlwaysShow() {
        return versionReporter.getProtocolVersion() >= Version.V1_21_4.protocolVersion;
    }

    // ── UseItemOn (block placement) ──────────────────────────────────

    /**
     * 1.19+ sends hand (VarInt) first, then position.
     * 1.13–1.18 send position first, then face/direction, then hand.
     */
    public boolean useItemOnHandFirst() {
        return versionReporter.isAtLeast(Version.V1_19);
    }

    // ── Chat / components ────────────────────────────────────────────

    /** 1.20.6+ serialises chat components as NBT; earlier versions use JSON strings. */
    public boolean chatIsNbt() {
        return versionReporter.isAtLeast(Version.V1_20_6);
    }

    // ── PlayerAction (block dig) ─────────────────────────────────────

    /**
     * The action status field in the PlayerAction packet is VarInt in all
     * supported versions (1.12–26.2), confirmed from PrismarineJS minecraft-data.
     */
    public boolean playerActionStatusIsVarInt() {
        return true;
    }
}
