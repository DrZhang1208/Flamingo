package yos.music.player.ui.widgets.basic

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Modal sheets draw in a separate window; the Activity's frame-rate vote is insufficient. */
@Composable
internal fun DialogRefreshRate() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val previousViewRate = if (Build.VERSION.SDK_INT >= 35) view.requestedFrameRate else 0f
        val root = view.rootView
        val manager = view.context.getSystemService(WindowManager::class.java)
        var previousWindowRate: Float? = null
        fun applyRate() {
            val display = view.display
            val mode = display?.mode
            val rate = display?.supportedModes
                ?.filter { it.physicalWidth == mode?.physicalWidth && it.physicalHeight == mode?.physicalHeight }
                ?.maxOfOrNull { it.refreshRate }
            if (rate != null) {
                if (window != null) {
                    if (previousWindowRate == null) previousWindowRate = window.attributes.preferredRefreshRate
                    window.attributes = window.attributes.apply { preferredRefreshRate = rate }
                } else {
                    // Material 1.2 sheets use a WindowManager popup rather than
                    // a DialogWindowProvider. Apply the vote to that window too.
                    (root.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                        if (previousWindowRate == null) previousWindowRate = params.preferredRefreshRate
                        // Preserve the existing popup's window configuration.
                        params.preferredRefreshRate = rate
                        manager.updateViewLayout(root, params)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 35) {
                view.requestedFrameRate = rate ?: View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = applyRate()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) applyRate()
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
            previousWindowRate?.let { previous ->
                if (window != null) {
                    window.attributes = window.attributes.apply { preferredRefreshRate = previous }
                } else if (root.isAttachedToWindow) {
                    (root.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                        params.preferredRefreshRate = previous
                        manager.updateViewLayout(root, params)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 35) view.requestedFrameRate = previousViewRate
        }
    }
}
