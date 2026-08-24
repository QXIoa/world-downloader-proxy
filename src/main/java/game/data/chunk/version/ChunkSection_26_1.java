package game.data.chunk.version;

import game.data.chunk.Chunk;
import game.data.chunk.palette.Palette;
import packets.builder.PacketBuilder;
import se.llbit.nbt.Tag;

/**
 * As of protocol 770 (1.21.5), chunk sections gained a "fluid count" short right after the block count, and the
 * paletted container data arrays (blocks and biomes) are no longer length-prefixed on the wire - the length is
 * calculated from bits-per-entry instead. See Chunk_26_1#readChunkColumn for the read side of this.
 */
public class ChunkSection_26_1 extends ChunkSection_1_18 {
    public ChunkSection_26_1(byte y, Palette palette, Chunk chunk) {
        super(y, palette, chunk);
    }

    public ChunkSection_26_1(int sectionY, Tag nbt, Chunk chunk) {
        super(sectionY, nbt, chunk);
    }

    @Override
    public void write(PacketBuilder packet) {
        if (blockCount < 0) { blockCount = palette.isEmpty() ? 0 : 4096; }

        packet.writeShort(blockCount);
        packet.writeShort(0); // fluid count - not tracked separately, 0 is a safe placeholder

        writePalettedContainer(packet, palette, blocks, true);
        writePalettedContainer(packet, biomePalette, biomes, false);
    }

    /**
     * In 26.1+ the data array length is not sent on the wire; it is derived from bits-per-entry.
     * A 1-entry palette with an empty data array (common for sections that contain only air) must be
     * written as a single-value palette (bitsPerBlock = 0, no data array), otherwise the reader
     * would expect longsRequired(bitsPerBlock) longs that were never written.
     */
    private void writePalettedContainer(PacketBuilder packet, Palette pal, long[] data, boolean isBlocks) {
        if (pal.size() == 1 && (data == null || data.length == 0)) {
            packet.writeByte((byte) 0);
            packet.writeVarInt(pal.stateFromId(0));
            return;
        }

        int expectedLen = isBlocks
            ? ChunkSection_1_18.longsRequired(pal.getBitsPerBlock())
            : ChunkSection_1_18.longsRequiredBiomes(pal.getBitsPerBlock());

        long[] dataToWrite = data;
        if (data == null || data.length != expectedLen) {
            dataToWrite = new long[expectedLen];
            if (data != null) {
                System.arraycopy(data, 0, dataToWrite, 0, Math.min(data.length, expectedLen));
            }
        }

        pal.write(packet);
        packet.writeLongArray(dataToWrite);
    }
}
