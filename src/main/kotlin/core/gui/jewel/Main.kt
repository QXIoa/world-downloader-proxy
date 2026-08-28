package core.gui.jewel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import core.config.Config
import core.gui.GuiManager
import core.gui.jewel.components.SettingsViewModel
import core.gui.jewel.map.ComposeMapViewModel
import core.gui.jewel.map.MapScreen
import core.util.AppVersion
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.*
import org.jetbrains.jewel.ui.ComponentStyling
import java.awt.Taskbar
import java.awt.Toolkit

private fun loadGrassBitmap(): ImageBitmap? {
    return loadIconBitmap("ui/icon/grass.png")
}

private fun loadIconBitmap(path: String): ImageBitmap? {
    val url = Thread.currentThread().contextClassLoader.getResource(path)
    if (url != null) {
        val img = Toolkit.getDefaultToolkit().getImage(url)
        val tracker = java.awt.MediaTracker(java.awt.Canvas())
        tracker.addImage(img, 0)
        try { tracker.waitForID(0) } catch (e: InterruptedException) {}
        val buffered = java.awt.image.BufferedImage(
            img.getWidth(null), img.getHeight(null),
            java.awt.image.BufferedImage.TYPE_INT_ARGB,
        )
        val g = buffered.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return buffered.toComposeImageBitmap()
    }
    return null
}

/**
 * Bottom grass strip — the 240x34 grass.png is drawn upside down (rotated 180°)
 * and tiled horizontally to fill the full window width responsively.
 * The image is scaled to physical pixels so it looks the same on HiDPI displays.
 */
@Composable
fun GrassBar(modifier: Modifier = Modifier) {
    val grass = remember { loadGrassBitmap() }
    if (grass == null) return
    // Use .dp (not .toDp()) so the image displays at its pixel dimensions in
    // density-independent points — on a 2x Retina display 34dp = 68 physical
    // pixels, which is the correct "original" visual size.
    val tileHDp = (grass.height * 0.85f).dp
    Canvas(modifier = modifier.fillMaxWidth().height(tileHDp)) {
        val w = size.width
        val h = size.height
        val drawH = h
        val drawW = grass.width.toFloat() * (drawH / grass.height.toFloat())
        val count = (w / drawW).toInt() + 1
        for (i in 0 until count) {
            val x = i * drawW
            rotate(degrees = 180f, pivot = Offset(x + drawW / 2f, drawH / 2f)) {
                drawImage(
                    image = grass,
                    dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), 0),
                    dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt()),
                )
            }
        }
    }
}

fun main() {
    System.setProperty("apple.awt.application.name", "World Downloader Proxy")
    System.setProperty("com.apple.macos.useScreenMenuBar", "true")

    try {
        if (Taskbar.isTaskbarSupported()) {
            val iconUrl = Thread.currentThread().contextClassLoader.getResource("ui/icon/icon.png")
            if (iconUrl != null) {
                val image = Toolkit.getDefaultToolkit().getImage(iconUrl)
                Taskbar.getTaskbar().setIconImage(image)
            }
        }
    } catch (e: Exception) {
    }

    application {
        bootstrapConfig()

        val vm = remember { SettingsViewModel() }
        LaunchedEffect(Unit) { vm.loadFromConfig() }

        var proxyStarted by remember { mutableStateOf(false) }
        var showAbout by remember { mutableStateOf(false) }
        val mapVm = remember { ComposeMapViewModel() }

        // Update check — runs once on startup. If a newer GitHub release
        // is found, an "Update" tab is shown that the user must dismiss.
        var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
        var updateDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            updateInfo = UpdateChecker.checkForUpdate()
        }

        val windowState = rememberWindowState(width = 800.dp, height = 620.dp)

        val iconPainter: Painter? = remember {
            val iconUrl = Thread.currentThread().contextClassLoader.getResource("ui/icon/icon.png")
            if (iconUrl != null) {
                val awtImage = Toolkit.getDefaultToolkit().getImage(iconUrl)
                val buffered = java.awt.image.BufferedImage(
                    awtImage.getWidth(null), awtImage.getHeight(null),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB
                )
                val g = buffered.createGraphics()
                g.drawImage(awtImage, 0, 0, null)
                g.dispose()
                BitmapPainter(buffered.toComposeImageBitmap())
            } else null
        }

        // About handler — opens Compose dialog instead of Swing
        DisposableEffect(Unit) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    val desktop = java.awt.Desktop.getDesktop()
                    if (desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)) {
                        desktop.setAboutHandler { _ ->
                            showAbout = true
                        }
                    }
                }
            } catch (e: Exception) {
            }
            onDispose { }
        }

        val textStyle = JewelTheme.createDefaultTextStyle()
        val editorStyle = JewelTheme.createEditorTextStyle()
        val themeDefinition = JewelTheme.darkThemeDefinition(
            defaultTextStyle = textStyle,
            editorTextStyle = editorStyle,
        )

        IntUiTheme(
            theme = themeDefinition,
            styling = ComponentStyling.default(),
            swingCompatMode = false,
        ) {
            Window(
                onCloseRequest = {
                    if (proxyStarted) {
                        shutdownProxy()
                        mapVm.shutdown()
                    }
                    exitApplication()
                },
                title = "World Downloader Proxy | ${AppVersion.get()}",
                state = windowState,
                icon = iconPainter,
            ) {
                LaunchedEffect(window) {
                    window.minimumSize = java.awt.Dimension(800, 620)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                if (proxyStarted) {
                    // Proxy is active — show only the map with a single
                    // "World Preview" tab that fills the whole window.
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Nav bar — single non-interactive tab
                        TabBar(
                            tabs = listOf("World Preview"),
                            selected = 0,
                            onSelect = { },
                        )
                        // Map fills the rest of the window including the area
                        // behind the grass bar (which overlays on top of it).
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clipToBounds(),
                        ) {
                            MapScreen(vm = mapVm)
                            // Grass strip overlays the bottom of the map
                            GrassBar(
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                    }
                } else if (updateInfo != null && !updateDismissed) {
                    // Update available — show update screen as a forced tab
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
                        TabBar(
                            tabs = listOf(t("gui.update.tab")),
                            selected = 0,
                            onSelect = { },
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            UpdateScreen(
                                info = updateInfo!!,
                                onDismiss = { updateDismissed = true },
                            )
                        }
                        GrassBar()
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SettingsScreen(
                                vm = vm,
                                onStart = {
                                    vm.saveToConfig()
                                    if (!Config.getInstance().isStarted) {
                                        if (portInUse(vm.portLocal)) {
                                            vm.errorMessages = listOf("Port ${vm.portLocal} is in use!")
                                            return@SettingsScreen
                                        }
                                        // Register the Compose map bridge before starting
                                        GuiManager.setGuiBridge(mapVm)
                                        startProxyFromCompose()
                                        proxyStarted = true
                                        vm.loadFromConfig()
                                    }
                                },
                                onAbout = { showAbout = true },
                            )
                        }
                        // Grass strip at the bottom
                        GrassBar()
                    }
                }

                if (showAbout) {
                    AboutDialog(
                        iconPainter = iconPainter,
                        onClose = { showAbout = false },
                    )
                }
                }
            }
        }
    }
}
