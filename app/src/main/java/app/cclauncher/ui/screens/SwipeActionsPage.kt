package app.cclauncher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cclauncher.R
import app.cclauncher.data.Constants
import app.cclauncher.helper.IconCache
import app.cclauncher.helper.getUserHandleFromString
import app.cclauncher.settings.AppPreference
import app.cclauncher.settings.swipePieIndexForAngle
import app.cclauncher.ui.components.SettingsItem
import app.cclauncher.ui.components.SettingsSection
import app.cclauncher.ui.components.SettingsToggle
import app.cclauncher.ui.dialogs.DropdownSettingDialog
import app.cclauncher.ui.dialogs.SliderSettingDialog
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val INNER_RADIUS_FRACTION = 0.30f

data class SwipePieSliceUi(
    val action: Int = Constants.SwipeAction.NULL,
    val app: AppPreference = AppPreference(),
    val existingActionField: String? = null,
    val existingAppField: String? = null,
)

@Composable
fun SwipeActionsPage(
    directionCount: Int,
    slices: List<SwipePieSliceUi>,
    iconPackName: String,
    showHintsOnHold: Boolean,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
    onDirectionCountChange: (Int) -> Unit,
    onShowHintsOnHoldChange: (Boolean) -> Unit,
    onShowAppIconsChange: (Boolean) -> Unit,
    onShowAppNamesChange: (Boolean) -> Unit,
    onHintFontSizeChange: (Float) -> Unit,
    onSliceActionSelected: (Int, Int) -> Unit,
) {
    val directionOptions = listOf(4, 6, 8)
    val actionLabels = stringArrayResource(R.array.setting_swipeupaction_options)

    var showCountDialog by remember { mutableStateOf(false) }
    var showCenterDialog by remember { mutableStateOf(false) }
    var showHintFontSizeDialog by remember { mutableStateOf(false) }
    var actionSliceIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSection(title = stringResource(R.string.configure_swipe_actions)) {
            SettingsItem(
                title = stringResource(R.string.number_of_swipe_directions),
                subtitle = directionCount.toString(),
                onClick = { showCountDialog = true },
            )

            Text(
                text = stringResource(R.string.tap_a_direction_to_configure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                SwipePie(
                    directionCount = directionCount,
                    slices = slices,
                    iconPackName = iconPackName,
                    showAppIcons = showAppIcons,
                    showAppNames = showAppNames,
                    hintFontSize = hintFontSize,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f),
                    onSliceClick = { actionSliceIndex = it },
                    onCenterClick = { showCenterDialog = true },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.swipe_hints)) {
            SettingsToggle(
                title = stringResource(R.string.show_swipe_hints_on_hold),
                isChecked = showHintsOnHold,
                onCheckedChange = onShowHintsOnHoldChange,
            )
            SettingsToggle(
                title = stringResource(R.string.show_app_icons),
                isChecked = showAppIcons,
                enabled = showHintsOnHold,
                onCheckedChange = onShowAppIconsChange,
            )
            SettingsToggle(
                title = stringResource(R.string.show_app_names),
                isChecked = showAppNames,
                enabled = showHintsOnHold,
                onCheckedChange = onShowAppNamesChange,
            )
            SettingsItem(
                title = stringResource(R.string.hint_font_size),
                subtitle = String.format(Locale.getDefault(), "%.1fx", hintFontSize),
                enabled = showHintsOnHold && showAppNames,
                onClick = { showHintFontSizeDialog = true },
            )
        }
    }

    if (showHintFontSizeDialog) {
        SliderSettingDialog(
            title = stringResource(R.string.hint_font_size),
            currentValue = hintFontSize,
            min = 0.5f,
            max = 2.0f,
            step = 0.1f,
            onDismiss = { showHintFontSizeDialog = false },
            onValueSelected = { size ->
                showHintFontSizeDialog = false
                onHintFontSizeChange(size)
            },
        )
    }

    if (showCountDialog) {
        DropdownSettingDialog(
            title = stringResource(R.string.number_of_swipe_directions),
            options = directionOptions.map { it.toString() },
            selectedIndex = directionOptions.indexOf(directionCount).coerceAtLeast(0),
            onDismiss = { showCountDialog = false },
            onOptionSelected = { index ->
                showCountDialog = false
                directionOptions.getOrNull(index)?.let(onDirectionCountChange)
            },
        )
    }

    if (showCenterDialog) {
        DropdownSettingDialog(
            title = stringResource(R.string.swipe_action),
            options = listOf(stringResource(R.string.open_cclauncher_settings)),
            selectedIndex = 0,
            note = stringResource(R.string.center_hold_action_locked_note),
            confirmOnly = true,
            onDismiss = { showCenterDialog = false },
            onOptionSelected = { showCenterDialog = false },
        )
    }

    val sliceIndex = actionSliceIndex
    if (sliceIndex != null) {
        val slice = slices.getOrNull(sliceIndex)
        if (slice == null) {
            actionSliceIndex = null
        } else {
            DropdownSettingDialog(
                title = stringResource(R.string.swipe_action),
                options = actionLabels.toList(),
                selectedIndex = slice.action.coerceIn(0, actionLabels.lastIndex),
                onDismiss = { actionSliceIndex = null },
                onOptionSelected = { action ->
                    actionSliceIndex = null
                    onSliceActionSelected(sliceIndex, action)
                },
            )
        }
    }
}

@Composable
private fun SwipePie(
    directionCount: Int,
    slices: List<SwipePieSliceUi>,
    iconPackName: String,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
    onSliceClick: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconCache = remember { IconCache(context) }
    val sliceColor = MaterialTheme.colorScheme.surfaceVariant
    val currentOnSliceClick by rememberUpdatedState(onSliceClick)
    val currentOnCenterClick by rememberUpdatedState(onCenterClick)
    val safeDirectionCount = directionCount.takeIf { it == 4 || it == 6 || it == 8 } ?: 4

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
        val contentSize = (diameter * 0.20f).coerceIn(44.dp, 76.dp)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(safeDirectionCount) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = min(size.width, size.height) / 2f
                        val innerRadius = outerRadius * INNER_RADIUS_FRACTION
                        val distance = (tap - center).getDistance()
                        if (distance <= innerRadius) {
                            currentOnCenterClick()
                        } else if (distance <= outerRadius) {
                            val degrees = Math.toDegrees(
                                atan2(
                                    (tap.y - center.y).toDouble(),
                                    (tap.x - center.x).toDouble(),
                                )
                            ).toFloat()
                            currentOnSliceClick(
                                swipePieIndexForAngle(degrees, safeDirectionCount)
                            )
                        }
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = min(size.width, size.height) / 2f
            val innerRadius = outerRadius * INNER_RADIUS_FRACTION
            val sliceAngle = 360f / safeDirectionCount
            val separatorWidth = 6.dp.toPx()

            repeat(safeDirectionCount) { index ->
                val startAngle = -90f - sliceAngle / 2f + index * sliceAngle
                val endAngle = startAngle + sliceAngle
                val sectorPath = annularSectorPath(
                    center = center,
                    innerRadius = innerRadius,
                    outerRadius = outerRadius,
                    startAngle = startAngle,
                    sweepAngle = sliceAngle,
                )
                val gapRadius = outerRadius + separatorWidth
                val startGap = radialGapPath(
                    center = center,
                    radius = gapRadius,
                    angleDegrees = startAngle,
                    width = separatorWidth,
                )
                val endGap = radialGapPath(
                    center = center,
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
                drawPath(slicePath, color = sliceColor)
            }

            val centerRadius = (innerRadius - separatorWidth).coerceAtLeast(0f)
            drawCircle(
                color = sliceColor,
                radius = centerRadius,
                center = center,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.open_cclauncher_settings),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        repeat(safeDirectionCount) { index ->
            val slice = slices.getOrNull(index) ?: SwipePieSliceUi()
            val sliceAngle = 360f / safeDirectionCount
            val midAngle = -90f + index * sliceAngle
            val radians = Math.toRadians(midAngle.toDouble())
            val contentRadius = (diameter / 2) * (1f + INNER_RADIUS_FRACTION) / 2f
            val dx = contentRadius * cos(radians).toFloat()
            val dy = contentRadius * sin(radians).toFloat()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dx, y = dy)
                    .size(contentSize),
                contentAlignment = Alignment.Center,
            ) {
                SwipeSliceContent(
                    slice = slice,
                    iconPackName = iconPackName,
                    iconCache = iconCache,
                    showAppIcons = showAppIcons,
                    showAppNames = showAppNames,
                    hintFontSize = hintFontSize,
                )
            }
        }
    }
}

@Composable
private fun SwipeSliceContent(
    slice: SwipePieSliceUi,
    iconPackName: String,
    iconCache: IconCache,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
) {
    if (slice.action == Constants.SwipeAction.NULL) {
        // Tint with the settings background so the plus looks punched out of the slice.
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.background,
            modifier = Modifier.size(28.dp),
        )
        return
    }

    if (slice.action == Constants.SwipeAction.APP && slice.app.packageName.isNotBlank()) {
        SwipeSliceAppIcon(
            app = slice.app,
            iconPackName = iconPackName,
            iconCache = iconCache,
            showAppIcons = showAppIcons,
            showAppNames = showAppNames,
            hintFontSize = hintFontSize,
        )
        return
    }

    val labels = stringArrayResource(R.array.setting_swipeupaction_options)
    val label = labels.getOrNull(slice.action).orEmpty()
    if (label.isNotEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = (MaterialTheme.typography.labelMedium.fontSize.value * hintFontSize).sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwipeSliceAppIcon(
    app: AppPreference,
    iconPackName: String,
    iconCache: IconCache,
    showAppIcons: Boolean,
    showAppNames: Boolean,
    hintFontSize: Float,
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
                    modifier = Modifier.size(if (showAppNames) 32.dp else 44.dp),
                )
            } else if (!showAppNames) {
                Text(
                    text = app.label.ifBlank { "App" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = (MaterialTheme.typography.labelMedium.fontSize.value * hintFontSize).sp,
                    ),
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
                    fontSize = (MaterialTheme.typography.labelSmall.fontSize.value * hintFontSize).sp,
                ),
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
