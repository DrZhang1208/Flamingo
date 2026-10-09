package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.data.objects.NowPlayingLyricState

class NativeLyricPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realPageSurvivesHighIndexToEssayToEmptyDocument() {
        val lines = (0..19).map { index ->
            YosLyricLine(index * 1000f, (index + 1) * 1000f,
                listOf(YosLyricToken(index * 1000f, (index + 1) * 1000f, "第${index}行歌词")))
        }
        val state = mutableStateOf(NowPlayingLyricState(trackKey = "song", entries = lines,
            effectivePositionMs = 12000L, generation = 1))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YosLyricView({ state.value }, { 12000 }, object : YosMediaEvent {
                override fun onSeek(position: Int) = Unit
            }, weightLambda = { false }, modifier = Modifier.size(320.dp, 600.dp).testTag("lyrics"),
                renderActive = false, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(500)
        repeat(5) { generation ->
            compose.runOnIdle {
                val text = "这是一首纯音乐的长篇介绍。😀".repeat(2000)
                state.value = NowPlayingLyricState(trackKey = "essay-$generation", entries = listOf(
                    YosLyricLine(0f, 180000f, listOf(YosLyricToken(0f, 180000f, text)))), generation = generation)
            }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("lyrics").assertExists()
            compose.runOnIdle { state.value = NowPlayingLyricState(trackKey = "empty-$generation") }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("No lyrics").assertExists()
            compose.runOnIdle { state.value = NowPlayingLyricState(trackKey = "song-$generation", entries = lines,
                effectivePositionMs = 12000L, generation = generation) }
            compose.mainClock.advanceTimeBy(500)
        }
    }
}
