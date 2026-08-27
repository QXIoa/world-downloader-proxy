package core.gui.jewel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.Toolkit

// ── Minecraft-style palette ──────────────────────────────────────────────

private val McBg          = Color(0xFF6A6A6A)
private val McBgHover     = Color(0xFF7A7A7A)
private val McBgPressed   = Color(0xFF5A5A5A)
private val McBgDisabled  = Color(0xFF4A4A4A)
private val McLight       = Color(0xFFA0A0A0)
private val McDark        = Color(0xFF404040)
private val McText        = Color(0xFFFFFFFF)
private val McTextShadow  = Color(0xFF3A3A3A)
private val McAccent      = Color(0xFF008943)
private val McAccentHover = Color(0xFF00A050)
private val McAccentLight = Color(0xFF27CE40)
private val McAccentDark  = Color(0xFF064D2A)

// ── Minecraft-style button ───────────────────────────────────────────────

@Composable
fun MinecraftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        !enabled   -> McBgDisabled
        isPressed  -> if (accent) McAccentDark else McBgPressed
        isHovered  -> if (accent) McAccentHover else McBgHover
        accent     -> McAccent
        else       -> McBg
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .shadow(
                elevation = if (isPressed) 1.dp else 3.dp,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .background(bgColor)
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                val b = 2f
                val (topLeftColor, bottomRightColor) = if (isPressed) {
                    McDark to McLight
                } else {
                    McLight to McDark
                }
                drawLine(topLeftColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = b)
                drawLine(topLeftColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = b)
                drawLine(bottomRightColor, Offset(0f, h), Offset(w, h), strokeWidth = b)
                drawLine(bottomRightColor, Offset(w, 0f), Offset(w, h), strokeWidth = b)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) McText else Color(0xFF888888),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(color = McTextShadow, offset = Offset(1f, 1f), blurRadius = 0f)),
        )
    }
}

// ── Help icon (?) with popup ─────────────────────────────────────────────

@Composable
fun HelpIcon(
    helpText: String,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(18.dp)
            .background(
                color = if (isHovered) Color(0xFF555555) else Color(0xFF3D3D3D),
                shape = RoundedCornerShape(50),
            )
            .border(
                width = 1.dp,
                color = if (isHovered) Color(0xFF777777) else Color(0xFF555555),
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { showPopup = !showPopup },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }

    if (showPopup) {
        Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = { showPopup = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(helpText, color = Color(0xFFCCCCCC), fontSize = 12.sp)
            }
        }
    }
}

// ── Labeled row with help ────────────────────────────────────────────────

@Composable
fun LabeledRow(
    label: String,
    helpText: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 13.sp, modifier = Modifier.width(180.dp))
        if (helpText != null) {
            HelpIcon(helpText, modifier = Modifier.padding(end = 8.dp))
        }
        trailing()
    }
}

// ── Checkbox with label + help ───────────────────────────────────────────

@Composable
fun SettingCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckboxRow(
            text = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        if (helpText != null) {
            HelpIcon(helpText, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

// ── Text field with label + help ─────────────────────────────────────────

@Composable
fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    helpText: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Hold TextFieldValue in local state so cursor position, selection and
    // IME composition are preserved across recompositions. Recreating
    // TextFieldValue(text) on every frame discards that info and breaks
    // Alt+A (select all), deletion and cursor movement.
    val tfvState = remember { mutableStateOf(TextFieldValue(value)) }
    // Sync external value changes (e.g. Realms tab setting the server) into the field
    // without clobbering the user's caret/selection when they match.
    if (tfvState.value.text != value) {
        tfvState.value = TextFieldValue(value)
    }

    LabeledRow(
        label = label,
        helpText = helpText,
        modifier = modifier,
    ) {
        TextField(
            value = tfvState.value,
            onValueChange = { tfv ->
                tfvState.value = tfv
                if (tfv.text != value) {
                    onValueChange(tfv.text)
                }
            },
            modifier = Modifier.fillMaxWidth().height(30.dp),
            enabled = enabled,
            outline = Outline.None,
            placeholder = if (placeholder != null) {
                { Text(placeholder, color = Color(0xFF666666), fontSize = 13.sp) }
            } else null,
        )
    }
}

// ── Integer text field ───────────────────────────────────────────────────

@Composable
fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    helpText: String? = null,
    enabled: Boolean = true,
    width: Int = 120,
) {
    val strValue = value.toString()
    val tfvState = remember { mutableStateOf(TextFieldValue(strValue)) }
    if (tfvState.value.text != strValue) {
        tfvState.value = TextFieldValue(strValue)
    }

    LabeledRow(label = label, helpText = helpText) {
        TextField(
            value = tfvState.value,
            onValueChange = { tfv ->
                val filtered = tfv.text.filter { ch -> ch.isDigit() || ch == '-' }
                tfvState.value = tfv.copy(text = filtered)
                filtered.toIntOrNull()?.let {
                    if (it != value) onValueChange(it)
                }
            },
            modifier = Modifier.width(width.dp).height(30.dp),
            enabled = enabled,
            outline = Outline.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ── Long text field ──────────────────────────────────────────────────────

@Composable
fun LongField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    helpText: String? = null,
    enabled: Boolean = true,
    width: Int = 200,
) {
    val strValue = value.toString()
    val tfvState = remember { mutableStateOf(TextFieldValue(strValue)) }
    if (tfvState.value.text != strValue) {
        tfvState.value = TextFieldValue(strValue)
    }

    LabeledRow(label = label, helpText = helpText) {
        TextField(
            value = tfvState.value,
            onValueChange = { tfv ->
                val filtered = tfv.text.filter { ch -> ch.isDigit() || ch == '-' }
                tfvState.value = tfv.copy(text = filtered)
                filtered.toLongOrNull()?.let {
                    if (it != value) onValueChange(it)
                }
            },
            modifier = Modifier.width(width.dp).height(30.dp),
            enabled = enabled,
            outline = Outline.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ── Section card ─────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = McAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        content()
    }
}

// ── Nav-bar textures (loaded once) ───────────────────────────────────────

private var buttonTex: ImageBitmap? = null

private fun loadTex(path: String): ImageBitmap? {
    val url = Thread.currentThread().contextClassLoader.getResource(path)
    if (url != null) {
        val img = Toolkit.getDefaultToolkit().getImage(url)
        val tracker = java.awt.MediaTracker(java.awt.Canvas())
        tracker.addImage(img, 0)
        try { tracker.waitForID(0) } catch (e: InterruptedException) {}
        val w = img.getWidth(null); val h = img.getHeight(null)
        if (w <= 0 || h <= 0) return null
        val buffered = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = buffered.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return buffered.toComposeImageBitmap()
    }
    return null
}

private fun getButtonTex(): ImageBitmap? {
    buttonTex?.let { return it }
    buttonTex = loadTex("ui/icon/button.png")
    return buttonTex
}

// ── Custom tab bar with Minecraft textures ───────────────────────────────

/**
 * Helper: tile an image horizontally to fill a width, keeping the image's
 * aspect ratio. The image is scaled to [dstH] and repeated along X.
 * Must be called from a DrawScope.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.tileHorizontally(
    image: ImageBitmap,
    w: Float,
    h: Float,
) {
    val srcW = image.width.toFloat()
    val srcH = image.height.toFloat()
    val drawW = srcW * (h / srcH)
    val count = (w / drawW).toInt() + 1
    // Overlap each tile by 1px to hide seam artifacts from edge transparency
    for (i in 0 until count) {
        val x = i * drawW
        drawImage(
            image = image,
            srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
            srcSize = androidx.compose.ui.unit.IntSize(srcW.toInt(), srcH.toInt()),
            dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), 0),
            dstSize = androidx.compose.ui.unit.IntSize((drawW + 1f).toInt(), h.toInt()),
        )
    }
}

@Composable
fun TabBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val buttonImg = remember { getButtonTex() }

    // Animated indicator position (0f..1f across the bar)
    val animProgress by animateFloatAsState(
        targetValue = if (tabs.isEmpty()) 0f else selected.toFloat() / tabs.size,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "tabIndicator",
    )
    val tabWidthFraction = if (tabs.isEmpty()) 0f else 1f / tabs.size

    val barHeight = 36.dp
    val stripHeight = 8.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Tab buttons row ──
        // button.png is tiled across the whole bar as a single background.
        // Active tab is brightened, inactive tabs are dimmed with bevels.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(barHeight),
        ) {
            // Background: button.png tiled across full width
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (buttonImg != null) {
                    tileHorizontally(buttonImg, size.width, size.height)
                } else {
                    drawRect(Color(0xFF2B2B2B))
                }
            }

            // Foreground: clickable tab regions
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawWithContent {
                                val w = size.width
                                val h = size.height
                                // Dim inactive tabs
                                if (!isSelected) {
                                    drawRect(Color.Black.copy(alpha = 0.35f))
                                }
                                // Brighten active tab
                                if (isSelected) {
                                    drawRect(Color.White.copy(alpha = 0.08f))
                                }
                                drawContent()
                                // Bevel shading on top of the button texture
                                val b = 2f
                                val (tl, br) = if (isSelected) {
                                    Color(0xFFA0A0A0) to Color(0xFF404040)
                                } else {
                                    Color(0xFF555555) to Color(0xFF222222)
                                }
                                drawLine(tl, Offset(0f, 0f), Offset(w, 0f), strokeWidth = b)
                                drawLine(br, Offset(0f, h), Offset(w, h), strokeWidth = b)
                                if (index == 0) {
                                    drawLine(tl, Offset(0f, 0f), Offset(0f, h), strokeWidth = b)
                                }
                                if (index == tabs.lastIndex) {
                                    drawLine(br, Offset(w, 0f), Offset(w, h), strokeWidth = b)
                                }
                                if (index < tabs.lastIndex) {
                                    drawLine(Color(0xFF333333), Offset(w, 0f), Offset(w, h), strokeWidth = 1f)
                                }
                            }
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) McAccentLight else Color(0xFF999999),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // ── Gradient strip under the navbar ──
        // Base: #d4fc79 ← middle → #96e6a1 (static, full width, symmetric)
        // Overlay: #81ff8a → #64965e (thin, half height, only under active tab,
        // animated, slides smoothly when switching tabs)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stripHeight)
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    if (tabs.isEmpty()) {
                        drawRect(Color(0xFF333333))
                        return@drawWithContent
                    }

                    // Base gradient — #d4fc79 on left, #96e6a1 on right,
                    // gentle blend in the middle (not too sharp, not too flat).
                    val baseBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f  to Color(0xFFD4FC79),
                            0.35f to Color(0xFFD4FC79),
                            0.65f to Color(0xFF96E6A1),
                            1.0f  to Color(0xFF96E6A1),
                        ),
                    )
                    drawRect(baseBrush)

                    // Overlay — thin bar (half the strip height) under active tab
                    val tabW = w * tabWidthFraction
                    val activeStart = w * animProgress
                    val activeEnd = activeStart + tabW
                    val overlayH = h * 0.5f
                    val overlayY = (h - overlayH) / 2f

                    val overlayBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF81FF8A),
                            Color(0xFF64965E),
                        ),
                        startX = activeStart,
                        endX = activeEnd,
                    )
                    drawRect(
                        brush = overlayBrush,
                        topLeft = Offset(activeStart, overlayY),
                        size = androidx.compose.ui.geometry.Size(tabW, overlayH),
                    )
                },
        )
    }
}

// ── Status badge ─────────────────────────────────────────────────────────

@Composable
fun StatusBadge(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = if (isError) Color(0xFFB33A3A) else Color(0xFF2D6E3F)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (isError) Color(0xFFE07070) else Color(0xFF6BCB7E),
            fontSize = 12.sp,
        )
    }
}
