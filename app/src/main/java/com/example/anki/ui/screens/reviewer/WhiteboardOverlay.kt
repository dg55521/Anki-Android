package com.example.anki.ui.screens.reviewer

import android.os.Build
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.anki.data.model.PressureSensitivity
import com.example.anki.data.model.WhiteboardBrushType
import com.example.anki.ui.theme.FlagBlueColor
import com.example.anki.ui.theme.FlagGreenColor
import com.example.anki.ui.theme.FlagOrangeColor
import com.example.anki.ui.theme.FlagRedColor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class StrokePoint(
    val offset: Offset,
    val pressure: Float,
)

data class WhiteboardStroke(
    val points: List<StrokePoint>,
    val color: Color,
    val baseWidthPx: Float,
    val opacity: Float,
    val brushType: WhiteboardBrushType,
    val pressureEnabled: Boolean,
    val pressureSensitivity: PressureSensitivity,
    val isEraser: Boolean,
)

@Stable
class WhiteboardState {
    val undoStack = mutableStateListOf<WhiteboardStroke>()
    val redoStack = mutableStateListOf<WhiteboardStroke>()
    var activeStroke by mutableStateOf<WhiteboardStroke?>(null)
        internal set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commitActiveStroke() {
        val stroke = activeStroke ?: return
        if (stroke.points.isNotEmpty()) {
            undoStack.add(stroke)
            redoStack.clear()
        }
        activeStroke = null
    }

    fun cancelActiveStroke() {
        activeStroke = null
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val removed = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(removed)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val restored = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(restored)
        }
    }

    fun clear() {
        activeStroke = null
        undoStack.clear()
        redoStack.clear()
    }
}

@Composable
fun rememberWhiteboardState(): WhiteboardState = remember { WhiteboardState() }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WhiteboardOverlay(
    state: WhiteboardState,
    settings: WhiteboardSettings,
    onUpdateStylusOnly: (Boolean) -> Unit,
    onUpdateBrushType: (WhiteboardBrushType) -> Unit,
    onUpdateStrokeWidthDp: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdatePressureEnabled: (Boolean) -> Unit,
    onUpdatePressureSensitivity: (PressureSensitivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var activePenColor by remember { mutableStateOf(FlagBlueColor) }
    var isEraserSelected by remember { mutableStateOf(false) }
    var showBrushPanel by remember { mutableStateOf(false) }
    var hoverPosition by remember { mutableStateOf<Offset?>(null) }
    var hoverIsEraser by remember { mutableStateOf(false) }

    val currentSettings by rememberUpdatedState(settings)
    val currentColor by rememberUpdatedState(activePenColor)
    val currentEraserSelected by rememberUpdatedState(isEraserSelected)
    val baseWidthPx by rememberUpdatedState(with(density) { settings.baseWidthDp.dp.toPx() })

    var activePointerId by remember { mutableIntStateOf(MotionEvent.INVALID_POINTER_ID) }
    var smoothedPressure by remember { mutableFloatStateOf(0.5f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Ink Drawing Surface with Offscreen Compositing so BlendMode.Clear erases ink cleanly without hiding card text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                // S Pen hover preview ring (Air View)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    val hoverChange = event.changes.firstOrNull {
                                        !it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                                    }
                                    if (hoverChange != null) {
                                        hoverPosition = hoverChange.position
                                        hoverIsEraser = currentEraserSelected ||
                                            hoverChange.type == PointerType.Eraser ||
                                            event.buttons.isSecondaryPressed
                                    }
                                }
                                PointerEventType.Exit -> {
                                    hoverPosition = null
                                }
                            }
                        }
                    }
                }
                // Hardware MotionEvent capture for S Pen pressure, historical pressure samples, side button & palm rejection
                .pointerInteropFilter { motionEvent ->
                    val cfg = currentSettings
                    val action = motionEvent.actionMasked
                    val actionIndex = motionEvent.actionIndex

                    fun isStylusTool(toolType: Int): Boolean =
                        toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

                    fun isPointerAllowed(pointerIndex: Int): Boolean {
                        if (!cfg.stylusOnly) return true
                        return isStylusTool(motionEvent.getToolType(pointerIndex))
                    }

                    val isCanceledFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        (motionEvent.flags and MotionEvent.FLAG_CANCELED) == MotionEvent.FLAG_CANCELED

                    when (action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (!isPointerAllowed(0)) {
                                // Allow finger scroll passthrough when S Pen Only mode is active
                                return@pointerInteropFilter false
                            }
                            activePointerId = motionEvent.getPointerId(0)
                            hoverPosition = null
                            showBrushPanel = false

                            val toolType = motionEvent.getToolType(0)
                            val isStylus = isStylusTool(toolType)
                            val rawPressure = if (isStylus) {
                                motionEvent.getPressure(0).takeIf { it > 0f } ?: 0.5f
                            } else {
                                0.55f
                            }
                            smoothedPressure = rawPressure.coerceIn(0.05f, 1.0f)

                            val sideButtonErase = (motionEvent.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                            val effectiveEraser = currentEraserSelected ||
                                toolType == MotionEvent.TOOL_TYPE_ERASER ||
                                sideButtonErase

                            state.activeStroke = WhiteboardStroke(
                                points = listOf(StrokePoint(Offset(motionEvent.getX(0), motionEvent.getY(0)), smoothedPressure)),
                                color = currentColor,
                                baseWidthPx = baseWidthPx,
                                opacity = cfg.opacity,
                                brushType = cfg.brushType,
                                pressureEnabled = cfg.pressureEnabled && isStylus,
                                pressureSensitivity = cfg.pressureSensitivity,
                                isEraser = effectiveEraser,
                            )
                            true
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            // If no active stroke yet and S Pen touches down after a palm/finger, capture the stylus
                            if (activePointerId == MotionEvent.INVALID_POINTER_ID && isPointerAllowed(actionIndex)) {
                                activePointerId = motionEvent.getPointerId(actionIndex)
                                hoverPosition = null
                                showBrushPanel = false

                                val toolType = motionEvent.getToolType(actionIndex)
                                val isStylus = isStylusTool(toolType)
                                val rawPressure = if (isStylus) {
                                    motionEvent.getPressure(actionIndex).takeIf { it > 0f } ?: 0.5f
                                } else {
                                    0.55f
                                }
                                smoothedPressure = rawPressure.coerceIn(0.05f, 1.0f)

                                val sideButtonErase = (motionEvent.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                                val effectiveEraser = currentEraserSelected ||
                                    toolType == MotionEvent.TOOL_TYPE_ERASER ||
                                    sideButtonErase

                                state.activeStroke = WhiteboardStroke(
                                    points = listOf(
                                        StrokePoint(
                                            Offset(motionEvent.getX(actionIndex), motionEvent.getY(actionIndex)),
                                            smoothedPressure,
                                        ),
                                    ),
                                    color = currentColor,
                                    baseWidthPx = baseWidthPx,
                                    opacity = cfg.opacity,
                                    brushType = cfg.brushType,
                                    pressureEnabled = cfg.pressureEnabled && isStylus,
                                    pressureSensitivity = cfg.pressureSensitivity,
                                    isEraser = effectiveEraser,
                                )
                            }
                            activePointerId != MotionEvent.INVALID_POINTER_ID
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val pIdx = motionEvent.findPointerIndex(activePointerId)
                            if (pIdx < 0) return@pointerInteropFilter false
                            val active = state.activeStroke ?: return@pointerInteropFilter false

                            val toolType = motionEvent.getToolType(pIdx)
                            val isStylus = isStylusTool(toolType)
                            val newPoints = active.points.toMutableList()

                            // Append high-frequency historical S Pen samples with hardware pressure
                            val historySize = motionEvent.historySize
                            for (h in 0 until historySize) {
                                val hx = motionEvent.getHistoricalX(pIdx, h)
                                val hy = motionEvent.getHistoricalY(pIdx, h)
                                val hp = if (isStylus) {
                                    motionEvent.getHistoricalPressure(pIdx, h).takeIf { it > 0f } ?: smoothedPressure
                                } else {
                                    0.55f
                                }.coerceIn(0.05f, 1.0f)
                                smoothedPressure = smoothedPressure * 0.35f + hp * 0.65f
                                newPoints.add(StrokePoint(Offset(hx, hy), smoothedPressure))
                            }

                            val rawPressure = if (isStylus) {
                                motionEvent.getPressure(pIdx).takeIf { it > 0f } ?: smoothedPressure
                            } else {
                                0.55f
                            }.coerceIn(0.05f, 1.0f)
                            smoothedPressure = smoothedPressure * 0.35f + rawPressure * 0.65f
                            newPoints.add(StrokePoint(Offset(motionEvent.getX(pIdx), motionEvent.getY(pIdx)), smoothedPressure))

                            state.activeStroke = active.copy(points = newPoints)
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            val liftedId = motionEvent.getPointerId(actionIndex)
                            if (liftedId == activePointerId) {
                                if (isCanceledFlag) {
                                    state.cancelActiveStroke()
                                } else {
                                    state.commitActiveStroke()
                                }
                                activePointerId = MotionEvent.INVALID_POINTER_ID
                                true
                            } else {
                                activePointerId != MotionEvent.INVALID_POINTER_ID
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                                if (isCanceledFlag) {
                                    state.cancelActiveStroke()
                                } else {
                                    state.commitActiveStroke()
                                }
                                activePointerId = MotionEvent.INVALID_POINTER_ID
                                true
                            } else {
                                false
                            }
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                                state.cancelActiveStroke()
                                activePointerId = MotionEvent.INVALID_POINTER_ID
                                true
                            } else {
                                false
                            }
                        }

                        else -> activePointerId != MotionEvent.INVALID_POINTER_ID
                    }
                },
        ) {
            // Offscreen compositing layer for ink + clear-mode erasers
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            ) {
                state.undoStack.forEach { stroke ->
                    drawVariableWidthStroke(stroke)
                }
                state.activeStroke?.let { stroke ->
                    drawVariableWidthStroke(stroke)
                }
            }

            // Optional S Pen hover cursor indicator (drawn above ink layer)
            hoverPosition?.let { pos ->
                val previewRadius = if (hoverIsEraser) {
                    baseWidthPx * 1.1f
                } else {
                    (baseWidthPx * 0.5f).coerceAtLeast(4f)
                }
                val ringColor = if (hoverIsEraser) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                } else {
                    activePenColor.copy(alpha = 0.7f)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ringColor,
                        radius = previewRadius,
                        center = pos,
                        style = Stroke(width = 2.5f),
                    )
                }
            }
        }

        // Floating Toolbar + Expandable Brush Popover anchored cleanly at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = showBrushPanel,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                BrushSettingsPopover(
                    settings = settings,
                    activeColor = activePenColor,
                    onUpdateBrushType = onUpdateBrushType,
                    onUpdateStrokeWidthDp = onUpdateStrokeWidthDp,
                    onUpdateOpacity = onUpdateOpacity,
                    onUpdatePressureEnabled = onUpdatePressureEnabled,
                    onUpdatePressureSensitivity = onUpdatePressureSensitivity,
                )
            }

            Surface(
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.testTag("whiteboard_toolbar"),
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Original 4 color swatches with 48dp accessible touch targets
                    listOf(FlagBlueColor, FlagRedColor, FlagGreenColor, FlagOrangeColor).forEach { c ->
                        val selected = !isEraserSelected && activePenColor == c
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    activePenColor = c
                                    isEraserSelected = false
                                }
                                .testTag("whiteboard_color_${c.value}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }

                    // Brush, size & opacity popover trigger
                    IconButton(
                        onClick = { showBrushPanel = !showBrushPanel },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showBrushPanel) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                        modifier = Modifier.testTag("whiteboard_brush_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = "笔刷与粗细设置",
                            tint = if (showBrushPanel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Eraser tool toggle
                    IconButton(
                        onClick = {
                            isEraserSelected = !isEraserSelected
                            showBrushPanel = false
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isEraserSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                        modifier = Modifier.testTag("whiteboard_eraser_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = "橡皮擦",
                            tint = if (isEraserSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Toggleable S Pen Only (Palm Rejection) mode
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (settings.stylusOnly) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onUpdateStylusOnly(!settings.stylusOnly) }
                            .testTag("whiteboard_spen_only_toggle"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Draw,
                                contentDescription = "仅限 S Pen 防误触",
                                tint = if (settings.stylusOnly) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "S Pen",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (settings.stylusOnly) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    // Undo (撤销上一步)
                    IconButton(
                        onClick = state::undo,
                        enabled = state.canUndo,
                        modifier = Modifier.testTag("whiteboard_undo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "撤销上一步",
                        )
                    }

                    // Redo (下一步)
                    IconButton(
                        onClick = state::redo,
                        enabled = state.canRedo,
                        modifier = Modifier.testTag("whiteboard_redo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "下一步",
                        )
                    }

                    // Clear all
                    IconButton(
                        onClick = state::clear,
                        enabled = state.canUndo || state.canRedo,
                        modifier = Modifier.testTag("whiteboard_clear_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "清空白板",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushSettingsPopover(
    settings: WhiteboardSettings,
    activeColor: Color,
    onUpdateBrushType: (WhiteboardBrushType) -> Unit,
    onUpdateStrokeWidthDp: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdatePressureEnabled: (Boolean) -> Unit,
    onUpdatePressureSensitivity: (PressureSensitivity) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .testTag("whiteboard_brush_popover"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Brush Type row
            Text("笔刷类型", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhiteboardBrushType.entries.forEach { brush ->
                    FilterChip(
                        selected = settings.brushType == brush,
                        onClick = { onUpdateBrushType(brush) },
                        label = { Text(brush.displayName) },
                        modifier = Modifier.testTag("brush_type_${brush.name.lowercase()}"),
                    )
                }
            }

            // Stroke Width slider + live preview dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "线条大小: ${settings.baseWidthDp.roundToInt()} dp",
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val dotSize = settings.baseWidthDp.coerceIn(2f, 24f).dp
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(activeColor.copy(alpha = settings.opacity)),
                    )
                }
            }
            Slider(
                value = settings.baseWidthDp,
                onValueChange = onUpdateStrokeWidthDp,
                valueRange = 2f..22f,
                modifier = Modifier.height(28.dp).testTag("brush_width_slider"),
            )

            // Opacity slider
            Text(
                text = "透明度: ${(settings.opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.opacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.15f..1.0f,
                modifier = Modifier.height(28.dp).testTag("brush_opacity_slider"),
            )

            // Pressure sensitivity toggle & curve
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("S Pen 压感粗细", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = settings.pressureEnabled,
                    onCheckedChange = onUpdatePressureEnabled,
                    modifier = Modifier.testTag("brush_pressure_switch"),
                )
            }

            if (settings.pressureEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PressureSensitivity.entries.forEach { curve ->
                        FilterChip(
                            selected = settings.pressureSensitivity == curve,
                            onClick = { onUpdatePressureSensitivity(curve) },
                            label = { Text(curve.displayName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawVariableWidthStroke(stroke: WhiteboardStroke) {
    val points = stroke.points
    if (points.isEmpty()) return

    val blendMode = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver
    val effectiveOpacity = if (stroke.isEraser) {
        1.0f
    } else {
        val avgPressure = points.map { it.pressure }.average().toFloat().coerceIn(0f, 1f)
        val brushAlpha = stroke.brushType.baseAlphaFactor +
            stroke.brushType.pressureAlphaFactor * avgPressure
        (stroke.opacity * brushAlpha).coerceIn(0.05f, 1.0f)
    }

    val needsSaveLayer = !stroke.isEraser && effectiveOpacity < 0.99f
    if (needsSaveLayer) {
        drawContext.canvas.saveLayer(
            size.toRect(),
            Paint().apply { alpha = effectiveOpacity },
        )
    }

    val drawColor = if (stroke.isEraser) Color.Black else stroke.color
    val cap = if (stroke.brushType == WhiteboardBrushType.MARKER && !stroke.isEraser) {
        StrokeCap.Square
    } else {
        StrokeCap.Round
    }

    fun widthForPressure(rawPressure: Float): Float {
        if (stroke.isEraser) {
            return stroke.baseWidthPx * 2.2f * (0.75f + 0.6f * rawPressure)
        }
        if (!stroke.pressureEnabled) {
            return stroke.baseWidthPx
        }
        val curved = rawPressure.coerceIn(0.05f, 1.0f).pow(stroke.pressureSensitivity.gamma)
        val ratio = stroke.brushType.minWidthRatio +
            (stroke.brushType.maxWidthRatio - stroke.brushType.minWidthRatio) * curved
        return (stroke.baseWidthPx * ratio).coerceAtLeast(1.5f)
    }

    if (points.size == 1) {
        val p = points.first()
        drawCircle(
            color = drawColor,
            radius = widthForPressure(p.pressure) / 2f,
            center = p.offset,
            blendMode = blendMode,
        )
    } else {
        // Quadratic midpoint smoothing with subdivided variable-width segments
        var prevMid = points.first().offset
        var prevPressure = points.first().pressure
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val mid = Offset((p0.offset.x + p1.offset.x) / 2f, (p0.offset.y + p1.offset.y) / 2f)
            val midPressure = (p0.pressure + p1.pressure) / 2f

            val dist = hypot(mid.x - prevMid.x, mid.y - prevMid.y)
            val steps = max(1, (dist / 4f).toInt().coerceAtMost(18))

            var segStart = prevMid
            var segStartP = prevPressure
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val invT = 1f - t
                // Quadratic Bezier point from prevMid -> p0 -> mid
                val qx = invT * invT * prevMid.x + 2f * invT * t * p0.offset.x + t * t * mid.x
                val qy = invT * invT * prevMid.y + 2f * invT * t * p0.offset.y + t * t * mid.y
                val segEnd = Offset(qx, qy)
                val segEndP = prevPressure + (midPressure - prevPressure) * t
                val w = widthForPressure((segStartP + segEndP) / 2f)

                drawLine(
                    color = drawColor,
                    start = segStart,
                    end = segEnd,
                    strokeWidth = w,
                    cap = cap,
                    blendMode = blendMode,
                )
                segStart = segEnd
                segStartP = segEndP
            }
            prevMid = mid
            prevPressure = midPressure
        }
        // Final tail to last point
        val last = points.last()
        drawLine(
            color = drawColor,
            start = prevMid,
            end = last.offset,
            strokeWidth = widthForPressure((prevPressure + last.pressure) / 2f),
            cap = cap,
            blendMode = blendMode,
        )
    }

    if (needsSaveLayer) {
        drawContext.canvas.restore()
    }
}
