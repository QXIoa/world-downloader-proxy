package core.gui.jewel.map

import core.config.Config
import core.coordinates.Coordinate2D
import core.dimension.WorldGeometry
import core.gui.ChunkImageState
import core.gui.images.ImageMode
import core.interfaces.IDimension
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO

class ComposeRegionImages(
    val coordinate: Coordinate2D,
    val normal: ComposeRegionImage,
    val caves: ComposeRegionImage,
) {
    fun getImage(mode: ImageMode): ComposeRegionImage = when (mode) {
        ImageMode.NORMAL -> normal
        ImageMode.CAVES -> caves
    }

    fun colourChunk(local: Coordinate2D, state: ChunkImageState) {
        normal.colourChunk(local, state)
        caves.colourChunk(local, state)
    }

    fun getOverlayImage(): BufferedImage? = normal.chunkOverlay

    fun save() {
        normal.save()
        caves.save()
    }

    companion object {
        fun loadRegion(dimension: IDimension, coordinate: Coordinate2D): ComposeRegionImages {
            val normalPath = dimensionPath(dimension, ImageMode.NORMAL)
            val cavesPath = dimensionPath(dimension, ImageMode.CAVES)
            return ComposeRegionImages(
                coordinate,
                ComposeRegionImage.load(normalPath, coordinate),
                ComposeRegionImage.load(cavesPath, coordinate),
            )
        }

        private fun dimensionPath(dim: IDimension, mode: ImageMode): java.nio.file.Path {
            return Paths.get(Config.getWorldOutputDir(), "image-cache", mode.path(), dim.path)
        }
    }
}

class ComposeRegionImage(
    private val path: java.nio.file.Path,
    val coordinate: Coordinate2D,
) {
    private val size = WorldGeometry.SECTION_WIDTH * WorldGeometry.REGION_SIZE // 512

    var image: BufferedImage? = null
        private set

    var chunkOverlay: BufferedImage? = null
        private set

    private var saved = true

    init {
        chunkOverlay = BufferedImage(
            WorldGeometry.REGION_SIZE, WorldGeometry.REGION_SIZE, BufferedImage.TYPE_INT_ARGB
        )
        if (Config.markOldChunks()) {
            fillOverlay(ChunkImageState.OUTDATED.getColor())
        }
    }

    private fun fillOverlay(c: Color) {
        val g = chunkOverlay!!.createGraphics()
        g.color = c
        g.fillRect(0, 0, WorldGeometry.REGION_SIZE, WorldGeometry.REGION_SIZE)
        g.dispose()
    }

    fun drawChunk(local: Coordinate2D, chunkImage: BufferedImage) {
        var img = image
        if (img == null) {
            img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            image = img
        }
        val g = img.createGraphics()
        val sx = local.x * WorldGeometry.SECTION_WIDTH
        val sz = local.z * WorldGeometry.SECTION_WIDTH
        g.drawImage(chunkImage, sx, sz, null)
        g.dispose()
        saved = false

        // Overlay is a separate independent layer — do NOT clear it here.
        // It is only modified through colourChunk() calls:
        //   - UNSAVED (red) when a chunk is first loaded or modified
        //   - SAVED (transparent) when the save service persists it
        // Clearing it on every re-render caused a race with the save thread
        // which made the red overlay flicker on and off.
    }

    fun colourChunk(local: Coordinate2D, state: ChunkImageState) {
        val g = chunkOverlay!!.createGraphics()
        // Use Src composite so the new color replaces the old pixel entirely
        // (including alpha). With the default SrcOver, painting a transparent
        // color (SAVED) would leave the previous red UNSAVED overlay in place.
        g.composite = java.awt.AlphaComposite.Src
        g.color = state.getColor()
        g.fillRect(local.x, local.z, 1, 1)
        g.dispose()
    }

    /**
     * Persist the region image to the image-cache directory as a PNG.
     * Mirrors RegionImage.save() in the JavaFX backend.
     */
    fun save() {
        if (saved) return
        val img = image ?: return
        try {
            path.toFile().mkdirs()
            val f = getFile(path, "", coordinate)
            ImageIO.write(img, "png", f)
            saved = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun load(path: java.nio.file.Path, coordinate: Coordinate2D): ComposeRegionImage {
            val img = ComposeRegionImage(path, coordinate)
            val file = getFile(path, "", coordinate)
            if (file.exists()) {
                try {
                    img.image = ImageIO.read(file)
                } catch (e: Exception) {
                }
            }
            return img
        }

        private fun getFile(p: java.nio.file.Path, prefix: String, coords: Coordinate2D): File {
            return File(p.toFile(), prefix + "r." + coords.x + "." + coords.z + ".png")
        }
    }
}
