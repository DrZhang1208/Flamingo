package yos.music.player.ui.widgets.basic

import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal suspend fun <T> withDialogDismissMotion(block: suspend () -> T): T {
    val systemScale = currentCoroutineContext()[MotionDurationScale]
    val dismissScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = (systemScale?.scaleFactor ?: 1f) * 0.5f
    }
    return withContext(dismissScale) { block() }
}
