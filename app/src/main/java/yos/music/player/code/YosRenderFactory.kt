package yos.music.player.code

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.logic.mss.MssAudioProcessor

@OptIn(UnstableApi::class)
class YosRenderFactory(context: Context) : DefaultRenderersFactory(context) {
    private val appContext = context.applicationContext

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        // All audio renderers, including Media3's reflected FFmpeg extension,
        // receive this same sink. The processor converts each supported PCM
        // representation to PCM16 for MSS and converts the result back afterward.
        .setEnableFloatOutput(false)
        .setEnableAudioTrackPlaybackParams(
            enableAudioTrackPlaybackParams && SettingsLibrary.HardwareAudioTrackPlayBackParams
        )
        .setAudioProcessors(arrayOf(MssAudioProcessor(appContext)))
        .build()
}
