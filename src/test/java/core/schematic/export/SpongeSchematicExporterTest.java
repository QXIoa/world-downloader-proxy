package core.schematic.export;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.interfaces.IBlockState;
import core.interfaces.INbtIO;
import core.interfaces.TestDimension;
import core.schematic.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.llbit.nbt.*;
import version.v26_1.module.VersionModuleImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpongeSchematicExporterTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
    }

    /**
     * A minimal fake {@link IBlockState} for testing — the Sponge format only needs
     * the name and properties (as NBT), both of which are version-independent.
     */
    private static final class FakeBlockState implements IBlockState {
        private final String name;
        private final CompoundTag properties;

        FakeBlockState(String name, CompoundTag properties) {
            this.name = name;
            this.properties = properties;
        }

        @Override
        public String getName() { return name; }

        @Override
        public CompoundTag getProperties() { return properties; }
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
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState(blockName, new CompoundTag());
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
        exporter.export(box, TestDimension.overworld(), target);

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
            public IBlockState blockAt(Coordinate3D coord) {
                if (coord.getX() == 0) {
                    return new FakeBlockState("minecraft:oak_stairs",
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
        exporter.export(box, TestDimension.overworld(), target);

        CompoundTag schematic = readSchematic(target);
        CompoundTag blockPalette = schematic.get("Blocks").asCompound().get("Palette").asCompound();

        assertThat(blockPalette.get("minecraft:oak_stairs[facing=north,half=bottom]").intValue()).isEqualTo(0);
        assertThat(blockPalette.get("minecraft:air").intValue()).isEqualTo(1);

        // Biome palette should contain the desert biome
        CompoundTag biomePalette = schematic.get("Biomes").asCompound().get("Palette").asCompound();
        assertThat(biomePalette.get("minecraft:desert").intValue()).isEqualTo(0);
    }

    @Test
    void exportsBlockEntityWithRelativePositionAndSeparatedData() throws IOException {
        Coordinate3D position = new Coordinate3D(10, 64, -5);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.add("id", new StringTag("minecraft:banner"));
        blockEntity.add("x", new IntTag(10));
        blockEntity.add("y", new IntTag(64));
        blockEntity.add("z", new IntTag(-5));
        blockEntity.add("CustomName", new StringTag("Banner"));
        CompoundTag pattern = new CompoundTag();
        pattern.add("pattern", new StringTag("minecraft:stripe_top"));
        blockEntity.add("patterns", new ListTag(Tag.TAG_COMPOUND, List.of(pattern)));

        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:white_banner", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }

            @Override
            public SpecificTag blockEntityAt(Coordinate3D coordinate) {
                return coordinate.equals(position) ? blockEntity : null;
            }
        };

        Path target = tempDir.resolve("block-entity.schem");
        new SpongeSchematicExporter(reader).export(new BoundingBox(position, position), TestDimension.overworld(), target);

        ListTag blockEntities = (ListTag) readSchematic(target).get("Blocks").asCompound().get("BlockEntities");
        assertThat(blockEntities.size()).isEqualTo(1);
        CompoundTag encoded = blockEntities.get(0).asCompound();
        assertThat(((IntArrayTag) encoded.get("Pos")).getData()).containsExactly(0, 0, 0);
        assertThat(encoded.get("Id").stringValue()).isEqualTo("minecraft:banner");
        assertThat(encoded.get("Data").asCompound().get("CustomName").stringValue()).isEqualTo("Banner");
        assertThat(encoded.get("Data").asCompound().get("patterns").asList().size()).isEqualTo(1);
        assertThat(encoded.get("Data").asCompound().get("x").isError()).isTrue();
    }

    @Test
    void exportsEntityDataWithPositionProvidedByRegionReader() throws IOException {
        CompoundTag entity = new CompoundTag();
        entity.add("id", new StringTag("minecraft:armor_stand"));
        entity.add("Pos", new ListTag(Tag.TAG_DOUBLE, List.of(
            new DoubleTag(0.5), new DoubleTag(1.0), new DoubleTag(0.5)
        )));
        entity.add("Marker", new ByteTag(1));

        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:air", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }

            @Override
            public List<SpecificTag> entitiesIn(BoundingBox box) {
                return List.of(entity);
            }
        };

        Path target = tempDir.resolve("entity.schem");
        BoundingBox box = new BoundingBox(new Coordinate3D(10, 64, -5), new Coordinate3D(11, 65, -4));
        new SpongeSchematicExporter(reader).export(box, TestDimension.overworld(), target);

        ListTag entities = (ListTag) readSchematic(target).get("Entities");
        assertThat(entities.size()).isEqualTo(1);
        CompoundTag encoded = entities.get(0).asCompound();
        assertThat(encoded.get("Id").stringValue()).isEqualTo("minecraft:armor_stand");
        assertThat(encoded.get("Pos").asList().get(0).doubleValue()).isEqualTo(0.5);
        assertThat(encoded.get("Data").asCompound().get("Marker").byteValue()).isEqualTo((byte) 1);
        assertThat(encoded.get("Data").asCompound().get("id").isError()).isTrue();
    }

    @Test
    void skipsErrorTagsWhenCopyingEntityNbt() throws IOException {
        // Regression: entity NBT can contain ErrorTag entries (from failed .get() lookups
        // that were accidentally stored). deepCopy/copyWithout must skip them instead of
        // throwing "Cannot write an error tag to NBT stream".
        CompoundTag entity = new CompoundTag();
        entity.add("id", new StringTag("minecraft:armor_stand"));
        entity.add("Pos", new ListTag(Tag.TAG_DOUBLE, List.of(
            new DoubleTag(0.5), new DoubleTag(1.0), new DoubleTag(0.5)
        )));
        entity.add("Marker", new ByteTag(1));
        // Simulate a corrupted entry that resolves to ErrorTag
        entity.add("BadField", (SpecificTag) entity.get("nonexistent"));

        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:air", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }

            @Override
            public List<SpecificTag> entitiesIn(BoundingBox box) {
                return List.of(entity);
            }
        };

        Path target = tempDir.resolve("error-tag.schem");
        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 1, 1));
        new SpongeSchematicExporter(reader).export(box, TestDimension.overworld(), target);

        ListTag entities = (ListTag) readSchematic(target).get("Entities");
        assertThat(entities.size()).isEqualTo(1);
        CompoundTag encoded = entities.get(0).asCompound();
        assertThat(encoded.get("Id").stringValue()).isEqualTo("minecraft:armor_stand");
        assertThat(encoded.get("Data").asCompound().get("Marker").byteValue()).isEqualTo((byte) 1);
        // BadField (ErrorTag) should have been skipped, not cause a crash
        assertThat(encoded.get("Data").asCompound().get("BadField").isError()).isTrue();
    }

    @Test
    void missingBiomesDefaultToPlains() throws IOException {
        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:stone", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return null; // no biome data -> should default to minecraft:plains
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(0, 0, 0));
        Path target = tempDir.resolve("default_biome.schem");
        exporter.export(box, TestDimension.overworld(), target);

        CompoundTag biomePalette = readSchematic(target).get("Biomes").asCompound().get("Palette").asCompound();
        assertThat(biomePalette.get("minecraft:plains").intValue()).isEqualTo(0);
    }

    @Test
    void reportsMissingBlocksWhenChunkIsNotLoaded() throws IOException {
        // Half the blocks are in an "unloaded chunk" (blockAt returns null) — the
        // exporter should count them as missingBlocks and treat them as air.
        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                if (coordinate.getX() == 0) {
                    return null; // unloaded
                }
                return new FakeBlockState("minecraft:stone", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 0, 0));
        Path target = tempDir.resolve("missing_blocks.schem");
        ExportResult result = exporter.export(box, TestDimension.overworld(), target);

        assertThat(result.totalBlocks()).isEqualTo(2);
        assertThat(result.missingBlocks()).isEqualTo(1);
        assertThat(result.missingHeads()).isEqualTo(0);
        assertThat(result.exportedBlockEntities()).isEqualTo(0);
    }

    @Test
    void reportsMissingHeadsWhenHeadBlockHasNoBlockEntityData() throws IOException {
        // A player_head block exists but has no block entity data — the exporter
        // should count it as a missingHead (its profile/skin is lost).
        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:player_head", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }

            @Override
            public SpecificTag blockEntityAt(Coordinate3D coordinate) {
                return null; // no block entity data -> head has no profile
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(0, 0, 0));
        Path target = tempDir.resolve("missing_head.schem");
        ExportResult result = exporter.export(box, TestDimension.overworld(), target);

        assertThat(result.missingBlocks()).isEqualTo(0);
        assertThat(result.missingHeads()).isEqualTo(1);
        assertThat(result.exportedBlockEntities()).isEqualTo(0);
    }

    @Test
    void doesNotCountHeadAsMissingWhenBlockEntityIsPresent() throws IOException {
        CompoundTag headEntity = new CompoundTag();
        headEntity.add("id", new StringTag("minecraft:skull"));
        headEntity.add("x", new IntTag(0));
        headEntity.add("y", new IntTag(0));
        headEntity.add("z", new IntTag(0));

        BlockRegionReader reader = new BlockRegionReader() {
            @Override
            public IBlockState blockAt(Coordinate3D coordinate) {
                return new FakeBlockState("minecraft:player_head", new CompoundTag());
            }

            @Override
            public String biomeAt(Coordinate3D coordinate) {
                return "minecraft:plains";
            }

            @Override
            public SpecificTag blockEntityAt(Coordinate3D coordinate) {
                return headEntity;
            }
        };
        SpongeSchematicExporter exporter = new SpongeSchematicExporter(reader);

        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(0, 0, 0));
        Path target = tempDir.resolve("head_with_entity.schem");
        ExportResult result = exporter.export(box, TestDimension.overworld(), target);

        assertThat(result.missingHeads()).isEqualTo(0);
        assertThat(result.exportedBlockEntities()).isEqualTo(1);
    }

    private CompoundTag readSchematic(Path file) throws IOException {
        INbtIO nbtIO = Config.getVersionModule().getNbtIO();
        Tag root = (Tag) nbtIO.read(java.nio.file.Files.newInputStream(file));
        return root.asCompound().get("Schematic").asCompound();
    }
}
