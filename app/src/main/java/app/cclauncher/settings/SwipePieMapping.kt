package app.cclauncher.settings

import app.cclauncher.data.Constants

/**
 * One slice of the swipe-action pie, resolved against the persisted settings.
 *
 * Slices that map onto the original four per-direction settings expose their
 * field names so the settings UI can edit them in place. Extra 6/8-direction
 * slices come from [SwipePieConfig.slices] and have no backing schema field.
 */
data class SwipePieSlice(
    val action: Int = Constants.SwipeAction.NULL,
    val app: AppPreference = AppPreference(),
    val existingActionField: String? = null,
    val existingAppField: String? = null,
)

private data class SwipeFieldRef(
    val actionField: String,
    val appField: String,
)

// Order matches the pie: top, right, bottom, left.
private val oneFingerSwipeRefs = listOf(
    SwipeFieldRef("swipeUpAction", "swipeUpApp"),
    SwipeFieldRef("swipeRightAction", "swipeRightApp"),
    SwipeFieldRef("swipeDownAction", "swipeDownApp"),
    SwipeFieldRef("swipeLeftAction", "swipeLeftApp"),
)

private val twoFingerSwipeRefs = listOf(
    SwipeFieldRef("twoFingerSwipeUpAction", "twoFingerSwipeUpApp"),
    SwipeFieldRef("twoFingerSwipeRightAction", "twoFingerSwipeRightApp"),
    SwipeFieldRef("twoFingerSwipeDownAction", "twoFingerSwipeDownApp"),
    SwipeFieldRef("twoFingerSwipeLeftAction", "twoFingerSwipeLeftApp"),
)

fun swipePieDirectionCount(config: SwipePieConfig): Int =
    config.directionCount.takeIf { it == 4 || it == 6 || it == 8 } ?: 4

/**
 * Maps the slice index shown by the pie onto the slice that should run.
 *
 * For 4/6/8 directions the cardinal slices stay backed by the original swipe
 * settings; diagonals (in the 8-direction layout) and the extra 6-direction
 * slices use the persisted pie configuration.
 */
fun swipePieSlice(settings: AppSettings, twoFinger: Boolean, index: Int): SwipePieSlice {
    val config = if (twoFinger) settings.twoFingerSwipePie else settings.oneFingerSwipePie
    val count = swipePieDirectionCount(config)
    if (index !in 0 until count) return SwipePieSlice()

    val refIndex = existingSwipeRefIndex(count, index)
    if (refIndex != null) {
        val refs = if (twoFinger) twoFingerSwipeRefs else oneFingerSwipeRefs
        val ref = refs[refIndex]
        return SwipePieSlice(
            action = swipeFieldAction(settings, ref.actionField),
            app = swipeFieldApp(settings, ref.appField),
            existingActionField = ref.actionField,
            existingAppField = ref.appField,
        )
    }

    val slice = config.slices["$count:$index"] ?: SwipePieSliceConfig()
    return SwipePieSlice(action = slice.action, app = slice.app)
}

/**
 * Converts a drag angle in screen coordinates into a pie slice index.
 *
 * Slice 0 is centred at the top of the pie and indices increase clockwise,
 * matching the order the pie renders its slices in.
 */
fun swipePieIndexForAngle(angleDegrees: Float, directionCount: Int): Int {
    val count = directionCount.takeIf { it == 4 || it == 6 || it == 8 } ?: 4
    val sliceAngle = 360f / count
    val clockwiseFromTop = (angleDegrees + 90f + 360f) % 360f
    return ((clockwiseFromTop + sliceAngle / 2f) / sliceAngle).toInt() % count
}

private fun existingSwipeRefIndex(count: Int, index: Int): Int? = when (count) {
    4 -> index
    6 -> when (index) {
        0 -> 0
        3 -> 2
        else -> null
    }
    8 -> when (index) {
        0 -> 0
        2 -> 1
        4 -> 2
        6 -> 3
        else -> null
    }
    else -> null
}

private fun swipeFieldAction(settings: AppSettings, field: String): Int = when (field) {
    "swipeUpAction" -> settings.swipeUpAction
    "swipeRightAction" -> settings.swipeRightAction
    "swipeDownAction" -> settings.swipeDownAction
    "swipeLeftAction" -> settings.swipeLeftAction
    "twoFingerSwipeUpAction" -> settings.twoFingerSwipeUpAction
    "twoFingerSwipeRightAction" -> settings.twoFingerSwipeRightAction
    "twoFingerSwipeDownAction" -> settings.twoFingerSwipeDownAction
    "twoFingerSwipeLeftAction" -> settings.twoFingerSwipeLeftAction
    else -> Constants.SwipeAction.NULL
}

private fun swipeFieldApp(settings: AppSettings, field: String): AppPreference = when (field) {
    "swipeUpApp" -> settings.swipeUpApp
    "swipeRightApp" -> settings.swipeRightApp
    "swipeDownApp" -> settings.swipeDownApp
    "swipeLeftApp" -> settings.swipeLeftApp
    "twoFingerSwipeUpApp" -> settings.twoFingerSwipeUpApp
    "twoFingerSwipeRightApp" -> settings.twoFingerSwipeRightApp
    "twoFingerSwipeDownApp" -> settings.twoFingerSwipeDownApp
    "twoFingerSwipeLeftApp" -> settings.twoFingerSwipeLeftApp
    else -> AppPreference()
}
