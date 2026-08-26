package core.gui;

import core.config.Config;
import core.interfaces.IWorldManager;
import core.interfaces.IChunkBinary;
import core.interfaces.IMcaFile;


import core.coordinates.Coordinate2D;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IDimension;

import core.gui.images.ImageMode;
import core.gui.images.RegionImageHandler;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

public class RightClickMenu extends ContextMenu {
    final static String PROMPT_PAUSE = "Pause chunk saving";
    final static String PROMPT_RESUME = "Resume chunk saving";

    final List<MenuItem> renderModes = List.of(
        construct("Automatic", e -> setRenderMode(null, e)),
        construct("Surface", e -> setRenderMode(ImageMode.NORMAL, e)),
        construct("Caves", e -> setRenderMode(ImageMode.CAVES, e))
    );

    private void setRenderMode(ImageMode mode, Event e) {
        renderModes.forEach(el -> el.setDisable(false));
        MenuItem clicked = (MenuItem) e.getTarget();
        clicked.setDisable(true);

        RegionImageHandler.setOverrideMode(mode);
    }

    public RightClickMenu(GuiMap handler) {
        List<MenuItem> menu = this.getItems();

        menu.add(construct(PROMPT_PAUSE, event -> {
            MenuItem item =  ((MenuItem) event.getTarget());
            if (Config.getVersionModule().getWorldManager().isPaused()) {
                Config.getVersionModule().getWorldManager().resumeSaving();
                item.setText(PROMPT_PAUSE);
            } else {
                Config.getVersionModule().getWorldManager().pauseSaving();
                item.setText(PROMPT_RESUME);
            }
            handler.setStatusMessage("");
        }));

        menu.add(construct("Delete all downloaded chunks", e -> {
            Alert alert = new Alert(Alert.AlertType.NONE,
                    "Are you sure you want to delete all downloaded chunks? This cannot be undone.",
                    ButtonType.CANCEL, ButtonType.YES
            );
            GuiManager.addIcon((Stage) alert.getDialogPane().getScene().getWindow());
            alert.setTitle("Confirm delete");
            var darkCss = getClass().getResource("/ui/dark.css");
            if (darkCss != null) {
                alert.getDialogPane().getStylesheets().add(darkCss.toExternalForm());
            }
            alert.showAndWait();

            if (alert.getResult() == ButtonType.YES) {
                Config.getVersionModule().getWorldManager().deleteAllExisting();
            }
        }));


        menu.add(new SeparatorMenuItem());

        menu.add(construct("Redraw nearby chunks", e -> {
            Coordinate2D region = handler.getCursorCoordinates().globalToRegion();
            new Thread(() -> Config.getVersionModule().getWorldManager().drawExistingChunks(region)).start();
        }));

        menu.add(construct("Redraw region", e -> {
            Coordinate2D region = handler.getCursorCoordinates().globalToRegion();
            handler.getRegionHandler().resetRegion(region);
            new Thread(() -> Config.getVersionModule().getWorldManager().drawExistingRegion(region)).start();
        }));


        menu.add(construct("Copy coordinates", e -> {
            Coordinate2D coords = handler.getCursorCoordinates();
            String coordsString = String.format("%d ~ %d", coords.getX(), coords.getZ());
            StringSelection selection = new StringSelection(coordsString);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        }));

        menu.add(new SeparatorMenuItem());

        ImageMode current = RegionImageHandler.getOverrideMode();


        menu.add(new Menu("Render mode", null, renderModes.toArray(new MenuItem[0])));
        menu.add(construct("Settings", e -> GuiManager.loadWindowSettings()));

        menu.add(construct("Save & Exit", e -> {
            GuiManager.saveAndExit();
        }));

        if (Config.isInDevMode()) {
            addDevOptions(menu, handler);
        }
    }

    private void addDevOptions(List<MenuItem> menu, GuiMap handler) {
        menu.add(new SeparatorMenuItem());

        menu.add(construct("Write chunk", e -> {

            CoordinateDim2D chunk = handler.getCursorCoordinates().globalToChunk().addDimension(Config.getVersionModule().overworld());
            CoordinateDim2D region = handler.getCursorCoordinates().globalToRegion().addDimension(Config.getVersionModule().overworld());

            IChunkBinary cb = Config.getVersionModule().createMcaFile(region).getChunkBinary(chunk);

            String filename = "chunkdata.bin";
            FileOutputStream f = new FileOutputStream(filename);
            ObjectOutputStream o = new ObjectOutputStream(f);
            o.writeObject(cb);

            System.out.println("Written chunk " + chunk + " to " + filename);
        }));

        menu.add(construct("Write all chunks as text", e -> {
           Config.toggleWriteChunkNbt();
        }));

        menu.add(construct("Print stats", e -> {
            int regions = Config.getVersionModule().getWorldManager().countActiveRegions();
            int binaryChunks = Config.getVersionModule().getWorldManager().countActiveBinaryChunks();
            int unpasedChunks = Config.getVersionModule().getWorldManager().countQueuedChunks();
            int chunks = Config.getVersionModule().getWorldManager().countActiveChunks();
            int extendedChunks = Config.getVersionModule().getWorldManager().countExtendedChunks();
            int entities = Config.getVersionModule().getWorldManager().countActiveEntities();
            int players = Config.getVersionModule().getWorldManager().countActivePlayers();
            int maps = Config.getVersionModule().getWorldManager().countActiveMaps();
            String imageStats = handler.imageStats();

            System.out.printf("Statistics:" +
                            "\n\tActive regions: %d" +
                            "\n\tActive binary chunks: %d" +
                            "\n\tActive unparsed chunks: %d" +
                            "\n\tActive chunks: %d" +
                            "\n\tActive extended chunks: %d" +
                            "\n\tActive entities: %d" +
                            "\n\tActive players: %d" +
                            "\n\tActive maps: %d" +
                            "\n\tActive region images: %s" +
                            "\n",
                    regions, binaryChunks, unpasedChunks, chunks, extendedChunks, entities, players, maps, imageStats);
        }));

        menu.add(construct("Print chunk events", e -> {
            Config.getVersionModule().printChunkEventLog(handler.getCursorCoordinates().globalToChunk().addDimension(Config.getVersionModule().overworld()));
        }));
    }

    private MenuItem construct(String name, boolean isDisabled, HandleError handler) {
        MenuItem item = construct(name, handler);
        item.setDisable(isDisabled);
        return item;
    }

    private MenuItem construct(String name, HandleError handler) {
        MenuItem item = new MenuItem(name);
        item.addEventHandler(EventType.ROOT, handler);
        return item;
    }
}

interface HandleError extends EventHandler<Event> {
    @Override
    default void handle(Event event) {
        try {
            handleErr(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleErr(Event event) throws IOException;
}