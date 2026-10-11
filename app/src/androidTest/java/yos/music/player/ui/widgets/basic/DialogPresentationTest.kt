package yos.music.player.ui.widgets.basic

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DialogPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationRunsOnlyAfterTheSheetIsHidden() {
        lateinit var sheet: SheetState
        val events = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                OptionDialog(icon = {}, title = "Song", bottomSheetState = sheet,
                    content = { dismiss ->
                        Text("Artist", Modifier.testTag("artist").clickable {
                            dismiss.after {
                                assertEquals(SheetValue.Hidden, sheet.currentValue)
                                events += "navigate"
                            }
                        })
                    }, onDismissRequest = { events += "dismissed" })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("artist").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { assertEquals(listOf("dismissed", "navigate"), events) }
    }

    @Test fun popupWindowAndItsDrawingViewRequestTheDisplayRefreshRate() {
        lateinit var drawingView: View
        compose.setContent {
            MaterialTheme {
                OptionDialog(icon = {}, title = "Lyrics", scrollContent = false,
                    content = { drawingView = LocalView.current; Text("Select lyrics") },
                    onDismissRequest = {})
            }
        }
        compose.runOnIdle {
            val display = drawingView.display ?: return@runOnIdle
            val mode = display.mode
            val expected = display.supportedModes.filter {
                it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight
            }.maxOf { it.refreshRate }
            val params = drawingView.rootView.layoutParams as WindowManager.LayoutParams
            assertEquals(expected, params.preferredRefreshRate, 0.01f)
            if (Build.VERSION.SDK_INT >= 35) assertEquals(expected, drawingView.requestedFrameRate, 0.01f)
        }
    }

    @Test fun listEdgesFadeIntoSheetSurfaceAndMiddleRetainsSelectionColor() {
        compose.setContent {
            Box(Modifier.size(120.dp).testTag("fade")
                .dialogListFade(Color.White).background(Color.Red))
        }
        val pixels = compose.onNodeWithTag("fade").captureToImage().toPixelMap()
        val x = pixels.width / 2
        assertTrue(pixels[x, 0].green > 0.9f)
        assertTrue(pixels[x, pixels.height - 1].green > 0.9f)
        assertTrue(pixels[x, pixels.height / 2].green < 0.01f)
    }
}
