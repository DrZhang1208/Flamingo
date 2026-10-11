package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DialogScrollContainmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameGestureAtTopStaysExpandedButNextGestureDismisses() {
        var dismissals = 0
        lateinit var sheet: androidx.compose.material3.SheetState
        compose.setContent {
            MaterialTheme {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                OptionDialog(icon = {}, title = "Lyrics", bottomSheetState = sheet,
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
