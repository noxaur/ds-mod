package io.github.noxaur.dsmod.core.contribute

/**
 * What a player agreed to send, collected until it is time to send it: a batch of at most [MAX_BATCH] things, each
 * told apart by [key]. A thing is not collected again while it waits, is on its way, or was delivered, and a report
 * goes out rarely, so a player who agreed to it is asked for little. Call it on the client thread.
 */
open class Batch<T : Any>(private val key: (T) -> Any) {
    private val pending = LinkedHashMap<Any, T>()
    private val sending = HashSet<Any>()
    private val sent = HashSet<Any>()

    /** What was already looked at, as the caller keyed it. A menu resends the same line for every slot, so most calls end here. */
    private val seen = HashSet<Any>()

    /** Whether a batch is already as big as it goes, so there is no point in looking at more. */
    val full get() = pending.size >= MAX_BATCH

    /** True the first time the caller shows [looked], so that what it costs to read the thing is paid once. */
    fun fresh(looked: Any): Boolean {
        if (seen.size >= MAX_SEEN) seen.clear()
        return seen.add(looked)
    }

    /** Collects [thing], unless it is already waiting, on its way, or delivered. */
    fun add(thing: T) {
        val k = key(thing)
        if (k in sent || k in sending || k in pending) return
        pending[k] = thing
    }

    val waiting get() = pending.isNotEmpty()

    private var waitingSince = -1L
    private var lastReport: Long? = null

    /**
     * Whether a report should go at [now], and if so counts it as sent. The first goes [FIRST_MILLIS] after something
     * began to wait, so a short session still reports, and each later one [SPACING_MILLIS] after the one before.
     */
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

    /** Takes what is waiting, to send. Tell [finished] how it went, and until then these are not collected again. */
    fun take(): List<T> {
        val batch = pending.values.toList()
        sending.addAll(pending.keys)
        pending.clear()
        return batch
    }

    /**
     * Ends what [take] began for [batch]. When it was [delivered], it is not collected again this session. Otherwise
     * it waits for the next report, as far as a batch has room, and the rest may be seen again.
     */
    fun finished(batch: List<T>, delivered: Boolean) {
        for (thing in batch) {
            val k = key(thing)
            if (!sending.remove(k)) continue
            if (delivered) sent.add(k) else if (pending.size < MAX_BATCH) pending[k] = thing
        }
        // What found no room must be read again to come back.
        if (!delivered) seen.clear()
    }

    /** Forgets what is waiting, for a player who switched the report off. */
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
    }
}
