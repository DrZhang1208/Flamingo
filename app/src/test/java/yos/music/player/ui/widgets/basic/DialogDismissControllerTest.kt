package yos.music.player.ui.widgets.basic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DialogDismissControllerTest {
    @Test fun confirmationReadsFormBeforeDismissClearsIt() = runBlocking {
        val hidden = CompletableDeferred<Boolean>()
        var form: String? = "chosen-server"
        var confirmed: String? = null
        val controller = DialogDismissController(this, hide = { hidden.await() }, dismissed = { form = null })
        controller.confirm { confirmed = form }
        yield()
        assertNull(confirmed)
        assertEquals("chosen-server", form)
        hidden.complete(true)
        yield()
        assertEquals("chosen-server", confirmed)
        assertNull(form)
    }

    @Test fun navigationWaitsForHideAndDuplicateClicksAreIgnored() = runBlocking {
        val hidden = CompletableDeferred<Boolean>()
        val events = mutableListOf<String>()
        val controller = DialogDismissController(this,
            hide = { events += "hiding"; hidden.await() },
            dismissed = { events += "dismissed" })
        val dismiss = DialogDismissAction(controller::request)
        dismiss.after { events += "navigate" }
        dismiss.after { events += "duplicate" }
        yield()
        assertEquals(listOf("hiding"), events)
        hidden.complete(true)
        yield()
        assertEquals(listOf("hiding", "dismissed", "navigate"), events)
    }

    @Test fun rejectedHideKeepsDialogAndDoesNotNavigate() = runBlocking {
        val events = mutableListOf<String>()
        val controller = DialogDismissController(this, hide = { false }, dismissed = { events += "dismissed" })
        controller.request { events += "navigate" }
        yield()
        assertTrue(events.isEmpty())
        controller.request { events += "retry" }
        yield()
        assertTrue(events.isEmpty())
    }

    @Test fun ownerCancellationDoesNotRunPendingNavigation() = runBlocking {
        val hidden = CompletableDeferred<Boolean>()
        val events = mutableListOf<String>()
        val owner = launch {
            val controller = DialogDismissController(this, hide = { hidden.await() }, dismissed = { events += "dismissed" })
            controller.request { events += "navigate" }
        }
        yield()
        owner.cancel()
        owner.join()
        hidden.complete(true)
        assertTrue(events.isEmpty())
    }
}
