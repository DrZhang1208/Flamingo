package yos.music.player.ui.widgets.effects

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackgroundAssetStoreTest {
    @Test fun concurrentPageAndPreloadShareOneResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = BackgroundAssetStore<Any>(6, scope)
            val ready = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val requests = List(128) {
                async(Dispatchers.Default) {
                    store.getOrLoad("cover") {
                        calls.incrementAndGet()
                        ready.await()
                        Any()
                    }
                }
            }
            ready.complete(Unit)
            val results = requests.awaitAll()
            assertEquals(1, calls.get())
            results.forEach { assertSame(results.first(), it) }
            assertSame(results.first(), store.peek("cover"))
        } finally { scope.cancel() }
    }

    @Test fun cancellingPreloadDoesNotCancelTheWaitingPage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = BackgroundAssetStore<Any>(6, scope)
            val ready = CompletableDeferred<Unit>()
            val expected = Any()
            var calls = 0
            var cancelled = false
            val preload = async(start = CoroutineStart.UNDISPATCHED) {
                store.getOrLoad("cover") {
                    calls++
                    try { ready.await(); expected }
                    finally { cancelled = !currentCoroutineContext().isActive }
                }
            }
            val page = async(start = CoroutineStart.UNDISPATCHED) {
                store.getOrLoad("cover") { error("duplicate conversion") }
            }
            preload.cancelAndJoin()
            assertFalse(cancelled)
            ready.complete(Unit)
            assertSame(expected, page.await())
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun lastCallerCancellationWaitsForOldConversionBeforeRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = BackgroundAssetStore<Any>(6, scope)
            val cleanup = CompletableDeferred<Unit>()
            var calls = 0
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                store.getOrLoad("cover") {
                    calls++
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { cleanup.await() } }
                }
            }
            first.cancelAndJoin()
            val expected = Any()
            val retry = async(start = CoroutineStart.UNDISPATCHED) {
                store.getOrLoad("cover") { calls++; expected }
            }
            assertEquals(1, calls)
            assertFalse(retry.isCompleted)
            assertNull(store.peek("cover"))
            cleanup.complete(Unit)
            assertSame(expected, withTimeout(2_000) { retry.await() })
            assertEquals(2, calls)
        } finally { scope.cancel() }
    }

    @Test fun failedAndNullLoadsCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = BackgroundAssetStore<Any>(6, scope)
            assertNull(store.getOrLoad("cover") { throw IllegalStateException("decode") })
            assertNull(store.peek("cover"))
            assertNull(store.getOrLoad("cover") { null })
            val expected = Any()
            assertSame(expected, store.getOrLoad("cover") { expected })
        } finally { scope.cancel() }
    }

    @Test fun cacheKeepsOnlyTheMostRecentlyUsedResults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = BackgroundAssetStore<Any>(2, scope)
            val a = store.getOrLoad("a") { Any() }
            store.getOrLoad("b") { Any() }
            assertSame(a, store.peek("a"))
            store.getOrLoad("c") { Any() }
            assertSame(a, store.peek("a"))
            assertNull(store.peek("b"))
            assertNotNull(store.peek("c"))
        } finally { scope.cancel() }
    }
}
