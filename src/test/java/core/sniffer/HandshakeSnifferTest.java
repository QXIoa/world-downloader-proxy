package core.sniffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HandshakeSniffer}, which parses the handshake packet layout
 * (protocol VarInt, host String, port Short, next-state VarInt) independently
 * of any per-version DataTypeProvider.
 */
class HandshakeSnifferTest {

    /**
     * Build a raw handshake packet: [length VarInt] [packet-id VarInt] [payload...]
     */
    private static byte[] buildPacket(int packetId, int protocolVersion, String host, int port, int nextState) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, protocolVersion);
        writeString(payload, host);
        payload.write((port >> 8) & 0xFF);
        payload.write(port & 0xFF);
        writeVarInt(payload, nextState);

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarInt(packet, packetId);
        byte[] payloadBytes = payload.toByteArray();
        // Prepend length prefix
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        writeVarInt(full, payloadBytes.length + varIntSize(packetId));
        full.writeBytes(packet.toByteArray());
        full.writeBytes(payloadBytes);
        return full.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static int varIntSize(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) return 1;
        if ((value & (0xFFFFFFFF << 14)) == 0) return 2;
        if ((value & (0xFFFFFFFF << 21)) == 0) return 3;
        if ((value & (0xFFFFFFFF << 28)) == 0) return 4;
        return 5;
    }

    @Test
    void parses26_1LoginHandshake() {
        byte[] packet = buildPacket(0, 775, "example.com", 25565, 2);
        HandshakeSniffer.Handshake hs = HandshakeSniffer.sniff(packet);
        assertThat(hs).isNotNull();
        assertThat(hs.getProtocolVersion()).isEqualTo(775);
        assertThat(hs.getHost()).isEqualTo("example.com");
        assertThat(hs.getPort()).isEqualTo(25565);
        assertThat(hs.getNextState()).isEqualTo(2);
        assertThat(hs.isLogin()).isTrue();
        assertThat(hs.isStatus()).isFalse();
    }

    @Test
    void parses26_2StatusHandshake() {
        byte[] packet = buildPacket(0, 776, "localhost", 25565, 1);
        HandshakeSniffer.Handshake hs = HandshakeSniffer.sniff(packet);
        assertThat(hs).isNotNull();
        assertThat(hs.getProtocolVersion()).isEqualTo(776);
        assertThat(hs.getHost()).isEqualTo("localhost");
        assertThat(hs.isStatus()).isTrue();
        assertThat(hs.isLogin()).isFalse();
    }

    @Test
    void returnsNullForIncompleteData() {
        // Only 2 bytes — not enough for a full VarInt
        assertThat(HandshakeSniffer.sniff(new byte[]{0x00, 0x00}, 0, 2)).isNull();
    }

    @Test
    void handlesEmptyHost() {
        byte[] packet = buildPacket(0, 775, "", 25565, 2);
        HandshakeSniffer.Handshake hs = HandshakeSniffer.sniff(packet);
        assertThat(hs).isNotNull();
        assertThat(hs.getHost()).isEmpty();
    }
}
