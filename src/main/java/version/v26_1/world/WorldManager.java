package version.v26_1.world;

import core.chunk.palette.BlockColors;
import core.config.Config;
import core.coordinates.Coordinate2D;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim2D;
import core.coordinates.CoordinateDouble3D;
import core.gui.ChunkImageState;
import core.gui.GuiManager;
import core.interfaces.IWorldManager;
import core.messages.Messages;
import core.util.PathUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import version.v26_1.chunk.*;
import version.v26_1.chunk.palette.BlockState;
import version.v26_1.commandblock.CommandBlockManager;
import version.v26_1.container.ContainerManager;
import version.v26_1.dimension.Dimension;
import version.v26_1.dimension.DimensionRegistry;
import version.v26_1.dimension.DimensionType;
import version.v26_1.entity.EntityNames;
import version.v26_1.entity.EntityRegistry;
import version.v26_1.maps.MapRegistry;
import version.v26_1.module.VersionAccessors;
import version.v26_1.packets.DataTypeProvider;
import version.v26_1.packets.builder.PacketBuilder;
import version.v26_1.proxy.PacketInjector;
import version.v26_1.region.McaFile;
import version.v26_1.region.McaFilePair;
import version.v26_1.region.Region;
import version.v26_1.villagers.VillagerManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static core.util.ExceptionHandling.attempt;

/**
 * Manage the world, including saving, parsing and updating the GUI.
 */
public class WorldManager implements IWorldManager {
    private static final int INIT_SAVE_DELAY = 5 * 1000;
    private static final int SAVE_DELAY = 12 * 1000;
    private static WorldManager instance;
    private final LevelData levelData;
    private final MapRegistry mapRegistry;
    private final Map<CoordinateDim2D, Queue<Runnable>> chunkLoadCallbacks = new ConcurrentHashMap<>();
    private Map<CoordinateDim2D, Region> regions = new ConcurrentHashMap<>();

    private EntityNames entityMap;
    private BlockColors blockColors;

    private boolean markNewChunks;
    private boolean writeChunks;
    private boolean schematicMode;
    private boolean isStarted;
    private final Set<Dimension> savingDimension;

    private ContainerManager containerManager;
    private CommandBlockManager commandBlockManager;
    private VillagerManager villagerManager;
    private DimensionRegistry dimensionCodec;
    private final RenderDistanceExtender renderDistanceExtender;

    private BiConsumer<CoordinateDouble3D, Double> playerPosListener;
    private final CoordinateDouble3D playerPosition;
    private boolean isBelowGround = false;
    private double playerRotation = 0;
    private Dimension dimension;
    private DimensionType dimensionType;
    private final EntityRegistry entityRegistry;
    private final ChunkFactory chunkFactory;

    private ScheduledExecutorService saveService;

    public WorldManager() {
        this.isStarted = false;
        this.entityMap = new EntityNames();
        this.entityRegistry = new EntityRegistry(this);
        this.chunkFactory = new ChunkFactory();
        this.mapRegistry = new MapRegistry();

        this.levelData = new LevelData(this);

        this.playerPosition = this.levelData.getPlayerPosition();
        this.dimension = this.levelData.getPlayerDimension();
        this.savingDimension = new HashSet<>();
        this.renderDistanceExtender = new RenderDistanceExtender(this);
    }

    public static WorldManager getInstance() {
        if (instance == null) {
            instance = new WorldManager();
        }
        return instance;
    }

    public static void setInstance(WorldManager worldManager) {
        instance = worldManager;
    }

    public void registerChunkLoadCallback(CoordinateDim2D coordinate, Runnable r) {
        chunkLoadCallbacks.putIfAbsent(coordinate, new ConcurrentLinkedQueue<>());
        chunkLoadCallbacks.get(coordinate).add(r);
    }

    public void deregisterChunkLoadCallback(CoordinateDim2D coordinate, Runnable r) {
        if (chunkLoadCallbacks.containsKey(coordinate)) {
            Queue<Runnable> queue = chunkLoadCallbacks.get(coordinate);
            queue.remove(r);
            if (queue.isEmpty()) {
                chunkLoadCallbacks.remove(coordinate);
            }
        }
    }

    public void updateExtendedRenderDistance(int val) {
        this.renderDistanceExtender.setExtendedDistance(val);
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        if (this.dimension.equals(dimension)) {
            return;
        }

        saveAndUnloadChunks();
        this.dimension = dimension;

        this.renderDistanceExtender.reset();

        GuiManager.setDimension(this.dimension);
    }

    public void setDimensionType(DimensionType dimensionType) {
        this.dimensionType = dimensionType;
        Chunk.setWorldHeight(dimensionType.getDimensionMinHeight(), dimensionType.getDimensionMaxHeight());
    }

    private void saveAndUnloadChunks() {
        if (saveService == null) {
            return;
        }

        // keep references of dimensions and regions, since the saving thread executes later and
        // will have been overwritten by then
        Dimension dimension = this.dimension;
        Map<CoordinateDim2D, Region> regions = this.regions;
        saveService.execute(() -> {
            save(dimension, regions);
            unloadChunks(regions);
        });
        this.regions = new ConcurrentHashMap<>();
    }

    private void checkAboveSurface() {
        Coordinate3D discrete = this.playerPosition.discretize();

        Coordinate3D local = discrete.globalToChunkLocal();

        if (dimension == Dimension.NETHER) {
            isBelowGround = local.getY() < 128;
            return;
        }

        Chunk c = getChunk(discrete.globalToChunk());
        if (c == null) {
            return;
        }

        int height = c.getChunkHeightHandler().heightAt(local.getX(), local.getZ()) - 5;
        isBelowGround = local.getY() < height;
    }

    private void unloadChunks(Map<CoordinateDim2D, Region> regions) {
        regions.values().forEach(Region::unloadAll);
    }

    public double getPlayerRotation() {
        return playerRotation;
    }

    public void setPlayerRotation(double playerRotation) {
        this.playerRotation = playerRotation;

        if (this.playerPosListener != null) {
            this.playerPosListener.accept(this.playerPosition, this.playerRotation);
        }
    }

    /**
     * Draw all previously-downloaded chunks in the GUI. Some limits in place
     * @param center center region around which to find neighbouring regions
     */
    public void drawExistingChunks(Coordinate2D center) {
        Collection<McaFile> files = McaFile.getFiles(center, this.dimension, 16).toList();

        for (McaFile file : files) {
            drawRegion(file);
        }
    }

    public void drawExistingRegion(Coordinate2D coords) {
        CoordinateDim2D regionCoordinates = coords.addDimension(this.dimension);
        drawRegion(new McaFile(regionCoordinates));
    }

    /**
     * Draw a region from a given MCA file. We can't just load them all and immediately draw them to
     * the GUI, as the shading requires that we look at neighbouring chunks. We first add them all
     * to the world manager, then draw them, and then delete them. This is more work but ensures
     * proper shading on all chunks.
     * @return the number of chunks drawn
     */
    private void drawRegion(McaFile file) {
        GuiManager.resetRegion(file.getRegionLocation());
        Map<CoordinateDim2D, Chunk> chunks = file.getParsedChunks(this.dimension);

        // add all chunks to the WorldManager if it doesn't have them yet
        Set<CoordinateDim2D> toDelete = new HashSet<>();
        for (Map.Entry<CoordinateDim2D, Chunk> entry : chunks.entrySet()) {
            CoordinateDim2D coord = entry.getKey();
            Chunk chunk = entry.getValue();
            Chunk existing = getChunk(coord);
            if (existing == null) {
                toDelete.add(coord);
                loadChunk(chunk, false, false);
            } else {
                existing.getChunkImageFactory().requestImage();
            }
        }

        // draw to GUI
        chunks.forEach(GuiManager::setChunkLoaded);

        // delete the newly added chunks
        toDelete.forEach(this::unloadChunk);
    }

    /**
     * Set the config variables for the save service.
     */
    public void setWorldManagerVariables(boolean markNewChunks, Boolean writeChunks) {
        this.markNewChunks = markNewChunks;
        this.writeChunks = writeChunks;
    }

    /**
     * Enable or disable schematic mode. While enabled, chunks are always retained in memory
     * (regardless of {@link #writeChunks} or whether the GUI is drawing them) so that in-game
     * selections can be read back later, but nothing is ever written to disk: no region files,
     * no level.dat, no dimension data. This is independent from {@code --disable-chunk-saving},
     * which (when the GUI is active) does not retain chunks in memory at all.
     */
    public void setSchematicMode(boolean schematicMode) {
        this.schematicMode = schematicMode;
    }

    public boolean isSchematicMode() {
        return schematicMode;
    }

    /**
     * Start the periodic saving service.
     */
    public void startSaveService() {
        if (isStarted) {
            return;
        }
        isStarted = true;

        this.start();
    }

    /**
     * Add a parsed chunk to the correct region.
     *
     * @param chunk the chunk
     */
    public void loadChunk(Chunk chunk, boolean drawInGui, boolean overrideExisting) {
        if (!drawInGui || writeChunks || schematicMode) {
            CoordinateDim2D regionCoordinates = chunk.location.chunkToDimRegion();

            Region r = regions.computeIfAbsent(regionCoordinates, Region::new);

            r.addChunk(chunk.location, chunk, overrideExisting);
        }

        if (drawInGui) {
            // draw the chunk once its been parsed
            chunk.whenParsed(() -> GuiManager.setChunkLoaded(chunk.location, chunk));
        }

        this.renderDistanceExtender.notifyLoaded(chunk.location.stripDimension());
    }

    public void chunkLoadedCallback(Chunk c) {
        // run callbacks
        Queue<Runnable> callbacks = chunkLoadCallbacks.remove(c.location);
        if (callbacks != null) {
            while (!callbacks.isEmpty()) {
                callbacks.remove().run();
            }
        }
    }

    /**
     * Get a chunk from the region its in.
     *
     * @param coordinate the global chunk coordinates
     * @return the chunk
     */
    public Chunk getChunk(CoordinateDim2D coordinate) {
        return regions.getOrDefault(coordinate.chunkToDimRegion(), Region.EMPTY).getChunk(coordinate);
    }

    public Chunk getChunk(Coordinate2D coords) {
        return getChunk(coords.addDimension(this.dimension));
    }

    public void unloadChunk(CoordinateDim2D coordinate) {
        chunkFactory.unloadChunk(coordinate);

        CoordinateDim2D regionCoordinate = coordinate.chunkToDimRegion();
        Region r = regions.get(regionCoordinate);
        if (r != null) {
            r.removeChunk(coordinate);

            if (r.canRemove()) {
                regions.remove(regionCoordinate);
            }
        }
        this.renderDistanceExtender.notifyUnloaded(coordinate.stripDimension());
    }

    public BlockState blockStateAt(Coordinate3D coordinate3D) {
        Chunk c = this.getChunk(coordinate3D.globalToChunk().addDimension(this.dimension));

        if (c == null) {
            return null;
        }

        Coordinate3D pos = coordinate3D.withinChunk();
        return c.getBlockStateAt(pos);
    }

    /**
     * Read the biome resource location at the given world-space coordinates, or {@code null} if
     * the chunk is not loaded or the version does not support per-section biomes.
     */
    public String biomeAt(Coordinate3D coordinate3D) {
        Chunk c = this.getChunk(coordinate3D.globalToChunk().addDimension(this.dimension));
        if (c == null) {
            return null;
        }
        Coordinate3D pos = coordinate3D.withinChunk();
        return c.getBiomeAt(pos.getX(), pos.getY(), pos.getZ());
    }

    public EntityNames getEntityMap() {
        return entityMap;
    }

    public void setEntityMap(EntityNames names) {
        entityMap = names;
    }

    public BlockColors getBlockColors() {
        if (blockColors == null) {
            blockColors = BlockColors.create();
        }
        return blockColors;
    }


    public boolean markNewChunks() {
        return markNewChunks;
    }


    /**
     * Mark a chunk and it's region as having unsaved changes.
     */
    public void touchChunk(ChunkEntities c) {
        c.touch();
        regions.get(c.getLocation().chunkToDimRegion()).touch();
    }

    public DimensionRegistry getDimensionRegistry() {
        if (dimensionCodec == null) {
            this.dimensionCodec = DimensionRegistry.empty();
        }
        return dimensionCodec;
    }

    /**
     * Set the dimension registry, used to store information about the dimensions that this server supports.
     */
    public void setDimensionRegistry(DimensionRegistry registry) {
        dimensionCodec = registry;

        // schematic mode never writes anything to disk
        if (schematicMode) {
            return;
        }

        // We can immediately try to write the dimension data to the proper directory.
        try {
            Path p = PathUtils.toPath(Config.getWorldOutputDir(), "datapacks", "downloaded", "data");
            if (registry.write(p)) {

                // we need to copy that pack.mcmeta file from so that Minecraft will recognise the datapack
                Path packMeta = PathUtils.toPath(p.getParent().toString(), "pack.mcmeta");
                InputStream in = WorldManager.class.getClassLoader().getResourceAsStream("pack.mcmeta");
                if (in == null) {
                    throw new IOException("Missing pack.mcmeta resource");
                }
                byte[] bytes = IOUtils.toByteArray(in);
                Files.write(packMeta, bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(Messages.console("console.dimension.codec_error"));
        }
    }

    /**
     * Periodically save the world.
     */
    public void start() {
        ThreadFactory namedThreadFactory = r -> new Thread(r, "World Save Service");
        saveService = Executors.newScheduledThreadPool(1, namedThreadFactory);
        saveService.scheduleWithFixedDelay(() -> attempt(this::save), INIT_SAVE_DELAY, SAVE_DELAY, TimeUnit.MILLISECONDS);
    }

    private void save(Dimension dimension, Map<CoordinateDim2D, Region> regions) {
        checkAboveSurface();

        if (!writeChunks || schematicMode) {
            return;
        }

        if (savingDimension.contains(dimension)) {
            System.out.println(Messages.console("console.dimension.already_saving", dimension));
            if (saveService != null) {
                attempt(() -> {
                    if (!saveService.awaitTermination(30, TimeUnit.SECONDS)) {
                        System.out.println(Messages.console("console.world.save_warning"));
                    }
                });
            }
            return;
        }
        savingDimension.add(dimension);

        // save level.dat
        attempt(levelData::save);
        attempt(mapRegistry::save);

        if (!regions.isEmpty()) {
            // convert the values to an array first to prevent blocking any threads
            Region[] r = regions.values().toArray(new Region[0]);
            for (Region region : r) {
                McaFilePair files = region.toFile(getPlayerPosition().globalToChunk());
                if (files == null) {
                    continue;
                }

                write(files.getRegion());
                write(files.getEntities());
            }
        }

        // remove empty regions
        regions.entrySet().removeIf(el -> el.getValue().isEmpty());

        savingDimension.remove(dimension);

        // suggest GC to clear up some memory that may have been freed by saving
        System.gc();
    }

    /**
     * Save the world. Will tell all regions to save their chunks.
     */
    public void save() {
        save(this.dimension, this.regions);
    }

    private void write(McaFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        attempt(file::write);
    }

    public ContainerManager getContainerManager() {
        if (containerManager == null) {
            containerManager = new ContainerManager();
        }
        return containerManager;
    }
    
    public CommandBlockManager getCommandBlockManager() {
        if (commandBlockManager == null) {
            commandBlockManager = new CommandBlockManager();
        }
        return commandBlockManager;
    }
    
    public VillagerManager getVillagerManager() {
        if (villagerManager == null) {
            villagerManager = new VillagerManager();
        }
        return villagerManager;
    }

    public void deleteAllExisting() {
        regions = new HashMap<>();
        chunkFactory.clear();

        try {
            File dir = PathUtils.toPath(Config.getWorldOutputDir(), this.dimension.getPath(), "region").toFile();

            if (dir.isDirectory()) {
                FileUtils.cleanDirectory(dir);
            }
        } catch (IOException ex) {
            System.out.println(Messages.console("console.world.delete_regions_failed", ex.getMessage()));
        }

        GuiManager.clearChunks();
    }

    public void setPlayerPosListener(BiConsumer<CoordinateDouble3D, Double> playerPosListener) {
        this.playerPosListener = playerPosListener;
    }

    public Coordinate3D getPlayerPosition() {
        return playerPosition.discretize();
    }

    public CoordinateDouble3D getPlayerPositionDouble() {
        return playerPosition;
    }

    public void setPlayerPosition(double x, double y, double z) {
        this.playerPosition.setTo(x, y, z);

        if (this.renderDistanceExtender != null) {
            this.renderDistanceExtender.updatePlayerPos(getPlayerPosition());
        }
        if (this.playerPosListener != null) {
            this.playerPosListener.accept(this.playerPosition, this.playerRotation);
        }
    }

    /**
     * Send unload chunk packets to the client for each of the coordinates. Currently not used as chunk unloading is
     * not really important, the client can figure it out.
     *
     * @param toUnload the set of chunks to unload.
     */
    public void unloadChunks(Collection<Coordinate2D> toUnload) {
        for (Coordinate2D coords : toUnload) {
            unloadChunk(coords.addDimension(getDimension()));
        }
    }

    /**
     * Load chunks from their MCA files and send them to the client.
     *
     * @param desired the set of chunk coordinates which we want to send to the client.
     * @return the set of chunks that was actually sent to the client.
     */
    public Set<Coordinate2D> sendChunksToPlayer(Collection<Coordinate2D> desired) {
        PacketInjector injector = VersionAccessors.injector();
        Set<Coordinate2D> loaded = new HashSet<>();

        int chunksSent = 0;
        Map<Coordinate2D, McaFile> loadedFiles = new HashMap<>();
        for (Coordinate2D coords : desired) {
            // since there is delay in this loop, it's possible some of the chunks were sent to the client by the time
            // we get to them.
            if (!this.renderDistanceExtender.isStillNeeded(coords)) {
                continue;
            }

            McaFile mca = loadedFiles.computeIfAbsent(coords.chunkToRegion(), (c) -> McaFile.ofCoords(c.addDimension(this.dimension)));

            if (mca == null) {
                continue;
            }

            CoordinateDim2D withDim = coords.addDimension(this.dimension);
            ChunkBinary chunkBinary = mca.getChunkBinary(withDim);

            // skip any chunks not in the MCA file
            if (chunkBinary == null) {
                continue;
            }

            // send a packet with the chunk to the client
            Chunk chunk = chunkBinary.toChunk(withDim);

            // skip chunks loaded in an earlier version
            if (chunk.getDataVersion() != Config.versionReporter().getDataVersion()) {
                continue;
            }

            try {
                PacketBuilder chunkData = chunk.toPacket();
                PacketBuilder light = chunk.toLightPacket();
                if (light != null) {
                    injector.enqueuePacket(light);
                }
                injector.enqueuePacket(chunkData);
                chunksSent++;

            } catch (IncompleteChunkException ex) {
                ex.printStackTrace();
                // chunk was not complete
                continue;
            }
            loaded.add(coords);

            if (Config.drawExtendedChunks()) {
                GuiManager.setChunkState(coords, ChunkImageState.EXTENDED);
            }

            // periodically sleep so the client doesn't stutter from receiving too many chunks
            chunksSent = (chunksSent + 1) % 5;
            if (chunksSent == 0) {
                attempt(() -> Thread.sleep(48));
            }
        }
        return loaded;
    }

    public void resetConnection() {
        this.renderDistanceExtender.reset();
        this.entityRegistry.reset();
        this.chunkFactory.reset();

        this.saveAndUnloadChunks();
    }

    @Override
    public int countActiveEntities() { return getEntityRegistry().countActiveEntities(); }

    @Override
    public int countActivePlayers() { return getEntityRegistry().countActivePlayers(); }

    @Override
    public int countActiveMaps() { return mapRegistry.countActiveMaps(); }

    public EntityRegistry getEntityRegistry() {
        return entityRegistry;
    }
    public ChunkFactory getChunkFactory() {
        return chunkFactory;
    }
    public MapRegistry getMapRegistry() {
        return mapRegistry;
    }

    public void unloadEntities(CoordinateDim2D coord) {
        this.entityRegistry.unloadChunk(coord);
        this.chunkFactory.unloadChunk(coord);
    }

    public int countActiveChunks() {
        return countActiveChunks(regions);
    }

    private int countActiveChunks(Map<CoordinateDim2D, Region> regions) {
        int total = 0;
        for (Region r : regions.values()) {
            total += r.countChunks();
        }
        return total;
    }

    public void blockChange(DataTypeProvider provider) {
        if (!Config.handleBlockChanges()) {
            return;
        }
        chunkFactory.runOnFactoryThread(() -> {
            Coordinate3D coords = provider.readCoordinates();
            Chunk c = getChunk(coords.globalToChunk().addDimension(this.dimension));
            if (c == null) {
                return;
            }

            c.updateBlock(coords.globalToChunkLocal(), provider.readVarInt());
            touchChunk(c);
        });
    }

    public void multiBlockChange(Coordinate3D pos, DataTypeProvider provider) {
        if (!Config.handleBlockChanges()) {
            return;
        }
        chunkFactory.runOnFactoryThread(() -> {
            Chunk c = getChunk(pos.addDimension(this.dimension));
            if (c == null) {
                return;
            }
            c.updateBlocks(pos, provider);
        });

    }


    /**
     * Update a chunk with lighting data. If the chunk is not known yet, give it to the chunk factory. If it is known,
     * it is given to the chunk to parse immediately.
     */
    public void updateLight(DataTypeProvider provider) {
        chunkFactory.runOnFactoryThread(() -> {
            int chunkX = provider.readVarInt();
            int chunkZ = provider.readVarInt();
            CoordinateDim2D coords = new CoordinateDim2D(chunkX, chunkZ, dimension);
            Chunk c = getChunk(coords);
            if (c == null) {
                chunkFactory.updateLight(coords, provider);
            } else {
                c.updateLight(provider);
                touchChunk(c);
            }
        });

    }

    public int countActiveRegions() {
        return this.regions.size();
    }

    public int countQueuedChunks() {
        return this.chunkFactory.countQueuedChunks();
    }


    public int countActiveBinaryChunks() {
        return this.regions.values().stream().mapToInt(el -> el.getFile().countChunks()).sum();
    }

    public void loadLevelData() {
        try {
            this.levelData.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        if (saveService != null) {
            saveService.shutdown();
        }
    }

    public boolean canForget(CoordinateDim2D co) {
        return renderDistanceExtender.canUnload(co);
    }

    public int countExtendedChunks() {
        return renderDistanceExtender.countLoaded();
    }

    public boolean isBelowGround() {
        return isBelowGround;
    }
}

