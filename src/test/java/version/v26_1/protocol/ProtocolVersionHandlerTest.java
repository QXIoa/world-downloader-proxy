package version.v26_1.protocol;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtocolVersionHandlerTest {

    @Test
    void bestMatch() {
        ProtocolVersionHandler pvh = ProtocolVersionHandler.getInstance();

        Map<Integer, String> versions = new HashMap<>();
        versions.put(775, "26.1");
        versions.put(776, "26.2");

        versions.forEach((k, v) -> {
            assertThat(pvh.getProtocolByProtocolVersion(k).getVersion()).isEqualTo(v);
        });
    }
}