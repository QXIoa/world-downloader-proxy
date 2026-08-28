package core.gui.jewel.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.gui.jewel.MinecraftButton
import org.jetbrains.jewel.ui.component.Text
import java.awt.image.BufferedImage

private fun BufferedImage.toImageBitmap(): ImageBitmap {
    return this.toComposeImageBitmap()
}

private fun loadNavIcon(): ImageBitmap? {
    val url = Thread.currentThread().contextClassLoader.getResource("ui/icon/nav.png")
    if (url != null) {
        val img = java.awt.Toolkit.getDefaultToolkit().getImage(url)
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
 * Loads the default Steve skin from resources and extracts the head (8×8 at
 * offset 8,8), scaled to 16×16. Used as fallback when a player's head skin
 * can't be fetched (non-premium account, API failure, still loading).
 */
private fun loadSteveHead(): ImageBitmap? {
    val url = Thread.currentThread().contextClassLoader.getResource("ui/icon/steve.png")
    if (url != null) {
        try {
            val skin = javax.imageio.ImageIO.read(url)
            if (skin == null) return null
            val headSize = 8
            val head = java.awt.image.BufferedImage(headSize, headSize, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = head.createGraphics()
            g.drawImage(skin, 0, 0, headSize, headSize, 8, 8, 8 + headSize, 8 + headSize, null)
            g.dispose()
            val scaled = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g2 = scaled.createGraphics()
            g2.drawImage(head, 0, 0, 16, 16, null)
            g2.dispose()
            return scaled.toImageBitmap()
        } catch (e: Exception) {
            return null
        }
    }
    return null
}

@Composable
fun MapScreen(
    vm: ComposeMapViewModel,
    modifier: Modifier = Modifier,
) {
    // Animation tick — triggers redraw
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            kotlinx.coroutines.delay(100)
        }
    }

    // Drag state
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    var dragStartCenterX by remember { mutableStateOf(0.0) }
    var dragStartCenterZ by remember { mutableStateOf(0.0) }

    val bgColor = Color(0.16f, 0.16f, 0.16f)
    val navIcon = remember { loadNavIcon() }
    val steveHead = remember { loadSteveHead() }

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Plus, Key.Equals -> {
                                vm.blocksPerPixel = (vm.blocksPerPixel * 0.8).coerceIn(1.0 / 16.0, 256.0)
                                true
                            }
                            Key.Minus -> {
                                vm.blocksPerPixel = (vm.blocksPerPixel * 1.25).coerceIn(1.0 / 16.0, 256.0)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Capture the current center BEFORE unlocking, so
                            // getCenter() still returns the player position
                            // (or the last manual center) instead of a stale
                            // centerX/centerZ from before follow-player.
                            val c = vm.getCenter()
                            dragStartX = offset.x
                            dragStartY = offset.y
                            dragStartCenterX = c.x
                            dragStartCenterZ = c.z
                            vm.centerX = c.x
                            vm.centerZ = c.z
                            vm.lockedToPlayer = false
                        },
                        onDrag = { change, _ ->
                            val dx = (dragStartX - change.position.x) * vm.blocksPerPixel.toFloat()
                            val dz = (dragStartY - change.position.y) * vm.blocksPerPixel.toFloat()
                            vm.centerX = dragStartCenterX + dx
                            vm.centerZ = dragStartCenterZ + dz
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Scroll-to-zoom (mouse wheel)
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                                if (scrollDelta != null) {
                                    // On macOS the scroll delta sign is inverted
                                    // relative to Windows/Linux, so scroll up (zoom in)
                                    // produces a negative y. Flip the factor accordingly.
                                    val factor = if (scrollDelta.y < 0) 0.85 else 1.15
                                    vm.blocksPerPixel = (vm.blocksPerPixel * factor).coerceIn(1.0 / 16.0, 256.0)
                                }
                            }
                        }
                    }
                },
        ) {
            // Read tick to subscribe to periodic redraws (every 100ms).
            // Without this, other players' positions wouldn't update when
            // the local player stands still.
            @Suppress("UNUSED_EXPRESSION")
            tick

            val width = size.width
            val height = size.height

            val center = vm.getCenter()
            val bpp = vm.blocksPerPixel
            val minX = center.x - width * bpp / 2
            val minZ = center.z - height * bpp / 2

            // Draw regions
            val regions = vm.getVisibleRegions(width, height)
            for (region in regions) {
                val rx = (region.regionCoord.x shl 9).toDouble()
                val rz = (region.regionCoord.z shl 9).toDouble()
                val drawX = ((rx - minX) / bpp).toFloat()
                val drawY = ((rz - minZ) / bpp).toFloat()
                val drawSize = (512.0 / bpp).toFloat()

                val img = region.image
                if (img.width > 0 && img.height > 0) {
                    drawImage(
                        image = img.toImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(drawSize.toInt(), drawSize.toInt()),
                    )
                }

                // Overlay
                region.overlay?.let { overlay ->
                    drawImage(
                        image = overlay.toImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(drawSize.toInt(), drawSize.toInt()),
                    )
                }
            }

            // Draw player marker (nav icon, rotated to player direction)
            val px = ((vm.playerX - minX) / bpp).toFloat()
            val pz = ((vm.playerZ - minZ) / bpp).toFloat()
            if (px in -50f..(width + 50) && pz in -50f..(height + 50)) {
                val navIcon = navIcon
                if (navIcon != null) {
                    // Icon points north by default; Minecraft yaw 0 = south, so rotate by yaw + 180
                    val angleDeg = (vm.playerRotation + 180.0).toFloat()
                    val iconSize = 20f
                    rotate(
                        degrees = angleDeg,
                        pivot = Offset(px, pz),
                    ) {
                        drawImage(
                            image = navIcon,
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (px - iconSize / 2).toInt(),
                                (pz - iconSize / 2).toInt(),
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(iconSize.toInt(), iconSize.toInt()),
                        )
                    }
                } else {
                    // Fallback: circle
                    drawCircle(
                        color = Color.White,
                        radius = 6f,
                        center = Offset(px, pz),
                    )
                }
            }

            // Draw other players — head skin from minotar.net (via PlayerHeadCache), fallback to Steve head
            if (core.config.Config.renderOtherPlayers()) {
                for (player in vm.getOtherPlayers()) {
                    val pos = player.position ?: continue
                    val opx = ((pos.x - minX) / bpp).toFloat()
                    val opz = ((pos.z - minZ) / bpp).toFloat()

                    val headSize = 16f
                    val uuid = player.uuidString
                    val headBmp = if (uuid != null) {
                        core.gui.PlayerHeadCache.getHead(uuid)?.toImageBitmap() ?: steveHead
                    } else {
                        steveHead
                    }
                    if (headBmp != null) {
                        drawImage(
                            image = headBmp,
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (opx - headSize / 2).toInt(),
                                (opz - headSize / 2).toInt(),
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(headSize.toInt(), headSize.toInt()),
                        )
                    } else {
                        drawCircle(
                            color = Color(0.6f, 0.95f, 1f, 0.7f),
                            radius = 3f,
                            center = Offset(opx, opz),
                        )
                    }
                }
            }
        }

        // Zoom controls (+ / -) top-right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MinecraftButton(
                text = "+",
                onClick = {
                    vm.blocksPerPixel = (vm.blocksPerPixel * 0.8).coerceIn(1.0 / 16.0, 256.0)
                },
                square = true,
                contentPadding = PaddingValues(0.dp),
            )
            MinecraftButton(
                text = "-",
                onClick = {
                    vm.blocksPerPixel = (vm.blocksPerPixel * 1.25).coerceIn(1.0 / 16.0, 256.0)
                },
                square = true,
                contentPadding = PaddingValues(0.dp),
            )
        }

        // Follow player button top-left
        if (!vm.lockedToPlayer) {
            MinecraftButton(
                text = "Follow player",
                onClick = { vm.lockedToPlayer = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }

        // Cave mode toggle top-left (below follow player) — locked to Caves in Nether
        val caveEnabled = vm.isCaveButtonEnabled()
        MinecraftButton(
            text = if (vm.caveMode) "Cave" else "Normal",
            onClick = { vm.caveMode = !vm.caveMode },
            enabled = caveEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 48.dp),
        )

        // Zoom level indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                text = "Zoom: ${String.format("%.1f", 1.0 / vm.blocksPerPixel)}x",
                color = Color(0xFF888888),
                fontSize = 11.sp,
            )
        }
    }
}
