package yos.music.player

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

abstract class BaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableHighRefreshRate()
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            val modes = display?.supportedModes ?: return
            val maxRefreshRate = modes.maxOfOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxRefreshRate
            }
        }
    }
}
