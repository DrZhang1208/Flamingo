package yos.music.player.ui.widgets.basic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Close first, then navigate or open another dialog without unmounting mid-animation. */
class DialogDismissAction internal constructor(private val request: (() -> Unit) -> Unit) : () -> Unit {
    override fun invoke() = request {}
    fun after(action: () -> Unit) = request(action)
}

internal class DialogDismissController(
    private val scope: CoroutineScope,
    private val hide: suspend () -> Boolean,
    private val dismissed: () -> Unit
) {
    private var closing = false

    fun request(after: () -> Unit) = close(before = {}, after = after)

    // Confirmation callbacks may read form state that onDismiss clears.
    // Preserve their original order while still waiting for the hide animation.
    fun confirm(action: () -> Unit) = close(before = action, after = {})

    private fun close(before: () -> Unit, after: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            try {
                if (hide()) {
                    before()
                    dismissed()
                    after()
                }
            } finally {
                closing = false
            }
        }
    }
}
