@file:Suppress("DEPRECATION")

package yos.music.player.code.utils.others

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import androidx.compose.runtime.Stable

@Stable
class Vibrator {
    companion object {
        private fun getVibrator(context: Context): android.os.Vibrator? =
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

        fun click(context: Context) {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        VibrationEffect.EFFECT_CLICK
                    )
                )
            } else {
                vibrator.vibrate(30)
            }
        }

        fun longClick(context: Context) {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        VibrationEffect.EFFECT_HEAVY_CLICK
                    )
                )
            } else {
                vibrator.vibrate(30)
            }
        }

        fun doubleClick(context: Context) {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        VibrationEffect.EFFECT_DOUBLE_CLICK
                    )
                )
            } else {
                vibrator.vibrate(30)
            }
        }
    }
}
