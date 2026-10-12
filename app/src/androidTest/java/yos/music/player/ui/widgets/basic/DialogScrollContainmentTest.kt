package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DialogScrollContainmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameGestureAtTopStaysExpandedButNextGestureDismisses() {
        assertScrollContainment(scrollContent = true)
    }

    @Test fun lyricListWithoutOuterScrollerKeepsTheSameDismissBoundary() {
        assertScrollContainment(scrollContent = false)
    }

    @Test fun headerAndFooterHaveTheSameResistanceBeforeAndAfterTheListReachesBottom() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        var clicks = 0
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                scope = rememberCoroutineScope()
                listState = rememberLazyListState()
                OptionDialog(icon = { Box(Modifier.size(160.dp, 60.dp).testTag("header")) },
                    title = "Lyrics", scrollContent = false,
                    content = {
                        LazyColumn(Modifier.height(200.dp).testTag("list"), state = listState) {
                            items(60) { Text("Lyric $it", Modifier.height(40.dp).padding(8.dp)) }
                        }
                        Box(Modifier.fillMaxWidth().height(60.dp).testTag("footer").clickable { clicks++ })
                    }, onDismissRequest = { dismissals++ })
            }
        }
        compose.waitForIdle()
        var referencePull: Float? = null
        for (index in listOf(0, 10, 59)) {
            compose.runOnIdle { scope.launch { listState.scrollToItem(index) } }
            compose.waitForIdle()
            for (tag in listOf("header", "footer")) {
                val node = compose.onNodeWithTag(tag)
                val before = node.fetchSemanticsNode().boundsInRoot
                var listIndex = 0
                var listOffset = 0
                compose.runOnIdle {
                    listIndex = listState.firstVisibleItemIndex
                    listOffset = listState.firstVisibleItemScrollOffset
                }
                node.performTouchInput { down(center); moveBy(Offset(0f, -120f)) }
                val held = node.fetchSemanticsNode().boundsInRoot
                val pull = before.top - held.top
                val limit = with(compose.density) { 48.dp.toPx() }
                assertTrue("An outside drag must lift the sheet with resistance at index $index", pull > 1f && pull < limit)
                assertEquals("The whole sheet moves without stretching", before.height, held.height, 1f)
                referencePull?.let { assertEquals("List position must not change outside resistance", it, pull, 1f) }
                referencePull = pull
                compose.runOnIdle {
                    assertEquals(listIndex, listState.firstVisibleItemIndex)
                    assertEquals(listOffset, listState.firstVisibleItemScrollOffset)
                }
                node.performTouchInput { up() }
                compose.waitForIdle()
                assertEquals(before.top, node.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
        }
        compose.onNodeWithTag("footer").performClick()
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(0, dismissals) }
    }

    private fun assertScrollContainment(scrollContent: Boolean) {
        var dismissals = 0
        lateinit var sheet: androidx.compose.material3.SheetState
        compose.setContent {
            MaterialTheme {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                OptionDialog(icon = {}, title = "Lyrics", bottomSheetState = sheet, scrollContent = scrollContent,
                    content = {
                        LazyColumn(Modifier.height(200.dp).testTag("list"), state = rememberLazyListState(initialFirstVisibleItemIndex = 1)) {
                            items(30) { Text("Lyric $it", Modifier.height(40.dp).padding(8.dp)) }
                        }
                    }, onDismissRequest = { dismissals++ })
            }
        }
        compose.waitForIdle()
        val list = compose.onNodeWithTag("list")
        list.performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertEquals(0, dismissals)
            assertEquals(SheetValue.Expanded, sheet.currentValue)
        }
        list.performTouchInput { swipeDown(durationMillis = 100) }
        compose.runOnIdle {
            assertEquals(SheetValue.Hidden, sheet.currentValue)
            assertEquals(1, dismissals)
        }
    }
}
