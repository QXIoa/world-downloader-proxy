package core.interfaces;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Core seam interface for NBT read/write with gzip compression.
 *
 * <p>The schematic export pipeline needs to write NBT data to disk (gzip-compressed)
 * without depending on a per-version {@code NbtUtil} or {@code CompressionManager}.
 * The per-version implementation bridges between the jo-nbt library (which is forked
 * per version in the WET architecture) and this interface.
 */
public interface INbtIO {
    /**
     * Write an NBT tag to a file, gzip-compressed.
     * @param nbt the NBT tag object (per-version concrete type, passed as Object)
     * @param destination the target file path
     */
    void write(Object nbt, Path destination) throws IOException;

    /**
     * Read an NBT tag from a gzip-compressed input stream.
     * @param input the input stream
     * @return the NBT tag object (per-version concrete type, returned as Object)
     */
    Object read(InputStream input) throws IOException;
}
