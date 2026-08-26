package core.schematic.export;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Encodes a list of non-negative palette indices as a byte array of protocol-style VarInts - the
 * encoding the Sponge Schematic format's {@code varint[]} data type maps to (see
 * https://wiki.vg/VarInt_And_VarLong, the same algorithm used elsewhere in this project by
 * {@code version.v26_1.packets.builder.PacketBuilder.writeVarInt}). Kept separate from
 * {@link SpongeSchematicExporter} so the bit-packing logic can be tested on its own with plain
 * lists of integers, without touching NBT.
 */
final class VarIntByteArray {
    private VarIntByteArray() {
    }

    static byte[] pack(List<Integer> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(values.size());
        for (int value : values) {
            writeVarInt(out, value);
        }
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        do {
            int b = value & 0b0111_1111;
            value >>>= 7;
            if (value != 0) {
                b |= 0b1000_0000;
            }
            out.write(b);
        } while (value != 0);
    }
}
