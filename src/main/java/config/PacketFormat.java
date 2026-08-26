package config;

/**
 * Centralised knowledge about how packet field layouts differ between Minecraft versions.
 * Every "does this version use X or Y?" question lives here instead of being scattered
 * across the codebase as {@code if (isAtLeast(V1_19))} checks.
 *
 * <p>For the supported versions (26.x) all pre-26 format variants are gone, so most methods
 * here now return a constant. The class is kept (rather than inlined) as the single place to
 * update if a future Minecraft version changes one of these formats - add a real version
 * check here and callers pick it up automatically (see docs/LEGACY_VERSION_REMOVAL_PLAN.md
 * section 3.1).
 */
public final class PacketFormat {
    private final VersionReporter versionReporter;

    public PacketFormat(VersionReporter versionReporter) {
        this.versionReporter = versionReporter;
    }

    // ── LevelParticles ───────────────────────────────────────────────

    /** 26.x uses Double for particle position. */
    public boolean particlePositionIsDouble() {
        return true;
    }

    /** 26.x uses VarInt for particleId. */
    public boolean particleIdIsVarInt() {
        return true;
    }

    /** 26.x has particleId at the end of the packet (after amount). */
    public boolean particleIdAtEnd() {
        return true;
    }

    /**
     * 1.21.4+ adds an {@code alwaysShow} boolean after {@code longDistance}.
     * All supported versions (26.x) include this field.
     */
    public boolean particleHasAlwaysShow() {
        return true;
    }

    // ── UseItemOn (block placement) ──────────────────────────────────

    /** 26.x sends hand (VarInt) first, then position. */
    public boolean useItemOnHandFirst() {
        return true;
    }

    // ── Chat / components ────────────────────────────────────────────

    /** 26.x serialises chat components as NBT. */
    public boolean chatIsNbt() {
        return true;
    }

    // ── PlayerAction (block dig) ─────────────────────────────────────

    /**
     * The action status field in the PlayerAction packet is VarInt in all
     * supported versions.
     */
    public boolean playerActionStatusIsVarInt() {
        return true;
    }
}
