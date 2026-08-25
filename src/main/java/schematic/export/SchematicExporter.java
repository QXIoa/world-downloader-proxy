package schematic.export;

import game.data.dimension.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import schematic.BoundingBox;

/**
 * Encodes a selected region of the world to a schematic file on disk. Implementations own the
 * on-disk format; callers only need a {@link BoundingBox} and the {@link Dimension} it was taken
 * from. Adding a new output format (e.g. Litematica, or a format tailored for ML training data)
 * means adding a new implementation of this interface, with no changes required to
 * {@link SchematicExportService} or anything upstream of it.
 */
public interface SchematicExporter {
    void export(BoundingBox box, Dimension dimension, Path targetFile) throws IOException;
}
