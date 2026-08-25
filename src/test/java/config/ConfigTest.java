package config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import game.UnsupportedMinecraftVersionException;
import game.data.WorldManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the minimum-supported-version guard in {@link Config#setProtocolVersion}, which rejects clients
 * older than 26.1 outright instead of silently matching them to the oldest known protocol entry (see
 * docs/LEGACY_VERSION_REMOVAL_PLAN.md, section 4.7).
 */
class ConfigTest {

    @BeforeEach
    void setUp() {
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
    }

    @Test
    void rejectsProtocolVersionsOlderThan26_1() {
        assertThrows(UnsupportedMinecraftVersionException.class,
            () -> Config.setProtocolVersion(Version.V1_21_4.protocolVersion));
    }

    @Test
    void rejectsVeryOldProtocolVersions() {
        assertThrows(UnsupportedMinecraftVersionException.class,
            () -> Config.setProtocolVersion(Version.V1_12.protocolVersion));
    }

    @Test
    void accepts26_1() {
        Config.setProtocolVersion(Version.V26_1.protocolVersion);
        assertThat(Config.getProtocolVersion()).isEqualTo(Version.V26_1.protocolVersion);
    }

    @Test
    void accepts26_2() {
        Config.setProtocolVersion(Version.V26_2.protocolVersion);
        assertThat(Config.getProtocolVersion()).isEqualTo(Version.V26_2.protocolVersion);
    }
}
