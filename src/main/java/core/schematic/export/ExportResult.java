package core.schematic.export;

/**
 * Statistics gathered during a schematic export. Returned by
 * {@link SchematicExporter#export} so the caller can report to the user how
 * complete the export was.
 *
 * <p>The two {@code missing*} fields are the key diagnostics for the common
 * "some heads had no skins after pasting" problem: if chunks inside the
 * selection were never loaded by the player, their block entities (including
 * player-head {@code profile} data) are silently absent from the exported
 * schematic.
 *
 * @param totalBlocks           total block positions in the selection
 *                              ({@code Width * Height * Length})
 * @param missingBlocks         positions where the chunk was not loaded and the
 *                              block was treated as air
 * @param exportedBlockEntities block entities actually written to the schematic
 * @param missingHeads          head/skull blocks that had no block-entity data
 *                              (their {@code profile}/skin is lost)
 */
public record ExportResult(
        long totalBlocks,
        long missingBlocks,
        int exportedBlockEntities,
        int missingHeads
) {
    public boolean hasMissingData() {
        return missingBlocks > 0 || missingHeads > 0;
    }
}
