@file:Suppress("DEPRECATION")

package yos.music.player.code.utils.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercise Media3 against Android's real focus manager, with a second focus owner. */
@UnstableApi
class AudioFocusHandlingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var audioFile: File
    private val competingOwner = AudioManager.OnAudioFocusChangeListener { }

    @Before fun startPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Silent PCM keeps the test inaudible while exercising actual playback and focus.
        val pcmSize = 8_000 * 2 * 60
        val wav = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(8_000)
            .putInt(16_000).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(pcmSize).array()
        audioFile = File.createTempFile("audio-focus-", ".wav", context.cacheDir)
        audioFile.writeBytes(wav)
        compose.setContent { }
        compose.runOnIdle {
            player = ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audioFile)))
            player.prepare()
            player.play()
        }
        awaitPlayer { isPlaying }
    }

    @After fun releasePlayback() {
        compose.runOnIdle {
            if (::player.isInitialized) player.release()
            if (::audioManager.isInitialized) audioManager.abandonAudioFocus(competingOwner)
        }
        if (::audioFile.isInitialized) audioFile.delete()
    }

    @Test fun transientInterruptionResumesWithoutReturningToTheApp() {
        takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        awaitPlayer { !isPlaying && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS }
        compose.runOnIdle { assertTrue(player.playWhenReady) }
        releaseCompetingFocus()
        awaitPlayer { isPlaying }
    }

    @Test fun manualPauseDuringTransientInterruptionPreventsRecovery() {
        takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        awaitPlayer { playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS }
        compose.runOnIdle { player.pause() }
        releaseCompetingFocus()
        assertRemainsPaused()
    }

    @Test fun permanentInterruptionWaitsForExplicitPlayAfterTheOtherPlayerStops() {
        takeFocus(AudioManager.AUDIOFOCUS_GAIN)
        awaitPlayer { !playWhenReady && !isPlaying }
        releaseCompetingFocus()
        assertRemainsPaused()
        compose.runOnIdle { player.play() }
        awaitPlayer { isPlaying }
    }

    private fun takeFocus(gain: Int) = compose.runOnIdle {
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            audioManager.requestAudioFocus(competingOwner, AudioManager.STREAM_MUSIC, gain))
    }

    private fun releaseCompetingFocus() = compose.runOnIdle {
        audioManager.abandonAudioFocus(competingOwner)
    }

    private fun awaitPlayer(condition: ExoPlayer.() -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnIdle { player.condition() } }
    }

    private fun assertRemainsPaused() {
        // Allow asynchronous focus callbacks to be delivered before checking playback intent.
        SystemClock.sleep(300)
        compose.runOnIdle {
            assertFalse(player.playWhenReady)
            assertFalse(player.isPlaying)
        }
    }
}
