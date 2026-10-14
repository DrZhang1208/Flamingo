package yos.music.player.code

import android.content.Context
import android.media.MediaRouter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class PlaybackOutputDevice(
    val name: String,
    val selected: Boolean,
    val enabled: Boolean,
    val select: () -> Boolean
)

/** Observe media routes, rather than inferring the output from paired Bluetooth devices. */
internal class PlaybackAudioOutput(private val context: Context) {
    private val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
    var devices by mutableStateOf<List<PlaybackOutputDevice>>(emptyList())
        private set
    var selectedName by mutableStateOf("")
        private set
    var externalOutput by mutableStateOf(false)
        private set

    private val callback = object : MediaRouter.SimpleCallback() {
        override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refresh()
        override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refresh()
    }

    fun start() {
        router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, callback)
        refresh()
    }

    fun stop() = router.removeCallback(callback)

    fun refresh() {
        val selected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        selectedName = selected.getName(context).toString()
        externalOutput = selected != router.defaultRoute
        devices = (0 until router.routeCount).map(router::getRouteAt)
            .filter { it.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_AUDIO != 0 && (it.isEnabled || it == selected) }
            .map { route ->
                PlaybackOutputDevice(route.getName(context).toString(), route == selected, route.isEnabled) {
                    // A device can disappear while the dialog is open; never select a stale route.
                    val available = (0 until router.routeCount).any { router.getRouteAt(it) == route }
                    if (!available || !route.isEnabled) false else runCatching {
                        router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, route)
                        refresh()
                        true
                    }.getOrDefault(false)
                }
            }
    }
}
