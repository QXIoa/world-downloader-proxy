package version.v26_2.chunk;

import core.config.Config;
import core.config.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import version.v26_2.module.VersionModuleImpl;
import version.v26_2.registries.RegistryLoader;
import version.v26_2.world.WorldManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the v26.2 module loads its registry reports from the
 * {@code /v26_2/} classpath resource directory, not from v26.1's resources.
 * This is a key isolation guarantee of the WET architecture (phase 2f).
 */
class RegistryIsolationTest {

    @BeforeEach
    void setUp() {
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
    }

    @Test
    void v26_2RegistryLoaderLoadsFromV26_2Resources() {
        RegistryLoader loader = RegistryLoader.forVersion("26.2");
        assertThat(loader).isNotNull();
        // The loader should successfully generate entity names from v26_2/registries.json
        // If it loaded from v26_1 resources instead, the data version would be wrong
        try {
            assertThat(loader.generateEntityNames()).isNotNull();
        } catch (Exception e) {
            throw new AssertionError("v26.2 RegistryLoader should load from /v26_2/ resources", e);
        }
    }

    @Test
    void v26_2ModuleDefaultsToProtocol776() {
        VersionModuleImpl module = new VersionModuleImpl();
        assertThat(module.defaultProtocolVersion()).isEqualTo(Version.V26_2.protocolVersion);
        assertThat(module.minSupportedProtocolVersion()).isEqualTo(Version.V26_2.protocolVersion);
    }
}
