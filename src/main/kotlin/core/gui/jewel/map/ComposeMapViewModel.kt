package core.gui.jewel.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.config.Config
import core.coordinates.Coordinate2D
import core.coordinates.CoordinateDim2D
import core.coordinates.CoordinateDouble2D
import core.gui.ChunkImageState
import core.gui.GuiBridge
import core.gui.images.ImageMode
import core.interfaces.IChunk
import core.interfaces.IChunkImageFactory
import core.interfaces.IDimension
import core.interfaces.IPlayerEntity
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ComposeMapViewModel : GuiBridge {

    var playerX by mutableStateOf(0.0)
    var playerZ by mutableStateOf(0.0)
    var playerRotation by mutableStateOf(0.0)
    var statusMsg by mutableStateOf("")
    var errorVisible by mutableStateOf(false)

    /** Errors collected from the backend, displayed in the Error log tab. */
    val errors = java.util.concurrent.CopyOnWriteArrayList<String>()

    var centerX by mutableStateOf(0.0)
    var centerZ by mutableStateOf(0.0)
    var lockedToPlayer by mutableStateOf(true)

    var blocksPerPixel by mutableStateOf(0.5)

    /**
     * Manual cave mode toggle for Overworld (false = Normal, true = Caves).
     * In Nether, caves are always rendered and this flag is ignored.
     */
    var caveMode by mutableStateOf(false)

    /**
     * Computes the current image mode:
     * - Nether → always CAVES (button is locked)
     * - Overworld + caveMode → CAVES
     * - Overworld + !caveMode → NORMAL
     */
    fun currentImageMode(): ImageMode {
        val isNether = try {
            Config.getVersionModule().worldManager.dimension.isNether
        } catch (e: Exception) { false }

        if (isNether) return ImageMode.CAVES
        return if (caveMode) ImageMode.CAVES else ImageMode.NORMAL
    }

    /**
     * Whether the cave toggle button should be enabled (disabled in Nether).
     */
    fun isCaveButtonEnabled(): Boolean {
        return try {
            !Config.getVersionModule().worldManager.dimension.isNether
        } catch (e: Exception) { true }
    }

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Compose Map Handler")
    }

    private val saveExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Compose Map Image Saver")
    }

    init {
        // Persist region images to disk every 20s, mirroring the JavaFX
        // RegionImageHandler scheduled save (world/image-cache/<mode>/<dim>/r.X.Z.png).
        // Skip in schematic mode — chunks are never saved to disk there, so
        // writing region PNGs would just waste CPU and I/O.
        saveExecutor.scheduleWithFixedDelay({
            if (!Config.isSchematicMode()) {
                regions.values.forEach { it.save() }
            }
        }, 20, 20, TimeUnit.SECONDS)
    }

    private val regions = ConcurrentHashMap<Coordinate2D, ComposeRegionImages>()
    private var activeDimension: IDimension? = null

    /**
     * Chunks that have been persisted to disk during this session. Once a chunk
     * is saved, we never let it flip back to UNSAVED via the onComplete callback
     * (which can fire again when a neighbouring chunk loads and triggers
     * requestImage). This prevents the red overlay from flickering back on
     * chunks that were already saved. A chunk only becomes UNSAVED again if it
     * is explicitly re-sent by the server as a brand-new chunk (which creates a
     * fresh Chunk object with saved=false).
     */
    private val savedChunks = ConcurrentHashMap<CoordinateDim2D, Boolean>()

    private val otherPlayers: Collection<IPlayerEntity>?
        get() = try {
            Config.getVersionModule().worldManager.entityRegistry.playerSet
        } catch (e: Exception) { null }

    init {
        try {
            val mgr = Config.getVersionModule().worldManager
            activeDimension = mgr.dimension
            val pos = mgr.playerPosition.toDouble()
            playerX = pos.x
            playerZ = pos.z
            centerX = pos.x
            centerZ = pos.z
            val guard = core.schematic.SchematicRadiusGuard(mgr)
            mgr.setPlayerPosListener { pos, rot ->
                val dx = kotlin.math.abs(pos.x - playerX)
                val dz = kotlin.math.abs(pos.z - playerZ)
                playerX = pos.x
                playerZ = pos.z
                playerRotation = rot
                // If the player teleported a large distance (e.g. bungeecord
                // server switch), re-lock the camera to the player so the map
                // follows them to the new location instead of snapping back
                // to the old center (or 0,0 if the position was never set).
                if (dx > 64 || dz > 64) {
                    lockedToPlayer = true
                    centerX = pos.x
                    centerZ = pos.z
                }
                // Evict chunks outside the schematic radius (no-op when not
                // in schematic mode or radius is 0).
                guard.accept(pos, rot)
            }
            // Pre-load cached region images from disk so the map isn't blank
            // on reconnect. Mirrors RegionImageHandler.loadFromFile().
            // Skip in schematic mode — schematic mode doesn't save to disk, so
            // loading cached PNGs would waste memory on stale images.
            if (!Config.isSchematicMode()) {
                loadCachedRegions()
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Scan the image-cache directory for previously saved region PNGs and load
     * them into the regions map. The overlay starts fully OUTDATED (grey) when
     * markOldChunks is enabled; individual chunks will be recoloured to SAVED
     * or UNSAVED as the server re-sends them and setChunkLoaded fires.
     */
    private fun loadCachedRegions() {
        val dim = activeDimension ?: return
        val normalDir = java.nio.file.Paths.get(
            Config.getWorldOutputDir(), "image-cache", ImageMode.NORMAL.path(), dim.path
        )
        if (!java.nio.file.Files.isDirectory(normalDir)) return
        try {
            java.nio.file.Files.list(normalDir).use { stream ->
                stream.forEach { file ->
                    val name = file.fileName.toString()
                    if (!name.endsWith(".png") || name.startsWith("small.")) return@forEach
                    val parts = name.split(".")
                    if (parts.size < 4) return@forEach
                    try {
                        val x = parts[1].toInt()
                        val z = parts[2].toInt()
                        val coord = Coordinate2D(x, z)
                        regions.computeIfAbsent(coord) {
                            ComposeRegionImages.loadRegion(dim, coord)
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setChunkLoaded(coord: CoordinateDim2D, chunk: IChunk) {
        val dim = activeDimension ?: return
        if (coord.dimension != dim) return

        val factory: IChunkImageFactory = chunk.chunkImageFactory
        factory.onComplete { imageMap, isSaved ->
            executor.schedule({
                drawChunk(coord, imageMap, isSaved)
            }, 0, TimeUnit.MILLISECONDS)
        }
        factory.onSaved {
            executor.schedule({
                markChunkSaved(coord)
            }, 0, TimeUnit.MILLISECONDS)
        }
        factory.requestImage()
    }

    private fun drawChunk(coord: CoordinateDim2D, imageMap: Map<ImageMode, BufferedImage>, isSaved: Boolean) {
        val region = coord.chunkToRegion()
        val images = regions.computeIfAbsent(region) {
            ComposeRegionImages.loadRegion(activeDimension!!, region)
        }
        val local = coord.toRegionLocal()
        imageMap.forEach { (mode, buf) ->
            val img = images.getImage(mode)
            img.drawChunk(local, buf)
        }
        // If this chunk was already saved earlier in this session, keep it
        // saved — don't let a stale isSaved=false from a re-render (triggered
        // by a neighbouring chunk loading) flip the overlay back to red.
        val effectiveSaved = isSaved || savedChunks.containsKey(coord)
        images.colourChunk(local, ChunkImageState.isSaved(effectiveSaved))
    }

    private fun markChunkSaved(coord: CoordinateDim2D) {
        savedChunks[coord] = true
        val region = coord.chunkToRegion()
        val images = regions[region] ?: return
        val local = coord.toRegionLocal()
        images.colourChunk(local, ChunkImageState.SAVED)
    }

    override fun setDimension(dimension: IDimension) {
        if (activeDimension == dimension || activeDimension?.equals(dimension) == true) return
        // Flush images for the old dimension before switching
        // (skip in schematic mode — nothing to persist)
        if (!Config.isSchematicMode()) {
            regions.values.forEach { it.save() }
        }
        activeDimension = dimension
        regions.clear()
        savedChunks.clear()
        loadCachedRegions()
    }

    override fun clearChunks() {
        regions.clear()
        savedChunks.clear()
    }

    override fun resetRegion(regionLocation: Coordinate2D) {
        regions.remove(regionLocation)
    }

    override fun setChunkState(coords: Coordinate2D, state: ChunkImageState) {
        val region = coords.chunkToRegion()
        val images = regions[region] ?: return
        images.colourChunk(coords.toRegionLocal(), state)
    }

    override fun clearChunk(coords: Coordinate2D) {
        val region = coords.chunkToRegion()
        val images = regions[region] ?: return
        images.clearChunk(coords.toRegionLocal())
    }

    /**
     * Remove all region images that are entirely outside the given Chebyshev
     * radius (in chunks) from the center. This frees the BufferedImage memory
     * (512×512×4 bytes × 2 modes ≈ 2MB per region) that would otherwise leak
     * indefinitely as the player teleports across the map.
     *
     * A region is considered outside if its closest chunk to the center is
     * farther than the radius. Region coordinates are in chunks/32.
     */
    override fun clearRegionsOutsideRadius(center: Coordinate2D, radius: Int) {
        val centerRegionX = center.x shr 5
        val centerRegionZ = center.z shr 5
        // A region spans 32 chunks. It's outside if even its nearest edge
        // is beyond the radius. Use region-level Chebyshev distance with
        // a margin of 1 to account for regions partially within range.
        val regionRadius = (radius shr 5) + 1
        val toRemove = mutableListOf<Coordinate2D>()
        regions.keys.forEach { rc ->
            val dx = kotlin.math.abs(rc.x - centerRegionX)
            val dz = kotlin.math.abs(rc.z - centerRegionZ)
            if (dx > regionRadius || dz > regionRadius) {
                toRemove.add(rc)
            }
        }
        toRemove.forEach { regions.remove(it) }
    }

    override fun setStatusMessage(str: String) {
        statusMsg = str
    }

    override fun showErrorMessage() {
        errorVisible = true
    }

    override fun hideErrorMessage() {
        errorVisible = false
    }

    override fun addError(message: String) {
        errors.add(message)
    }

    fun getCenter(): CoordinateDouble2D {
        return if (lockedToPlayer) {
            CoordinateDouble2D(playerX, playerZ)
        } else {
            CoordinateDouble2D(centerX, centerZ)
        }
    }

    fun getVisibleRegions(viewportWidth: Float, viewportHeight: Float): List<RegionRenderData> {
        val center = getCenter()
        val blockW = viewportWidth * blocksPerPixel.toFloat()
        val blockH = viewportHeight * blocksPerPixel.toFloat()
        val minX = center.x - blockW / 2
        val maxX = center.x + blockW / 2
        val minZ = center.z - blockH / 2
        val maxZ = center.z + blockH / 2

        val result = mutableListOf<RegionRenderData>()
        val mode = currentImageMode()

        regions.forEach { (coord, images) ->
            val rx = coord.x shl 9
            val rz = coord.z shl 9
            val visible = maxX > rx && minX < rx + 512 && maxZ > rz && minZ < rz + 512
            if (visible) {
                val img = images.getImage(mode).image
                if (img != null) {
                    result.add(RegionRenderData(coord, img, images.getOverlayImage()))
                }
            }
        }
        return result
    }

    fun getOtherPlayers(): List<IPlayerEntity> {
        return otherPlayers?.toList() ?: emptyList()
    }

    fun shutdown() {
        // Flush pending images to disk before shutting down
        // (skip in schematic mode — nothing to persist)
        if (!Config.isSchematicMode()) {
            regions.values.forEach { it.save() }
        }
        saveExecutor.shutdown()
        executor.shutdown()
    }
}

data class RegionRenderData(
    val regionCoord: Coordinate2D,
    val image: BufferedImage,
    val overlay: BufferedImage?,
)
