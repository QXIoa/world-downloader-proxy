package core.gui.jewel.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
                            vm.lockedToPlayer = false
                            dragStartX = offset.x
                            dragStartY = offset.y
                            val c = vm.getCenter()
                            dragStartCenterX = c.x
                            dragStartCenterZ = c.z
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

            // Draw other players
            if (core.config.Config.renderOtherPlayers()) {
                for (player in vm.getOtherPlayers()) {
                    val pos = player.position ?: continue
                    val opx = ((pos.x - minX) / bpp).toFloat()
                    val opz = ((pos.z - minZ) / bpp).toFloat()
                    drawCircle(
                        color = Color(0.6f, 0.95f, 1f, 0.7f),
                        radius = 3f,
                        center = Offset(opx, opz),
                    )
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
                modifier = Modifier.size(32.dp),
                contentPadding = PaddingValues(0.dp),
            )
            MinecraftButton(
                text = "-",
                onClick = {
                    vm.blocksPerPixel = (vm.blocksPerPixel * 1.25).coerceIn(1.0 / 16.0, 256.0)
                },
                modifier = Modifier.size(32.dp),
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

        // Status bar at bottom
        if (vm.statusMsg.isNotEmpty() || vm.errorVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = if (vm.errorVisible) "Error — check error log tab" else vm.statusMsg,
                    color = if (vm.errorVisible) Color(0xFFE07070) else Color.White,
                    fontSize = 12.sp,
                )
            }
        }

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
