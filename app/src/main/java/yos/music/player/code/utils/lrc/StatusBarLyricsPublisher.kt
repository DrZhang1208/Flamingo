package yos.music.player.code.utils.lrc

import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.ResourceUtils.getDrawable
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import cn.lyric.getter.api.tools.Tools
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricPush
import com.hchen.superlyricapi.SuperLyricTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import java.util.Locale

data class LyricTrackInfo(
    val title: String,
    val artist: String,
    val mediaId: String,
    val mediaUri: String,
    val durationMs: Long
)

/** Service-owned lyric clock and all external lyric publication channels. */
@Suppress("DEPRECATION")
class StatusBarLyricsPublisher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trackKeyProvider: () -> String?,
    private val positionProvider: () -> Long,
    private val playingProvider: () -> Boolean,
    private val trackInfoProvider: () -> LyricTrackInfo?,
    private val mediaSessionPublisher: (line: String?, subtitle: String?, lyricInfo: String?) -> Unit,
    private val tickerPublisher: (String?) -> Unit
) {
    private var job: Job? = null
    private val lyriconPublisher = LyriconLyricsPublisher(context)

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val lyricApi = runCatching { API() }.getOrNull()
            val icon = runCatching {
                Tools.drawableToBase64(getDrawable(R.drawable.flamingo_icon_notification)!!)
            }.getOrDefault("")
            var hooked = false
            var superLyricHooked = false
            var lastHookCheck = 0L
            var lastPublishedTrack: String? = null
            var lastPublishedLine = -1
            var lastPublishedGeneration = -1
            var lyricInfo: String? = null
            var lastTicker: String? = null
            var colorOsProviderUri: String? = null
            var colorOsProviderPayload: String? = null
            var colorOsProviderGeneration = 0L

            while (isActive) {
                val trackKey = trackKeyProvider()
                LyricsController.updatePosition(trackKey, positionProvider().toFloat())
                val now = System.currentTimeMillis()
                if (now - lastHookCheck >= HOOK_CHECK_INTERVAL_MS) {
                    hooked = runCatching { lyricApi?.hasEnable == true }.getOrDefault(false)
                    superLyricHooked = SuperLyricTool.isEnabled
                    if (SettingsLibrary.StatusBarLyricHooked != hooked) SettingsLibrary.StatusBarLyricHooked = hooked
                    if (SettingsLibrary.SuperLyricAPIHooked != superLyricHooked) {
                        SettingsLibrary.SuperLyricAPIHooked = superLyricHooked
                    }
                    lastHookCheck = now
                }

                val state = LyricsController.currentState()
                lyriconPublisher.update(
                    enabled = SettingsLibrary.LyriconEnabled,
                    track = trackInfoProvider(),
                    state = state,
                    positionMs = positionProvider(),
                    playing = playingProvider(),
                    showTranslation = SettingsLibrary.NowPlayingTranslation
                )
                if (trackKey != lastPublishedTrack) {
                    lastPublishedTrack = trackKey
                    lastPublishedLine = -1
                    lastPublishedGeneration = -1
                    lyricInfo = null
                    mediaSessionPublisher(null, null, null)
                    tickerPublisher(null)
                    lastTicker = null
                }

                if (state.trackKey == trackKey && state.generation != lastPublishedGeneration) {
                    val trackInfo = trackInfoProvider()
                    lyricInfo = if (trackInfo != null && state.entries.isNotEmpty()) {
                        buildColorOsLyricInfo(trackInfo, state.entries, state.generation.toLong())
                    } else null
                    val providerEnabled = SettingsLibrary.ColorOsLockScreenLyricEnabled &&
                        SettingsLibrary.ColorOsLockScreenLyricProviderEnabled
                    if (providerEnabled && trackInfo != null) {
                        when {
                            lyricInfo == null && colorOsProviderPayload != null -> {
                                colorOsProviderGeneration += 1L
                                publishColorOs("trackChanged", trackInfo, null, colorOsProviderGeneration)
                                colorOsProviderUri = trackInfo.mediaUri
                                colorOsProviderPayload = null
                            }
                            lyricInfo != null && (
                                colorOsProviderUri != trackInfo.mediaUri || colorOsProviderPayload != lyricInfo
                            ) -> {
                                if (colorOsProviderGeneration <= 0L) colorOsProviderGeneration = 1L
                                publishColorOs("lyricReady", trackInfo, lyricInfo, colorOsProviderGeneration)
                                colorOsProviderUri = trackInfo.mediaUri
                                colorOsProviderPayload = lyricInfo
                            }
                        }
                    } else {
                        colorOsProviderUri = null
                        colorOsProviderPayload = null
                    }
                    lastPublishedGeneration = state.generation
                    lastPublishedLine = -1
                }

                if (
                    state.trackKey == trackKey && state.currentLineIndex >= 0 &&
                    state.currentLineIndex != lastPublishedLine
                ) {
                    val line = state.entries.getOrNull(state.currentLineIndex)
                    if (line != null && !line.isCountdown) {
                        if (hooked && SettingsLibrary.StatusBarLyricEnabled) {
                            runCatching {
                                lyricApi?.sendLyric(
                                    line.text,
                                    extra = ExtraData().apply {
                                        customIcon = icon.isNotEmpty()
                                        base64Icon = icon
                                        val duration = (line.endMs - line.startMs).toInt()
                                        if (duration > 0) delay = duration
                                    }
                                )
                            }
                        }
                        if (superLyricHooked && SettingsLibrary.SuperLyricAPIEnabled) {
                            runCatching {
                                val data = SuperLyricData()
                                    .setLyric(line.text)
                                    .setPackageName(context.packageName)
                                    .setBase64Icon(icon)
                                val duration = (line.endMs - line.startMs).toInt()
                                if (duration > 0) data.setDelay(duration)
                                if (line.translation.isNotBlank()) data.setTranslation(line.translation)
                                SuperLyricPush.onSuperLyric(data)
                            }
                        }
                        if (SettingsLibrary.MediaSessionLyricEnabled) {
                            val subtitle = if (
                                SettingsLibrary.MediaSessionLyricTranslationEnabled && line.translation.isNotBlank()
                            ) line.translation else trackInfoProvider()?.let { "${it.title} - ${it.artist}" }
                            mediaSessionPublisher(line.text, subtitle, lyricInfo)
                        } else {
                            mediaSessionPublisher(null, null, lyricInfo)
                        }
                    }
                    lastPublishedLine = state.currentLineIndex
                }
                val ticker = state.entries.getOrNull(state.currentLineIndex)
                    ?.takeIf { SettingsLibrary.TickerLyricEnabled && !it.isCountdown }
                    ?.text
                    ?.takeIf(String::isNotBlank)
                if (ticker != lastTicker) {
                    tickerPublisher(ticker)
                    lastTicker = ticker
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { API().clearLyric() }
        runCatching { SuperLyricPush.onStop(SuperLyricData().setPackageName(context.packageName)) }
        mediaSessionPublisher(null, null, null)
        tickerPublisher(null)
        lyriconPublisher.stop()
        SettingsLibrary.StatusBarLyricHooked = false
        SettingsLibrary.SuperLyricAPIHooked = false
    }

    private fun publishColorOs(eventType: String, info: LyricTrackInfo, lyricInfo: String?, generation: Long) {
        runCatching {
            val intent = Intent(COLOR_OS_ACTION)
                .setPackage("com.android.systemui")
                .putExtra("protocolVersion", 4)
                .putExtra("source", "lyricprovider/flamingo")
                .putExtra("playerPackage", context.packageName)
                .putExtra("senderPackage", context.packageName)
                .putExtra("senderKind", "provider")
                .putExtra("capabilities", "trackGeneration,currentTrackAuthority,translationToggle")
                .putExtra("identityConfidence", "currentTrack")
                .putExtra("eventType", eventType)
                .putExtra("requestId", "${info.mediaId}:$generation:$eventType")
                .putExtra("mediaId", info.mediaId)
                .putExtra("mediaUri", info.mediaUri)
                .putExtra("songName", info.title)
                .putExtra("artist", info.artist)
                .putExtra("duration", info.durationMs)
                .putExtra("trackGeneration", generation)
                .putExtra("capturedAt", System.currentTimeMillis())
            if (!lyricInfo.isNullOrBlank()) {
                intent.putExtra("lyricInfo", lyricInfo)
                    .putExtra("lyric", buildTimedLyrics(LyricsController.currentState().entries, false))
                    .putExtra("rawLyric", buildTimedLyrics(LyricsController.currentState().entries, false))
                    .putExtra("translationLyric", buildTimedLyrics(LyricsController.currentState().entries, true))
            }
            context.sendBroadcast(intent)
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 100L
        const val HOOK_CHECK_INTERVAL_MS = 2_000L
        const val COLOR_OS_ACTION = "io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_DIRECT_V4"
    }
}

internal fun buildTimedLyrics(entries: List<YosLyricLine>, translation: Boolean): String =
    entries.asSequence()
        .filterNot(YosLyricLine::isCountdown)
        .mapNotNull { line ->
            val text = if (translation) line.translation else line.text
            text.takeIf(String::isNotBlank)?.let { "[${formatLrcTimestamp(line.startMs.toLong())}]$it" }
        }
        .joinToString("\n")

internal fun buildColorOsLyricInfo(
    track: LyricTrackInfo,
    entries: List<YosLyricLine>,
    generation: Long
): String {
    val raw = buildTimedLyrics(entries, false)
    val translation = buildTimedLyrics(entries, true)
    return "{" + listOf(
        "\"songName\":\"${jsonEscape(track.title)}\"",
        "\"artist\":\"${jsonEscape(track.artist)}\"",
        "\"songId\":\"${jsonEscape(track.mediaId)}\"",
        "\"lyric\":\"${jsonEscape(raw)}\"",
        "\"rawLyric\":\"${jsonEscape(raw)}\"",
        "\"translationLyric\":\"${jsonEscape(translation)}\"",
        "\"sessionGeneration\":$generation"
    ).joinToString(",") + "}"
}

private fun formatLrcTimestamp(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d.%03d", safe / 60_000, (safe / 1_000) % 60, safe % 1_000)
}

private fun jsonEscape(value: String): String = buildString(value.length + 16) {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
}
