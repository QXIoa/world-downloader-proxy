package schematic.export;

import static org.assertj.core.api.Assertions.assertThat;

import config.Config;
import game.data.chunk.palette.BlockState;
import game.data.coordinates.Coordinate3D;
import game.data.dimension.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schematic.BoundingBox;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntArrayTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;
import util.NbtUtil;

class SpongeSchematicExporterTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Config.setInstance(new Config());
    }

    private static CompoundTag propertiesOf(Map<String, String> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach((key, value) -> tag.add(key, new StringTag(value)));
        return tag;
    }

    /**
     * A fake reader that returns a constant block and a constant biome for every coordinate.
     */
    private static BlockRegionReader uniformReader(String blockName, String biomeName) {
        return new BlockRegionReader() {
            @Override
            public BlockState blockAt(Coordinate3D coordinate) {
                return new BlockState(blockName, 1, new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return biomeName;
            }
        };
    }

    @Test
    void exportsAUniformRegionAsASingleValuePaletteEntry() throws IOException {
        BlockRegionReader reader = uniformReader("minecraft:stone", "minecraft:plains");
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 0, 1));
        Path target = tempDir.resolve("out.schem");
        exporter.export(box, Dimension.OVERWORLD, target);

        CompoundTag schematic = readSchematic(target);

        // --- Required Schematic fields ---
        assertThat(schematic.get("Version").intValue()).isEqualTo(3);
        assertThat(schematic.get("DataVersion").intValue()).isEqualTo(Config.getDataVersion());
        assertThat(schematic.get("Width").shortValue()).isEqualTo((short) 2);
        assertThat(schematic.get("Height").shortValue()).isEqualTo((short) 1);
        assertThat(schematic.get("Length").shortValue()).isEqualTo((short) 2);

        // --- Blocks ---
        CompoundTag blocks = schematic.get("Blocks").asCompound();
        CompoundTag blockPalette = blocks.get("Palette").asCompound();
        assertThat(blockPalette.get("minecraft:stone").intValue()).isEqualTo(0);

        byte[] blockData = blocks.get("Data").byteArray();
        // 2x1x2 region, single palette entry -> every varint-encoded byte is index 0
        assertThat(blockData).containsOnly(0);
        assertThat(blockData).hasSize((int) box.volume());

        // BlockEntities is an empty list of compounds (spec: Object[] = List of Compound)
        assertThat(blocks.get("BlockEntities").isList()).isTrue();

        // --- Offset (optional but required by FAWE; [0,0,0] = paste at player position) ---
        int[] offset = ((IntArrayTag) schematic.get("Offset")).getData();
        assertThat(offset).containsExactly(0, 0, 0);

        // --- Metadata (optional, spec-compliant) ---
        CompoundTag metadata = schematic.get("Metadata").asCompound();
        assertThat(metadata.get("Name").stringValue()).isEqualTo("world-downloader-proxy export");
        assertThat(metadata.get("Author").stringValue()).isEqualTo("world-downloader-proxy");
        assertThat(metadata.get("Date").longValue()).isGreaterThan(0);
        // RequiredMods must be string[] (List of String), not List of Compound
        Tag requiredMods = metadata.get("RequiredMods");
        assertThat(requiredMods.isList()).isTrue();
        assertThat(((ListTag) requiredMods).getType()).isEqualTo(Tag.TAG_STRING);

        // --- Biomes (optional, included for 1:1 spec compliance) ---
        CompoundTag biomes = schematic.get("Biomes").asCompound();
        CompoundTag biomePalette = biomes.get("Palette").asCompound();
        assertThat(biomePalette.get("minecraft:plains").intValue()).isEqualTo(0);
        byte[] biomeData = biomes.get("Data").byteArray();
        assertThat(biomeData).hasSize((int) box.volume());

        // --- Entities (optional, empty list) ---
        Tag entities = schematic.get("Entities");
        assertThat(entities.isList()).isTrue();
        assertThat(((ListTag) entities).getType()).isEqualTo(Tag.TAG_COMPOUND);
    }

    @Test
    void treatsMissingBlocksAsAirAndEncodesPropertiesInThePaletteKey() throws IOException {
        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public BlockState blockAt(Coordinate3D coord) {
                if (coord.getX() == 0) {
                    return new BlockState("minecraft:oak_stairs", 2,
                        propertiesOf(Map.of("facing", "north", "half", "bottom")));
                }
                return null; // unloaded -> should be treated as air
            }

            @Override
            public String biomeAt(Coordinate3D coord) {
                return "minecraft:desert";
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 0, 0));
        Path target = tempDir.resolve("stairs.schem");
        exporter.export(box, Dimension.OVERWORLD, target);

        CompoundTag schematic = readSchematic(target);
        CompoundTag blockPalette = schematic.get("Blocks").asCompound().get("Palette").asCompound();

        assertThat(blockPalette.get("minecraft:oak_stairs[facing=north,half=bottom]").intValue()).isEqualTo(0);
        assertThat(blockPalette.get("minecraft:air").intValue()).isEqualTo(1);

        // Biome palette should contain the desert biome
        CompoundTag biomePalette = schematic.get("Biomes").asCompound().get("Palette").asCompound();
        assertThat(biomePalette.get("minecraft:desert").intValue()).isEqualTo(0);
    }

    @Test
    void missingBiomesDefaultToPlains() throws IOException {
        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public BlockState blockAt(Coordinate3D coordinate) {
                return new BlockState("minecraft:stone", 1, new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return null; // no biome data -> should default to minecraft:plains
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(0, 0, 0));
        Path target = tempDir.resolve("default_biome.schem");
        exporter.export(box, Dimension.OVERWORLD, target);

        CompoundTag biomePalette = readSchematic(target).get("Biomes").asCompound().get("Palette").asCompound();
        assertThat(biomePalette.get("minecraft:plains").intValue()).isEqualTo(0);
    }

    private CompoundTag readSchematic(Path file) throws IOException {
        Tag root = NbtUtil.read(file.toFile());
        return root.asCompound().get("Schematic").asCompound();
    }
}
