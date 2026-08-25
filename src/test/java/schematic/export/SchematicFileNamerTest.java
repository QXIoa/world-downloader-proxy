package schematic.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SchematicFileNamerTest {
    private static final Instant FIXED_INSTANT =
        ZonedDateTime.of(2026, 8, 24, 21, 15, 3, 0, ZoneId.systemDefault()).toInstant();

    @Test
    void includesTimestampAndServerAddress() {
        String fileName = new SchematicFileNamer().buildFileName(FIXED_INSTANT, "play.example.com");

        assertThat(fileName).isEqualTo("2026-08-24_21-15-03_play.example.com.schem");
    }

    @Test
    void sanitizesCharactersThatAreNotSafeInFileNames() {
        String fileName = new SchematicFileNamer().buildFileName(FIXED_INSTANT, "ns3101294.ip-54-36-175.eu:25566");

        assertThat(fileName).isEqualTo("2026-08-24_21-15-03_ns3101294.ip-54-36-175.eu_25566.schem");
    }

    @Test
    void fallsBackToPlaceholderWhenServerAddressIsMissing() {
        assertThat(new SchematicFileNamer().buildFileName(FIXED_INSTANT, null))
            .isEqualTo("2026-08-24_21-15-03_unknown-server.schem");
        assertThat(new SchematicFileNamer().buildFileName(FIXED_INSTANT, "  "))
            .isEqualTo("2026-08-24_21-15-03_unknown-server.schem");
    }
}
