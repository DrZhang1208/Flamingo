package yos.music.player.code

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.MediaRouter2
import android.os.Build

class SystemMediaControlResolver(private val context: Context) {
    /** False lets the caller show its own live route picker instead of silently doing nothing. */
    fun intentSystemMediaDialog(): Boolean {
        // Prefer the public API on every manufacturer, including Xiaomi and Samsung.
        if (Build.VERSION.SDK_INT >= 34 && runCatching {
                MediaRouter2.getInstance(context).showSystemOutputSwitcher()
            }.getOrDefault(false)) return true

        val vendorActivity = when {
            Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ->
                "miui.systemui.plugin" to "miui.systemui.miplay.MiPlayDetailActivity"
            Build.MANUFACTURER.contains("samsung", ignoreCase = true) ->
                "com.samsung.android.mdx.quickboard" to "com.samsung.android.mdx.quickboard.view.MediaActivity"
            else -> null
        }
        if (vendorActivity != null && startIntent(Intent().setClassName(
                vendorActivity.first, vendorActivity.second))) return true

        val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        val systemHandler = context.packageManager.queryIntentActivities(panel, 0).firstOrNull {
            val info = it.activityInfo
            info != null && info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.exported
        } ?: return false
        panel.setClassName(systemHandler.activityInfo.packageName, systemHandler.activityInfo.name)
        return startIntent(panel)
    }

    private fun startIntent(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
