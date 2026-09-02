package core.schematic.export;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.interfaces.IBlockState;
import core.interfaces.IDimension;
import core.interfaces.INbtIO;
import core.schematic.BoundingBox;
import se.llbit.nbt.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Encodes a {@link BoundingBox} to the Sponge Schematic Format, version 3
 * (https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md).
 *
 * <p>The output includes <b>all</b> fields defined by the specification — both required and
 * optional — so that it is accepted by strict readers such as FastAsyncWorldEdit's
 * {@code FastSchematicReaderV3}, which NPEs on missing optional fields like {@code Offset}.
 *
 * <p>Block entities and entities are copied from the loaded chunks when they fall inside the
 * selection. Biomes are read from the loaded chunk data when available (1.18+); if a biome is
 * missing for a coordinate, the default {@code minecraft:plains} is used so the
 * {@code Biomes.Data} array always has the required {@code Width * Height * Length} entries.
 */
public class SpongeSchematicExporter implements SchematicExporter {
    private static final int FORMAT_VERSION = 3;
    private static final String AIR = "minecraft:air";
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private final BlockRegionReader blockRegionReader;

    public SpongeSchematicExporter(BlockRegionReader blockRegionReader) {
        this.blockRegionReader = blockRegionReader;
    }

    public BlockRegionReader getBlockRegionReader() {
        return blockRegionReader;
    }

    @Override
    public ExportResult export(BoundingBox box, IDimension dimension, Path targetFile) throws IOException {
        long totalBlocks = box.volume();

        // --- Blocks ---
        Map<String, Integer> blockPaletteIndices = new LinkedHashMap<>();
        BlockEncodingStats blockStats = new BlockEncodingStats();
        byte[] blockData = encodeBlocks(box, blockPaletteIndices, blockStats);

        CompoundTag blocks = new CompoundTag();
        blocks.add("Palette", buildPaletteTag(blockPaletteIndices));
        blocks.add("Data", new ByteArrayTag(blockData));
        List<SpecificTag> blockEntityTags = encodeBlockEntities(box, blockStats);
        blocks.add("BlockEntities", new ListTag(Tag.TAG_COMPOUND, blockEntityTags));

        // --- Biomes ---
        Map<String, Integer> biomePaletteIndices = new LinkedHashMap<>();
        byte[] biomeData = encodeBiomes(box, biomePaletteIndices);

        CompoundTag biomes = new CompoundTag();
        biomes.add("Palette", buildPaletteTag(biomePaletteIndices));
        biomes.add("Data", new ByteArrayTag(biomeData));

        // --- Schematic root ---
        CompoundTag schematic = new CompoundTag();
        // Required fields
        schematic.add("Version", new IntTag(FORMAT_VERSION));
        schematic.add("DataVersion", new IntTag(Config.getDataVersion()));
        schematic.add("Width", new ShortTag((short) box.sizeX()));
        schematic.add("Height", new ShortTag((short) box.sizeY()));
        schematic.add("Length", new ShortTag((short) box.sizeZ()));
        // Optional fields (included for 1:1 spec compliance and FAWE compatibility)
        // Offset is the relative offset from the paster's position - [0,0,0] means the schematic
        // pastes at the player's position. Using the selection's world coordinates here would
        // cause //paste to place blocks thousands of blocks away from the player.
        schematic.add("Offset", new IntArrayTag(new int[] { 0, 0, 0 }));
        schematic.add("Metadata", buildMetadataTag());
        schematic.add("Blocks", blocks);
        schematic.add("Biomes", biomes);
        schematic.add("Entities", new ListTag(Tag.TAG_COMPOUND, encodeEntities(box)));

        CompoundTag root = new CompoundTag();
        root.add("Schematic", schematic);

        Path absoluteTarget = targetFile.toAbsolutePath();
        if (absoluteTarget.getParent() != null) {
            Files.createDirectories(absoluteTarget.getParent());
        }
        INbtIO nbtIO = Config.getVersionModule().getNbtIO();
        nbtIO.write(new NamedTag("", root), absoluteTarget);

        return new ExportResult(
                totalBlocks,
                blockStats.missingBlocks,
                blockEntityTags.size(),
                blockStats.missingHeads
        );
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    /**
     * Reads every block in the box and builds the palette as it goes. {@link BoundingBox#forEachBlock}
     * iterates y-slowest/z-middle/x-fastest, which is exactly the {@code x + z * Width + y * Width * Length}
     * linear ordering the format requires for the Data array.
     */
    private byte[] encodeBlocks(BoundingBox box, Map<String, Integer> paletteIndices,
                                BlockEncodingStats stats) {
        List<Integer> indices = new ArrayList<>();
        box.forEachBlock(coord -> {
            IBlockState state = blockRegionReader.blockAt(coord);
            if (state == null) {
                stats.missingBlocks++;
            }
            String blockStateKey = state == null ? AIR : toBlockStateKey(state);
            int index = paletteIndices.computeIfAbsent(blockStateKey, key -> paletteIndices.size());
            indices.add(index);
        });
        return VarIntByteArray.pack(indices);
    }

    private List<SpecificTag> encodeBlockEntities(BoundingBox box, BlockEncodingStats stats) {
        List<SpecificTag> result = new ArrayList<>();
        box.forEachBlock(coordinate -> {
            SpecificTag tag = blockRegionReader.blockEntityAt(coordinate);
            if (tag instanceof CompoundTag blockEntity && !blockEntity.get("id").isError()) {
                String id = blockEntity.get("id").stringValue();
                CompoundTag data = copyWithout(blockEntity, Set.of("id", "x", "y", "z"));

                // Debug: log head/banner block entities that are missing their key data.
                // A player_head without "profile" (or "texture"/"Owner" in older formats)
                // will paste as a Steve head. A banner without "patterns" will paste
                // as a blank banner. This typically happens when findBlockEntities
                // overwrote real NBT with a minimal stub.
                if (isHeadBlock(id) && !hasProfileData(data)) {
                    System.out.println("[schematic-export] WARNING: head at " + coordinate
                        + " has no profile/skin data in block entity (id=" + id + ")");
                }
                if (isBannerBlock(id) && !hasPatternData(data)) {
                    System.out.println("[schematic-export] WARNING: banner at " + coordinate
                        + " has no pattern data in block entity (id=" + id + ")");
                }

                CompoundTag encoded = new CompoundTag();
                encoded.add("Pos", new IntArrayTag(new int[] {
                        coordinate.getX() - box.getMin().getX(),
                        coordinate.getY() - box.getMin().getY(),
                        coordinate.getZ() - box.getMin().getZ()
                }));
                encoded.add("Id", new StringTag(id));
                encoded.add("Data", data);
                result.add(encoded);
            } else if (tag == null) {
                // No block entity data for this position. Check whether the block
                // at this position is a head/skull — if so, its profile/skin data
                // is missing (typically because the chunk was never loaded, or the
                // server hasn't sent the block entity yet).
                IBlockState state = blockRegionReader.blockAt(coordinate);
                if (state != null && isHeadBlock(state.getName())) {
                    stats.missingHeads++;
                    System.out.println("[schematic-export] WARNING: head at " + coordinate
                        + " has NO block entity at all (chunk may not be loaded)");
                }
            }
        });
        return result;
    }

    private static boolean isHeadBlock(String name) {
        return name.endsWith("_head") || name.endsWith("_skull");
    }

    private static boolean isBannerBlock(String name) {
        return name.endsWith("_banner") || name.endsWith("banner");
    }

    private static boolean hasProfileData(CompoundTag data) {
        // 1.20.5+ block entity format uses "profile" (compound with name/id/properties)
        // Older format uses "Owner" (compound) or "ExtraType" (string, player name)
        // 26.x may also store texture/cape/elytra/model directly on the block entity
        return !data.get("profile").isError()
            || !data.get("Owner").isError()
            || !data.get("ExtraType").isError()
            || !data.get("texture").isError();
    }

    private static boolean hasPatternData(CompoundTag data) {
        // 1.20.5+ uses "patterns" (list), older format uses "Patterns" (list)
        return !data.get("patterns").isError() || !data.get("Patterns").isError();
    }

    private List<SpecificTag> encodeEntities(BoundingBox box) {
        List<SpecificTag> result = new ArrayList<>();
        Coordinate3D min = box.getMin();
        for (SpecificTag tag : blockRegionReader.entitiesIn(box)) {
            if (!(tag instanceof CompoundTag entity) || entity.get("id").isError() || entity.get("Pos").isError()) {
                continue;
            }

            CompoundTag data = copyWithout(entity, Set.of("id", "Pos"));

            // The entity NBT was generated by toNbtRelativeTo(box.getMin()), so ALL position
            // fields (Pos, TileX/TileY/TileZ, block_pos) are relative to the schematic origin.
            // The Sponge "Pos" field correctly uses relative coordinates. But the "Data" field
            // should contain NBT "as it would appear in a chunk file" (per the Sponge v3 spec),
            // which means absolute world coordinates. FAWE only offsets the Sponge "Pos" field
            // when pasting — it does NOT offset TileX/TileY/TileZ/block_pos inside Data.
            // Without this fix, item frames/paintings try to attach to blocks at the relative
            // (small) position instead of the pasted (absolute) position, causing
            // "Block-attached entity at invalid position" errors.
            offsetTilePositionsToAbsolute(data, min);

            CompoundTag encoded = new CompoundTag();
            encoded.add("Pos", (SpecificTag) entity.get("Pos"));
            encoded.add("Id", new StringTag(entity.get("id").stringValue()));
            encoded.add("Data", data);
            result.add(encoded);
        }
        return result;
    }

    /**
     * Convert entity tile position fields (TileX/TileY/TileZ, block_pos) from
     * schematic-relative coordinates back to absolute world coordinates by adding
     * the box minimum. The Sponge v3 spec says the Data field should contain NBT
     * "as it would appear in a chunk file", which uses absolute coordinates.
     */
    private static void offsetTilePositionsToAbsolute(CompoundTag data, Coordinate3D origin) {
        if (!data.get("TileX").isError()) {
            data.add("TileX", new IntTag(data.get("TileX").intValue() + origin.getX()));
            data.add("TileY", new IntTag(data.get("TileY").intValue() + origin.getY()));
            data.add("TileZ", new IntTag(data.get("TileZ").intValue() + origin.getZ()));
        }
        if (!data.get("block_pos").isError() && data.get("block_pos") instanceof IntArrayTag bp) {
            int[] arr = bp.getData().clone();
            arr[0] += origin.getX();
            arr[1] += origin.getY();
            arr[2] += origin.getZ();
            data.add("block_pos", new IntArrayTag(arr));
        }
    }

    private CompoundTag copyWithout(CompoundTag source, Set<String> excluded) {
        CompoundTag copy = new CompoundTag();
        for (NamedTag entry : source) {
            if (excluded.contains(entry.name())) {
                continue;
            }
            SpecificTag tag = entry.getTag();
            if (tag.isError()) {
                continue;
            }
            copy.add(entry.name(), deepCopy(tag));
        }
        return copy;
    }

    private SpecificTag deepCopy(SpecificTag source) {
        if (source.isError()) {
            return null;
        }
        if (source instanceof CompoundTag compound) {
            CompoundTag copy = new CompoundTag();
            for (NamedTag entry : compound) {
                if (entry.getTag().isError()) {
                    continue;
                }
                SpecificTag child = deepCopy(entry.getTag());
                if (child != null) {
                    copy.add(entry.name(), child);
                }
            }
            return copy;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            source.writeType(output);
            source.write(output);
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            return SpecificTag.read(input.readByte(), input);
        } catch (IOException e) {
            throw new IllegalStateException("Could not copy NBT data", e);
        }
    }

    private String blockStateKeyAt(Coordinate3D coord) {
        IBlockState state = blockRegionReader.blockAt(coord);
        return state == null ? AIR : toBlockStateKey(state);
    }

    /** Mutable counters accumulated during block/entity encoding. */
    private static final class BlockEncodingStats {
        long missingBlocks;
        int missingHeads;
    }

    /**
     * Formats a block state as {@code minecraft:name[prop1=a,prop2=b]}. Property keys are sorted
     * for deterministic, easily testable output; the spec itself allows any order.
     */
    private String toBlockStateKey(IBlockState state) {
        CompoundTag properties = (CompoundTag) state.getProperties();
        if (properties == null || properties.isEmpty()) {
            return state.getName();
        }

        List<String> parts = new ArrayList<>();
        for (NamedTag entry : properties) {
            parts.add(entry.name() + "=" + entry.getTag().stringValue());
        }
        Collections.sort(parts);

        return state.getName() + "[" + String.join(",", parts) + "]";
    }

    // ------------------------------------------------------------------
    // Biomes
    // ------------------------------------------------------------------

    /**
     * Reads the biome at every position in the box, building the biome palette as it goes.
     * Uses the same iteration order and varint packing as {@link #encodeBlocks}.
     * Missing biome data defaults to {@link #DEFAULT_BIOME} so the Data array always has
     * {@code Width * Height * Length} entries as required by the spec.
     */
    private byte[] encodeBiomes(BoundingBox box, Map<String, Integer> paletteIndices) {
        List<Integer> indices = new ArrayList<>();
        box.forEachBlock(coord -> {
            String biome = blockRegionReader.biomeAt(coord);
            if (biome == null || biome.isEmpty()) {
                biome = DEFAULT_BIOME;
            }
            int index = paletteIndices.computeIfAbsent(biome, key -> paletteIndices.size());
            indices.add(index);
        });
        return VarIntByteArray.pack(indices);
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    private CompoundTag buildMetadataTag() {
        CompoundTag metadata = new CompoundTag();
        metadata.add("Name", new StringTag("world-downloader-proxy export"));
        metadata.add("Author", new StringTag("world-downloader-proxy"));
        metadata.add("Date", new LongTag(System.currentTimeMillis()));
        // RequiredMods is string[] per spec — an empty list means no mods are required
        metadata.add("RequiredMods", new ListTag(Tag.TAG_STRING, Collections.emptyList()));
        return metadata;
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    private CompoundTag buildPaletteTag(Map<String, Integer> paletteIndices) {
        CompoundTag palette = new CompoundTag();
        paletteIndices.forEach((key, index) -> palette.add(key, new IntTag(index)));
        return palette;
    }
}
