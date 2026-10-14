package yos.music.player.ui.widgets

internal const val NATIVE_LYRIC_FOCUS_SCALE = 1.06f

/** Reserve growth only at the trailing edge; the leading baseline never moves. */
internal fun nativeLyricFocusTrailingInset(width: Float, visibleInset: Float): Float {
    val usable = (width - 2f * visibleInset).coerceAtLeast(0f)
    return width - visibleInset - usable / NATIVE_LYRIC_FOCUS_SCALE
}
