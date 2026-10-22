package yos.music.player.ui.widgets.effects

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded LRU plus one conversion per key. Work lives only while it has callers. */
internal class BackgroundAssetStore<V : Any>(
    private val capacity: Int,
    private val scope: CoroutineScope
) {
    init { require(capacity > 0) }

    private class Flight<V>(val task: Deferred<V?>, var callers: Int = 0)
    private val lock = Any()
    private val cached = LinkedHashMap<String, V>(capacity, 0.75f, true)
    private val pending = mutableMapOf<String, Flight<V>>()

    fun peek(key: String): V? = synchronized(lock) { cached[key] }

    suspend fun getOrLoad(key: String, load: suspend () -> V?): V? {
        while (true) {
            currentCoroutineContext().ensureActive()
            scope.coroutineContext.ensureActive()
            val (flight, joined) = synchronized(lock) {
                cached[key]?.let { return it }
                val flight = pending[key] ?: startFlight(key, load)
                val joined = !flight.task.isCancelled
                if (joined) flight.callers++
                flight to joined
            }
            if (!joined) {
                // A cancelled CPU conversion may still be unwinding. Do not start
                // another conversion for this key until it has actually finished.
                flight.task.join()
                synchronized(lock) {
                    if (pending[key] === flight) pending.remove(key)
                }
                continue
            }
            try {
                return flight.task.await()
            } finally {
                synchronized(lock) {
                    flight.callers--
                    if (flight.callers == 0 && !flight.task.isCompleted) flight.task.cancel()
                }
            }
        }
    }

    // Called under lock; lazy start prevents completion before pending is populated.
    private fun startFlight(key: String, load: suspend () -> V?): Flight<V> {
        val task = scope.async(start = CoroutineStart.LAZY) {
            val loadContext = currentCoroutineContext()
            val result = try {
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Failed loads are not cached; the next request may retry.
            }
            loadContext.ensureActive()
            if (result != null) synchronized(lock) {
                loadContext.ensureActive()
                cached[key] = result
                if (cached.size > capacity) cached.remove(cached.keys.first())
            }
            result
        }
        val flight = Flight(task)
        pending[key] = flight
        task.invokeOnCompletion {
            synchronized(lock) {
                if (pending[key] === flight) pending.remove(key)
            }
        }
        return flight
    }
}
