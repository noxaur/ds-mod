package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.unwrapped

class Batch<T : Any>(private val key: (T) -> Any) {
    private val pending = LinkedHashMap<Any, T>()
    private val sending = HashSet<Any>()
    private val sent = HashSet<Any>()

    private val seen = HashSet<Any>()

    val full get() = pending.size >= MAX_BATCH

    fun fresh(looked: Any): Boolean {
        if (seen.size >= MAX_SEEN) seen.clear()
        return seen.add(looked)
    }

    fun add(thing: T) {
        val k = key(thing)
        if (k in sent || k in sending || k in pending) return
        pending[k] = thing
    }

    val waiting get() = pending.isNotEmpty()

    private var waitingSince = -1L
    private var lastReport: Long? = null

    fun due(now: Long): Boolean {
        if (!waiting) {
            waitingSince = -1
            return false
        }
        if (waitingSince < 0) waitingSince = now
        val last = lastReport
        if (if (last == null) now - waitingSince < FIRST_MILLIS else now - last < SPACING_MILLIS) return false
        lastReport = now
        waitingSince = -1
        return true
    }

    fun take(): List<T> {
        val batch = pending.values.toList()
        sending.addAll(pending.keys)
        pending.clear()
        return batch
    }

    fun finished(batch: List<T>, error: Throwable? = null) {
        val done = error == null || refusedForGood(error)
        for (thing in batch) {
            val k = key(thing)
            if (!sending.remove(k)) continue
            if (done) sent.add(k) else if (pending.size < MAX_BATCH) pending[k] = thing
        }
        if (!done) seen.clear()
    }

    fun drop() {
        waitingSince = -1
        pending.clear()
        seen.clear()
    }

    companion object {
        const val FIRST_MILLIS = 2 * 60 * 1000L
        const val SPACING_MILLIS = 20 * 60 * 1000L
        const val MAX_BATCH = 50
        private const val MAX_SEEN = 1024

        private val REFUSED = setOf(400, 404, 409, 413, 422)

        private fun refusedForGood(error: Throwable): Boolean =
            error.unwrapped().let { it is BackendException && it.status in REFUSED }
    }
}
