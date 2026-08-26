package core.gui;

import core.config.Config;
import core.interfaces.IWorldManager;
import core.interfaces.IChunkBinary;
import core.interfaces.IMcaFile;
import core.messages.Messages;


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
    final static String PROMPT_PAUSE = Messages.gui("gui.menu.pause_saving");
    final static String PROMPT_RESUME = Messages.gui("gui.menu.resume_saving");

    final List<MenuItem> renderModes = List.of(
        construct(Messages.gui("gui.menu.render_automatic"), e -> setRenderMode(null, e)),
        construct(Messages.gui("gui.menu.render_surface"), e -> setRenderMode(ImageMode.NORMAL, e)),
        construct(Messages.gui("gui.menu.render_caves"), e -> setRenderMode(ImageMode.CAVES, e))
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

        menu.add(construct(Messages.gui("gui.menu.delete_all"), e -> {
            Alert alert = new Alert(Alert.AlertType.NONE,
                    Messages.gui("gui.menu.delete_confirm_msg"),
                    ButtonType.CANCEL, ButtonType.YES
            );
            GuiManager.addIcon((Stage) alert.getDialogPane().getScene().getWindow());
            alert.setTitle(Messages.gui("gui.menu.delete_confirm_title"));
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

        menu.add(construct(Messages.gui("gui.menu.redraw_nearby"), e -> {
            Coordinate2D region = handler.getCursorCoordinates().globalToRegion();
            new Thread(() -> Config.getVersionModule().getWorldManager().drawExistingChunks(region)).start();
        }));

        menu.add(construct(Messages.gui("gui.menu.redraw_region"), e -> {
            Coordinate2D region = handler.getCursorCoordinates().globalToRegion();
            handler.getRegionHandler().resetRegion(region);
            new Thread(() -> Config.getVersionModule().getWorldManager().drawExistingRegion(region)).start();
        }));


        menu.add(construct(Messages.gui("gui.menu.copy_coords"), e -> {
            Coordinate2D coords = handler.getCursorCoordinates();
            String coordsString = String.format("%d ~ %d", coords.getX(), coords.getZ());
            StringSelection selection = new StringSelection(coordsString);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        }));

        menu.add(new SeparatorMenuItem());

        ImageMode current = RegionImageHandler.getOverrideMode();


        menu.add(new Menu(Messages.gui("gui.menu.render_mode"), null, renderModes.toArray(new MenuItem[0])));
        menu.add(construct(Messages.gui("gui.menu.settings"), e -> GuiManager.loadWindowSettings()));

        menu.add(construct(Messages.gui("gui.menu.save_exit"), e -> {
            GuiManager.saveAndExit();
        }));

        if (Config.isInDevMode()) {
            addDevOptions(menu, handler);
        }
    }

    private void addDevOptions(List<MenuItem> menu, GuiMap handler) {
        menu.add(new SeparatorMenuItem());

        menu.add(construct(Messages.gui("gui.menu.write_chunk"), e -> {

            CoordinateDim2D chunk = handler.getCursorCoordinates().globalToChunk().addDimension(Config.getVersionModule().overworld());
            CoordinateDim2D region = handler.getCursorCoordinates().globalToRegion().addDimension(Config.getVersionModule().overworld());

            IChunkBinary cb = Config.getVersionModule().createMcaFile(region).getChunkBinary(chunk);

            String filename = "chunkdata.bin";
            FileOutputStream f = new FileOutputStream(filename);
            ObjectOutputStream o = new ObjectOutputStream(f);
            o.writeObject(cb);

            System.out.println(Messages.console("console.chunk.written", chunk, filename));
        }));

        menu.add(construct(Messages.gui("gui.menu.write_all_chunks"), e -> {
           Config.toggleWriteChunkNbt();
        }));

        menu.add(construct(Messages.gui("gui.menu.print_stats"), e -> {
            int regions = Config.getVersionModule().getWorldManager().countActiveRegions();
            int binaryChunks = Config.getVersionModule().getWorldManager().countActiveBinaryChunks();
            int unpasedChunks = Config.getVersionModule().getWorldManager().countQueuedChunks();
            int chunks = Config.getVersionModule().getWorldManager().countActiveChunks();
            int extendedChunks = Config.getVersionModule().getWorldManager().countExtendedChunks();
            int entities = Config.getVersionModule().getWorldManager().countActiveEntities();
            int players = Config.getVersionModule().getWorldManager().countActivePlayers();
            int maps = Config.getVersionModule().getWorldManager().countActiveMaps();
            String imageStats = handler.imageStats();

            System.out.println(Messages.console("console.stats.header"));
            System.out.println(Messages.console("console.stats.active_regions", regions));
            System.out.println(Messages.console("console.stats.active_binary_chunks", binaryChunks));
            System.out.println(Messages.console("console.stats.active_unparsed_chunks", unpasedChunks));
            System.out.println(Messages.console("console.stats.active_chunks", chunks));
            System.out.println(Messages.console("console.stats.active_extended_chunks", extendedChunks));
            System.out.println(Messages.console("console.stats.active_entities", entities));
            System.out.println(Messages.console("console.stats.active_players", players));
            System.out.println(Messages.console("console.stats.active_maps", maps));
            System.out.println(Messages.console("console.stats.active_region_images", imageStats));
        }));

        menu.add(construct(Messages.gui("gui.menu.print_chunk_events"), e -> {
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