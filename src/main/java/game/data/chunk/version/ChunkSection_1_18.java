package game.data.chunk.version;

import game.data.chunk.Chunk;
import game.data.chunk.palette.State;
import game.data.chunk.palette.DirectPalette;
import game.data.chunk.palette.Palette;
import game.data.chunk.palette.PaletteTransformer;
import game.data.chunk.palette.PaletteType;
import game.data.chunk.palette.SingleValuePalette;
import game.data.coordinates.Coordinate3D;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import packets.builder.PacketBuilder;
import se.llbit.nbt.ByteArrayTag;
import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.LongArrayTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.Tag;

public class ChunkSection_1_18 extends ChunkSection_1_16 {
    long[] biomes;
    Palette biomePalette;
    int blockCount = -1;

    public ChunkSection_1_18(byte y, Palette palette, Chunk chunk) {
        super(y, palette, chunk);
    }

    public ChunkSection_1_18(int sectionY, Tag nbt, Chunk chunk) {
        super(sectionY, nbt, chunk);
    }

    @Override
    protected void parse(Tag nbt) {
        this.setBlockLight(nbt.get("BlockLight").byteArray());
        this.setSkyLight(nbt.get("SkyLight").byteArray());

        CompoundTag blockStates = nbt.get("block_states").asCompound();
        this.setBlocks(blockStates.get("data").longArray());
        this.palette = new Palette(getDataVersion(), blockStates.get("palette").asList());

        CompoundTag biomes = nbt.get("biomes").asCompound();
        this.biomePalette = Palette.biomes(getDataVersion(), biomes.get("palette").asList());
        this.biomes = biomes.get("data").longArray();

        Tag blockCount = nbt.get("block_count");
        if (!blockCount.isError()) {
            this.blockCount = blockCount.intValue();
        }
    }

    @Override
    public void write(PacketBuilder packet) {
        if (blockCount < 0) { blockCount = palette.isEmpty() ? 0 : 4096; }

        packet.writeShort(blockCount);
        palette.write(packet);

        packet.writeVarInt(blocks.length);
        packet.writeLongArray(blocks);

        biomePalette.write(packet);
        packet.writeVarInt(biomes.length);
        packet.writeLongArray(biomes);
    }

    public void setBiomes(long[] biomes) {
        this.biomes = biomes;
    }

    public void setBiomePalette(Palette biomePalette) {
        this.biomePalette = biomePalette;
        this.biomePalette.biomePalette();
    }

    public void setBlockPalette(Palette blockPalette) {
        this.palette = blockPalette;
    }

    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();

        tag.add("biomes", getPalettedCompound(biomePalette, Tag.TAG_STRING, biomes, PaletteType.BIOMES));
        tag.add("block_states", getPalettedCompound(palette, Tag.TAG_COMPOUND, blocks, PaletteType.BLOCKS));

        tag.add("Y", new ByteTag(y));
        if (blockLight != null && blockLight.length > 0) {
            tag.add("BlockLight", new ByteArrayTag(blockLight));
        }
        if (skyLight != null && skyLight.length > 0) {
            tag.add("SkyLight", new ByteArrayTag(skyLight));
        }
        if (blockCount > 0) {
            tag.add("block_count", new IntTag(blockCount));
        }

        return tag;
    }

    private CompoundTag getPalettedCompound(Palette palette, int tagType, long[] data, PaletteType type) {
        CompoundTag tag = new CompoundTag();

        // If the palette is empty (usually meaning no blocks in a section), set it to a palette
        // with just air in it.
        if (palette == null || (data.length == 0 && !(palette instanceof SingleValuePalette))) {
            palette = new SingleValuePalette(0);
            if (type == PaletteType.BIOMES) {
                palette.biomePalette();
            }
        }

        // If we have a direct palette, we need to convert it to a proper palette since the world
        // format doesn't allow direct palettes (I think).
        if (palette instanceof DirectPalette directPalette) {
            PaletteTransformer transformer = new PaletteTransformer(getLocationEncoder(), directPalette);
            long[] newData = transformer.transform(data);

            if (newData != data) {
                data = newData;
                palette = transformer.getNewPalette();
            }
        }

        List<SpecificTag> paletteItems = palette.toNbt();
        if (paletteItems.isEmpty()) {
            // this shouldn't ever happen
            System.err.println("Empty palette @ " + getY() + " :: " + palette);
        } else {
            tag.add("palette", new ListTag(tagType, paletteItems));
        }
        if (data != null && data.length > 0) {
            tag.add("data", new LongArrayTag(data));
        }

        return tag;
    }
    
    @Override
    public synchronized void setBlockAt(Coordinate3D coords, int blockStateId) {
        int index = palette.getIndexFor(this, blockStateId);

        if (palette instanceof SingleValuePalette svp) {
            if (blocks == null || blocks.length == 0) {
                resetBlocks();
            }

            this.palette = svp.asNormalPalette();
        }

        // Some servers seem to send a palette with a bits-per-block that doesn't match the number of provided longs
        // when the section is empty. In this case we assume the section was empty before and remake the array.
        resizeBlocksIfRequired(palette.getBitsPerBlock());

        getLocationEncoder().setTo(
                coords.getX(), coords.getY(), coords.getZ(),
                palette.getBitsPerBlock()
        );
        getLocationEncoder().write(blocks, index);
    }

    @Override
    public String toString() {
        return "ChunkSection{" +
            "y=" + y +
            ", biomePalette=" + biomePalette +
            ", biomes=" + Arrays.toString(biomes) +
            ", blocks[" + blocks.length + "]" +
            ", palette=" + palette +
            ", blockLight[" + blockLight.length + "]" +
            ", skyLight[" + skyLight.length + "]" +
            '}';
    }

    /**
     * Vanilla client doesn't store the block count but we can speed things up a bit by saving it
     */
    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    /**
     * Read the biome name at the given block-local coordinates. Biomes in 1.18+ are stored in a
     * 4×4×4 grid per section (64 entries), packed in the same long[] bit-array format as blocks.
     *
     * @param x  block-local X (0–15)
     * @param y  block-local Y within the section (0–15)
     * @param z  block-local Z (0–15)
     * @return the biome resource location (e.g. {@code minecraft:plains}), or {@code null} if
     *         the section or biome palette is missing
     */
    public String getBiomeAt(int x, int y, int z) {
        if (biomePalette == null || biomes == null || biomes.length == 0) {
            return null;
        }

        // biome grid is 4×4×4 within a 16×16×16 section
        int biomeX = x / 4;
        int biomeY = y / 4;
        int biomeZ = z / 4;

        int bitsPerEntry = biomePalette.getBitsPerBlock();
        int paletteIndex;
        if (bitsPerEntry == 0) {
            paletteIndex = 0;
        } else {
            paletteIndex = getLocationEncoder().setTo(biomeX, biomeY, biomeZ, bitsPerEntry).fetch(biomes);
        }

        int biomeId = biomePalette.stateFromId(paletteIndex);
        State state = biomePalette.lookupState(biomeId);
        return state != null ? state.toString() : null;
    }
}
