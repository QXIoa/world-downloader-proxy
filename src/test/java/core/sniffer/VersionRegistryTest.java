package core.sniffer;

import static org.assertj.core.api.Assertions.assertThat;

import core.interfaces.VersionModule;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VersionRegistry}, which maps protocol version numbers to
 * {@link VersionModule} instances discovered via Java SPI.
 */
class VersionRegistryTest {

    @Test
    void resolvesProtocol775ToModule() {
        VersionRegistry registry = VersionRegistry.getInstance();
        VersionModule module = registry.getModule(775);
        assertThat(module).isNotNull();
        assertThat(module.minSupportedProtocolVersion()).isLessThanOrEqualTo(775);
    }

    @Test
    void resolvesProtocol776ToModule() {
        VersionRegistry registry = VersionRegistry.getInstance();
        VersionModule module = registry.getModule(776);
        assertThat(module).isNotNull();
    }

    @Test
    void protocol775And776ResolveToDifferentModules() {
        VersionRegistry registry = VersionRegistry.getInstance();
        VersionModule m775 = registry.getModule(775);
        VersionModule m776 = registry.getModule(776);
        // After the WET split, 26.1 and 26.2 are served by independent modules
        assertThat(m775).isNotSameAs(m776);
        assertThat(m775.defaultProtocolVersion()).isEqualTo(775);
        assertThat(m776.defaultProtocolVersion()).isEqualTo(776);
    }

    @Test
    void isSupportedFor26_1() {
        assertThat(VersionRegistry.getInstance().isSupported(775)).isTrue();
    }

    @Test
    void isSupportedFor26_2() {
        assertThat(VersionRegistry.getInstance().isSupported(776)).isTrue();
    }

    @Test
    void isNotSupportedForOldProtocol() {
        // 769 is 1.21.4, below the minimum supported (775 = 26.1)
        assertThat(VersionRegistry.getInstance().isSupported(769)).isFalse();
    }

    @Test
    void defaultModuleReturnedForUnknownProtocol() {
        // A future protocol not yet registered should fall back to the default module
        VersionModule module = VersionRegistry.getInstance().getModule(99999);
        assertThat(module).isNotNull();
    }
}
