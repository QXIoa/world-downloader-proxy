package core.gui.jewel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.Outline
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

// ── Fonts ────────────────────────────────────────────────────────────────

val Font1 = FontFamily(
    androidx.compose.ui.text.platform.ResourceFont(
        name = "ui/fonts/font1.ttf",
        weight = FontWeight.Bold,
    ),
)

val Font2 = FontFamily(
    androidx.compose.ui.text.platform.ResourceFont(
        name = "ui/fonts/font2.otf",
        weight = FontWeight.Normal,
    ),
)

// ── Minecraft-style button ───────────────────────────────────────────────

@Composable
fun MinecraftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    square: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val button2Img = remember { getButton2Tex() }

    // Overlay tint to distinguish states on top of the texture
    val overlayColor = when {
        !enabled   -> Color.Black.copy(alpha = 0.45f)
        isPressed  -> Color.Black.copy(alpha = 0.25f)
        accent     -> McAccent.copy(alpha = 0.25f)
        else       -> Color.Transparent
    }

    // Animate blur on hover
    val blurAlpha by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "btnBlur",
    )

    // Size modifier: square buttons keep fixed size, text buttons wrap content
    val sizeModifier = if (square) {
        Modifier.size(36.dp)
    } else {
        Modifier.height(36.dp).wrapContentWidth()
    }

    // Corner rounding: rounded for text buttons, sharp for square (+/-)
    val cornerModifier = if (square) {
        Modifier
    } else {
        Modifier.clip(RoundedCornerShape(4.dp))
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .then(cornerModifier)
            .shadow(
                elevation = if (isPressed) 1.dp else 3.dp,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .drawWithContent {
                // Draw button2.png texture tiled horizontally to fill button.
                if (button2Img != null) {
                    val srcW = button2Img.width.toFloat()
                    val srcH = button2Img.height.toFloat()
                    val dstH = size.height
                    val scale = dstH / srcH
                    val tileW = srcW * scale
                    val count = (size.width / tileW).toInt() + 1
                    for (i in 0 until count) {
                        val x = i * tileW
                        drawImage(
                            image = button2Img,
                            srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                            srcSize = androidx.compose.ui.unit.IntSize(srcW.toInt(), srcH.toInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), 0),
                            dstSize = androidx.compose.ui.unit.IntSize((tileW + 1f).toInt(), dstH.toInt()),
                        )
                    }
                } else {
                    drawRect(McBg)
                }
                // State overlay
                if (overlayColor != Color.Transparent) {
                    drawRect(overlayColor)
                }
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Blurred texture layer — matchParentSize covers the FULL button
        if (blurAlpha > 0.01f && button2Img != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(14.dp)
                    .graphicsLayer { this.alpha = blurAlpha * 0.9f }
                    .drawWithContent {
                        val srcW = button2Img.width.toFloat()
                        val srcH = button2Img.height.toFloat()
                        val dstH = size.height
                        val scale = dstH / srcH
                        val tileW = srcW * scale
                        val count = (size.width / tileW).toInt() + 1
                        for (i in 0 until count) {
                            val x = i * tileW
                            drawImage(
                                image = button2Img,
                                srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                                srcSize = androidx.compose.ui.unit.IntSize(srcW.toInt(), srcH.toInt()),
                                dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), 0),
                                dstSize = androidx.compose.ui.unit.IntSize((tileW + 1f).toInt(), dstH.toInt()),
                            )
                        }
                    },
            )
        }
        // Text — softWrap=false prevents line breaks, padding adds breathing room
        Text(
            text = text.uppercase(),
            color = if (enabled) McText else Color(0xFF888888),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Font2,
            softWrap = false,
            maxLines = 1,
            style = TextStyle(shadow = Shadow(color = McTextShadow, offset = Offset(1f, 1f), blurRadius = 0f)),
            modifier = Modifier.padding(contentPadding),
        )
    }
}

// ── Help icon (?) with popup ─────────────────────────────────────────────

@Composable
fun HelpIcon(
    helpText: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val button2Img = remember { getButton2Tex() }

    // Animate blur on hover
    val blurAlpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "helpBlur",
    )

    // Simple hover state with short debounce to prevent edge flicker
    var hoverState by remember { mutableStateOf(false) }
    LaunchedEffect(isHovered) {
        if (isHovered) {
            hoverState = true
        } else {
            kotlinx.coroutines.delay(150)
            hoverState = false
        }
    }
    val showPopup = hoverState

    // Outer hover area — slightly larger than the circle to make hover
    // detection more stable near edges.
    Box(
        modifier = modifier
            .size(22.dp)
            .hoverable(interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        // Inner circle — clips texture to a circle
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
        // Texture background — crop center square from button2.png, scale to fill circle
        if (button2Img != null) {
            val srcSize = minOf(button2Img.width, button2Img.height)
            val srcX = (button2Img.width - srcSize) / 2
            val srcY = (button2Img.height - srcSize) / 2
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = button2Img,
                    srcOffset = androidx.compose.ui.unit.IntOffset(srcX, srcY),
                    srcSize = androidx.compose.ui.unit.IntSize(srcSize, srcSize),
                    dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF3D3D3D)))
        }

        // Blurred texture layer on hover
        if (blurAlpha > 0.01f && button2Img != null) {
            val srcSize = minOf(button2Img.width, button2Img.height)
            val srcX = (button2Img.width - srcSize) / 2
            val srcY = (button2Img.height - srcSize) / 2
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(4.dp)
                    .graphicsLayer { this.alpha = blurAlpha * 0.85f },
            ) {
                drawImage(
                    image = button2Img,
                    srcOffset = androidx.compose.ui.unit.IntOffset(srcX, srcY),
                    srcSize = androidx.compose.ui.unit.IntSize(srcSize, srcSize),
                    dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }

        Text("?", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    // Popup on hover — positioned to the right, non-interactive (useSimpleRendering
    // + focusable=false so it doesn't capture pointer events and cause flicker)
    if (showPopup) {
        Popup(
            alignment = Alignment.TopStart,
            offset = androidx.compose.ui.unit.IntOffset(48, -2),
            properties = PopupProperties(
                focusable = false,
                clippingEnabled = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .wrapContentHeight()
            ) {
                // Blurred frosted glass background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2B2B2B).copy(alpha = 0.85f))
                        .blur(12.dp)
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp))
                )
                Text(
                    helpText,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    fontFamily = Font2,
                    modifier = Modifier.padding(12.dp)
                )
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
    fillWidth: Boolean = true,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) {
            if (helpText != null) {
                HelpIcon(helpText, modifier = Modifier.padding(end = 8.dp))
            }
            Text(label, color = Color(0xFFBBBBBB), fontSize = 13.sp, fontFamily = Font2, modifier = if (fillWidth) Modifier.width(180.dp) else Modifier.padding(end = 6.dp))
        }
        trailing()
    }
}

// ── Custom checkbox with green indicator ─────────────────────────────────

@Composable
fun CustomCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Green checkbox indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (!enabled) Color(0xFF333333)
                    else if (checked) Color(0xFF7FD440)
                    else Color(0xFF3D3D3D)
                )
                .border(
                    1.dp,
                    if (!enabled) Color(0xFF555555)
                    else if (checked) Color(0xFF9FE560)
                    else if (isHovered) Color(0xFF777777)
                    else Color(0xFF666666),
                    RoundedCornerShape(3.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color(0xFF1A2E0A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) Color(0xFFBBBBBB) else Color(0xFF888888),
            fontSize = 12.sp,
            fontFamily = Font2,
        )
    }
}

// ── Custom radio button with green indicator ─────────────────────────────

@Composable
fun CustomRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Green radio button indicator (circle)
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (!enabled) Color(0xFF333333)
                    else if (selected) Color(0xFF7FD440)
                    else Color(0xFF3D3D3D)
                )
                .border(
                    1.dp,
                    if (!enabled) Color(0xFF555555)
                    else if (selected) Color(0xFF9FE560)
                    else if (isHovered) Color(0xFF777777)
                    else Color(0xFF666666),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A2E0A))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) Color(0xFFBBBBBB) else Color(0xFF888888),
            fontSize = 12.sp,
            fontFamily = Font2,
        )
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
        if (helpText != null) {
            HelpIcon(helpText, modifier = Modifier.padding(end = 8.dp))
        }
        CustomCheckbox(
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
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
    fillWidth: Boolean = true,
) {
    val strValue = value.toString()
    val tfvState = remember { mutableStateOf(TextFieldValue(strValue)) }
    if (tfvState.value.text != strValue) {
        tfvState.value = TextFieldValue(strValue)
    }

    LabeledRow(label = label, helpText = helpText, fillWidth = fillWidth) {
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

// ── Minecraft-style slider ───────────────────────────────────────────────

/**
 * A Minecraft-styled slider: vertical bars (one per chunk) side by side.
 * Active bars (0..value) are green and taller; inactive bars are dark and shorter.
 * Click or drag anywhere to set the value.
 */
@Composable
fun McSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val min = valueRange.start
    val max = valueRange.endInclusive
    val tickCount = (max - min + 1).toInt()
    val currentTick = value.toInt()

    val activeColor = if (enabled) Color(0xFF6B9F4A) else Color(0xFF444444)
    val pastColor = if (enabled) Color(0xFF3A6B2A) else Color(0xFF333333)
    val inactiveColor = Color(0xFF555555)

    var trackWidthPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(enabled, trackWidthPx) {
                if (!enabled || trackWidthPx == 0f) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val f = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        onValueChange(min + f * (max - min))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        onValueChange(min + f * (max - min))
                    },
                )
            }
            .pointerInput(enabled, trackWidthPx) {
                if (!enabled || trackWidthPx == 0f) return@pointerInput
                detectTapGestures { offset ->
                    val f = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    onValueChange(min + f * (max - min))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Vertical bars — one per chunk, evenly spaced side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until tickCount) {
                val color = when {
                    i == currentTick -> activeColor
                    i < currentTick -> pastColor
                    else -> inactiveColor
                }
                val isActive = i <= currentTick
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(if (isActive) 20.dp else 12.dp)
                        .background(color),
                )
            }
        }
    }
}

@Composable
fun LongField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    helpText: String? = null,
    enabled: Boolean = true,
    width: Int = 200,
    fillWidth: Boolean = true,
) {
    val strValue = value.toString()
    val tfvState = remember { mutableStateOf(TextFieldValue(strValue)) }
    if (tfvState.value.text != strValue) {
        tfvState.value = TextFieldValue(strValue)
    }

    LabeledRow(label = label, helpText = helpText, fillWidth = fillWidth) {
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
private var button2Tex: ImageBitmap? = null

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

private fun getButton2Tex(): ImageBitmap? {
    button2Tex?.let { return it }
    button2Tex = loadTex("ui/icon/button2.png")
    return button2Tex
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
        // Active/hovered tab gets a blurred copy of the texture behind the text
        // (frosted glass effect). Text: bold gray inactive, bold white active.
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
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val showBlur = isSelected || isHovered

                    // Animate blur alpha so it fades in/out smoothly on hover
                    val blurAlpha by animateFloatAsState(
                        targetValue = if (showBlur) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                        label = "tabBlur",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onSelect(index) },
                    ) {
                        // Blurred texture layer — fades in on active/hover
                        if (blurAlpha > 0.01f && buttonImg != null) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(2.dp)
                                    .graphicsLayer { this.alpha = blurAlpha },
                            ) {
                                tileHorizontally(buttonImg, size.width, size.height)
                            }
                        }
                        // Thin bottom bar + left separator
                        // Active: light gray bar; inactive: dark
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.matchParentSize(),
                        ) {
                            val barColor = if (isSelected) Color(0xFFCCCCCC) else Color(0xFF333333)
                            // Bottom bar
                            drawLine(
                                barColor,
                                Offset(0f, size.height - 3f),
                                Offset(size.width, size.height - 3f),
                                strokeWidth = 6f,
                            )
                            // Right separator (between tabs) — skip on last tab
                            if (index < tabs.lastIndex) {
                                drawLine(
                                    barColor,
                                    Offset(size.width - 3f, 0f),
                                    Offset(size.width - 3f, size.height),
                                    strokeWidth = 6f,
                                )
                            }
                        }
                        // Text on top (always sharp)
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Font1,
                            modifier = Modifier.align(Alignment.Center),
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
