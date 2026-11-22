package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.Serializable

class Metrics(private val nanoTime: () -> Long = System::nanoTime) {
    @Serializable
    data class Timer(val count: Long = 0, val totalNanos: Long = 0, val maxNanos: Long = 0)

    @Serializable
    data class Failure(val count: Long, val first: String)

    @Serializable
    data class Snapshot(val timers: Map<String, Timer>, val counters: Map<String, Long>, val failures: Map<String, Failure>)

    private class Timing(var count: Long = 0, var total: Long = 0, var max: Long = 0)

    private val timings = HashMap<String, Timing>()
    private val counters = HashMap<String, Long>()
    private val failures = HashMap<String, Failure>()

    var onUnparsed: ((feature: String, sample: String) -> Unit)? = null

    // @keep A sink that does I/O must hand the snapshot to its own thread
    val sinks = ArrayList<(Snapshot) -> Unit>()

    inline fun time(name: String, block: () -> Unit) {
        val start = start()
        try {
            block()
        } catch (e: Exception) {
            fail(name, e)
        }
        stop(name, start)
    }

    fun count(name: String, by: Long = 1) {
        counters[name] = (counters[name] ?: 0) + by
    }

    fun fail(name: String, error: Exception) {
        failures[name] = Failure((failures[name]?.count ?: 0) + 1, failures[name]?.first ?: error.stackTraceToString())
    }

    fun snapshot() = Snapshot(
        timings.mapValues { (_, timing) -> Timer(timing.count, timing.total, timing.max) },
        counters.toMap(),
        failures.toMap(),
    )

    fun flush() {
        val snapshot = snapshot()
        timings.clear()
        counters.clear()
        failures.clear()
        sinks.forEach { it(snapshot) }
    }

    @PublishedApi
    internal fun start() = nanoTime()

    @PublishedApi
    internal fun stop(name: String, start: Long) {
        val elapsed = nanoTime() - start
        val timing = timings.getOrPut(name, ::Timing)
        timing.count++
        timing.total += elapsed
        if (elapsed > timing.max) timing.max = elapsed
    }
}
