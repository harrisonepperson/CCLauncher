package app.cclauncher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cclauncher.R
import app.cclauncher.data.Constants
import app.cclauncher.helper.IconCache
import app.cclauncher.helper.getUserHandleFromString
import app.cclauncher.settings.AppPreference
import app.cclauncher.settings.SwipePieSlice
import app.cclauncher.settings.swipePieIndexForAngle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HINT_INNER_RADIUS_FRACTION = 0.30f

internal data class SwipeHintGeometry(
    val center: Offset,
    val outerRadius: Float,
    val innerRadius: Float,
)

internal sealed interface SwipeHintHit {
    data class Slice(val index: Int) : SwipeHintHit
    data object Center : SwipeHintHit
    data object None : SwipeHintHit
}

internal fun swipeHintGeometry(
    center: Offset,
    size: IntSize,
    density: Density,
): SwipeHintGeometry {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    if (width <= 0f || height <= 0f) return SwipeHintGeometry(center, 0f, 0f)

    val margin = with(density) { 8.dp.toPx() }
    val minDimension = minOf(width, height)
    val maxRadius = ((minDimension - margin * 2f) / 2f).coerceAtLeast(0f)
    val outerRadius = (minDimension * 0.40f).coerceAtMost(maxRadius)
    val innerRadius = outerRadius * HINT_INNER_RADIUS_FRACTION
    val lowX = outerRadius + margin
    val highX = width - outerRadius - margin
    val lowY = outerRadius + margin
    val highY = height - outerRadius - margin

    fun clamp(value: Float, low: Float, high: Float): Float =
        if (low > high) (low + high) / 2f else value.coerceIn(low, high)

    return SwipeHintGeometry(
        center = Offset(
            clamp(center.x, lowX, highX),
            clamp(center.y, lowY, highY),
        ),
        outerRadius = outerRadius,
        innerRadius = innerRadius,
    )
}

internal fun swipeHintHit(
    pointer: Offset,
    geometry: SwipeHintGeometry,
    directionCount: Int,
    slices: List<SwipePieSlice>,
): SwipeHintHit {
    if (geometry.outerRadius <= 0f) return SwipeHintHit.None
    val distance = (pointer - geometry.center).getDistance()
    return when {
        distance <= geometry.innerRadius -> SwipeHintHit.Center
        distance <= geometry.outerRadius -> {
            val degrees = Math.toDegrees(
                atan2(
                    (pointer.y - geometry.center.y).toDouble(),
                    (pointer.x - geometry.center.x).toDouble(),
                )
            ).toFloat()
            val index = swipePieIndexForAngle(degrees, directionCount)
            val action = slices.getOrNull(index)?.action
            if (action != null && action != Constants.SwipeAction.NULL) {
                SwipeHintHit.Slice(index)
            } else {
                SwipeHintHit.None
            }
        }

        else -> SwipeHintHit.None
    }
}

/**
 * Full-screen, long-press swipe hint. Only slices with an assigned action are
 * drawn; tapping the middle opens launcher settings.
 */
@Composable
fun SwipeHintOverlay(
    center: Offset,
    directionCount: Int,
    slices: List<SwipePieSlice>,
    iconPackName: String,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
    appNameColor: Color,
    appNameFontWeight: FontWeight,
    hoveredSliceIndex: Int?,
    centerHovered: Boolean,
    onSliceSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val iconCache = remember { IconCache(context) }
    val sliceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val density = LocalDensity.current
    val contentSizePx = with(density) { 64.dp.toPx() }
    val settingsIconSizePx = with(density) { 40.dp.toPx() }
    val currentOnSliceSelected by rememberUpdatedState(onSliceSelected)
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val safeDirectionCount = directionCount.takeIf { it == 4 || it == 6 || it == 8 } ?: 4

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val overlaySize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val geometry = swipeHintGeometry(center, overlaySize, density)
        val sliceAngle = 360f / safeDirectionCount
        val separatorWidth = with(density) { 6.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    safeDirectionCount,
                    slices,
                    geometry,
                ) {
                    detectTapGestures { tap ->
                        when (val hit = swipeHintHit(tap, geometry, safeDirectionCount, slices)) {
                            is SwipeHintHit.Slice -> currentOnSliceSelected(hit.index)
                            SwipeHintHit.Center -> currentOnSettingsClick()
                            SwipeHintHit.None -> currentOnDismiss()
                        }
                    }
                },
        ) {
            val centerRadius = (geometry.innerRadius - separatorWidth).coerceAtLeast(0f)
            drawCircle(
                color = if (centerHovered) highlightColor else sliceColor,
                radius = centerRadius,
                center = geometry.center,
            )

            repeat(safeDirectionCount) { index ->
                val slice = slices.getOrNull(index) ?: return@repeat
                if (slice.action == Constants.SwipeAction.NULL) return@repeat

                val startAngle = -90f - sliceAngle / 2f + index * sliceAngle
                val endAngle = startAngle + sliceAngle
                val sectorPath = annularSectorPath(
                    center = geometry.center,
                    innerRadius = geometry.innerRadius,
                    outerRadius = geometry.outerRadius,
                    startAngle = startAngle,
                    sweepAngle = sliceAngle,
                )
                val gapRadius = geometry.outerRadius + separatorWidth
                val startGap = radialGapPath(
                    center = geometry.center,
                    radius = gapRadius,
                    angleDegrees = startAngle,
                    width = separatorWidth,
                )
                val endGap = radialGapPath(
                    center = geometry.center,
                    radius = gapRadius,
                    angleDegrees = endAngle,
                    width = separatorWidth,
                )
                val cutStart = Path().apply {
                    op(sectorPath, startGap, PathOperation.Difference)
                }
                val slicePath = Path().apply {
                    op(cutStart, endGap, PathOperation.Difference)
                }
                drawPath(
                    slicePath,
                    color = if (index == hoveredSliceIndex) highlightColor else sliceColor,
                )
            }
        }

        repeat(safeDirectionCount) { index ->
            val slice = slices.getOrNull(index) ?: return@repeat
            if (slice.action == Constants.SwipeAction.NULL) return@repeat

            val midAngle = -90f + index * sliceAngle
            val radians = Math.toRadians(midAngle.toDouble())
            val contentRadius = (geometry.innerRadius + geometry.outerRadius) / 2f
            val x = geometry.center.x + contentRadius * cos(radians).toFloat()
            val y = geometry.center.y + contentRadius * sin(radians).toFloat()

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - contentSizePx / 2f).roundToInt(),
                            (y - contentSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                SwipeHintSliceContent(
                    slice = slice,
                    iconPackName = iconPackName,
                    iconCache = iconCache,
                    showAppIcons = showAppIcons,
                    showAppNames = showAppNames,
                    hintFontSize = hintFontSize,
                    appNameColor = appNameColor,
                    appNameFontWeight = appNameFontWeight,
                )
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (geometry.center.x - settingsIconSizePx / 2f).roundToInt(),
                        (geometry.center.y - settingsIconSizePx / 2f).roundToInt(),
                    )
                }
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SwipeHintSliceContent(
    slice: SwipePieSlice,
    iconPackName: String,
    iconCache: IconCache,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
    appNameColor: Color,
    appNameFontWeight: FontWeight,
) {
    if (slice.action == Constants.SwipeAction.APP && slice.app.packageName.isNotBlank()) {
        SwipeHintAppIcon(
            app = slice.app,
            iconPackName = iconPackName,
            iconCache = iconCache,
            showAppIcons = showAppIcons,
            showAppNames = showAppNames,
            hintFontSize = hintFontSize,
            appNameColor = appNameColor,
            appNameFontWeight = appNameFontWeight,
        )
        return
    }

    val labels = stringArrayResource(R.array.setting_swipeupaction_options)
    val label = labels.getOrNull(slice.action).orEmpty()
    if (label.isNotEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = appNameFontWeight,
                fontSize = (MaterialTheme.typography.labelLarge.fontSize.value * hintFontSize).sp,
            ),
            color = appNameColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwipeHintAppIcon(
    app: AppPreference,
    iconPackName: String,
    iconCache: IconCache,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
    appNameColor: Color,
    appNameFontWeight: FontWeight,
) {
    val context = LocalContext.current
    var icon by remember(app.packageName, app.activityClassName, app.userString) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(app.packageName, app.activityClassName, app.userString, iconPackName) {
        icon = runCatching {
            iconCache.getIcon(
                packageName = app.packageName,
                className = app.activityClassName,
                user = getUserHandleFromString(context, app.userString),
                iconPackName = iconPackName,
            )
        }.getOrNull()
    }

    val loadedIcon = icon
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showAppIcons) {
            if (loadedIcon != null) {
                Image(
                    bitmap = loadedIcon,
                    contentDescription = app.label,
                    modifier = Modifier.size(if (showAppNames) 34.dp else 44.dp),
                )
            } else if (!showAppNames) {
                Text(
                    text = app.label.ifBlank { "App" },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = appNameFontWeight,
                        fontSize = (MaterialTheme.typography.labelLarge.fontSize.value * hintFontSize).sp,
                    ),
                    color = appNameColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showAppNames) {
            Text(
                text = app.label.ifBlank { "App" },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = appNameFontWeight,
                    fontSize = (MaterialTheme.typography.labelSmall.fontSize.value * hintFontSize).sp,
                ),
                color = appNameColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun radialGapPath(
    center: Offset,
    radius: Float,
    angleDegrees: Float,
    width: Float,
): Path {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val offsetX = -dy * width / 2f
    val offsetY = dx * width / 2f
    val tipX = center.x + dx * radius
    val tipY = center.y + dy * radius

    return Path().apply {
        moveTo(center.x + offsetX, center.y + offsetY)
        lineTo(tipX + offsetX, tipY + offsetY)
        lineTo(tipX - offsetX, tipY - offsetY)
        lineTo(center.x - offsetX, center.y - offsetY)
        close()
    }
}

private fun annularSectorPath(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
): Path {
    val path = Path()
    val startRadians = Math.toRadians(startAngle.toDouble())
    val endAngle = startAngle + sweepAngle
    val endRadians = Math.toRadians(endAngle.toDouble())
    val outerRect = Rect(
        center.x - outerRadius,
        center.y - outerRadius,
        center.x + outerRadius,
        center.y + outerRadius,
    )
    val innerRect = Rect(
        center.x - innerRadius,
        center.y - innerRadius,
        center.x + innerRadius,
        center.y + innerRadius,
    )

    path.moveTo(
        center.x + innerRadius * cos(startRadians).toFloat(),
        center.y + innerRadius * sin(startRadians).toFloat(),
    )
    path.lineTo(
        center.x + outerRadius * cos(startRadians).toFloat(),
        center.y + outerRadius * sin(startRadians).toFloat(),
    )
    path.arcTo(outerRect, startAngle, sweepAngle, false)
    path.lineTo(
        center.x + innerRadius * cos(endRadians).toFloat(),
        center.y + innerRadius * sin(endRadians).toFloat(),
    )
    path.arcTo(innerRect, endAngle, -sweepAngle, false)
    path.close()
    return path
}
