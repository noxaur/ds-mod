package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.GameEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A capture file that lacks records, because the writer failed on one or did not finish in time. */
class CaptureIncompleteException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The `type` of every line that holds a [GameEvent]. Every other type is a [RawRecord]. */
val eventTypes = GameEvent.serializer().descriptor.getElementDescriptor(1).let { subclasses ->
    (0 until subclasses.elementsCount).map(subclasses::getElementName).toSet()
}

/**
 * One event a capture recorded. [tick] is the client tick that posted it.
 * A capture from before ticks were recorded has none, and a replay then ticks after every event.
 */
data class Captured(val time: Long, val event: GameEvent, val tick: Int? = null)

/**
 * Appends one JSON line per event to a new file in [dir]. Events are built and written on a background
 * thread, which also creates the file, so [record] and the constructor cost the caller no disk time.
 */
class CaptureWriter(
    dir: Path,
    private val closeMillis: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    val file: Path = dir.resolve(Instant.ofEpochMilli(clock()).toString().replace(':', '-') + ".jsonl")
    private val opened = lazy {
        Files.createDirectories(dir)
        Files.newBufferedWriter(file)
    }
    private val out by opened
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "dsmod-capture").apply { isDaemon = true } }

    @Volatile
    private var failure: Throwable? = null

    /**
     * The client tick the next record belongs to. [record] reads it on the client thread and the writer thread
     * only sees the copy, so advancing it is an integer increment and an empty tick writes nothing.
     */
    private var tick = 0

    /** Records an event the features saw, in the form [readCapture] gives back. */
    fun record(time: Long, event: GameEvent) {
        val tick = this.tick
        write { captureLine(time, event, tick) }
    }

    /** Records raw data that no [GameEvent] carries yet. */
    fun record(type: RawRecord, data: () -> JsonObject) {
        val time = clock()
        val tick = this.tick
        write { line(time, type.type, data(), tick) }
    }

    /**
     * The client tick ended, after the events it posted. The next record belongs to the next tick.
     * Call it once from the end of `END_CLIENT_TICK`.
     */
    fun endTick() {
        tick++
    }

    private fun write(line: () -> String) {
        thread.execute {
            attempt {
                out.write(line())
                out.newLine()
                out.flush()
            }
        }
    }

    /** Keeps the first failure for [close] to report. The writer goes on, so one bad record costs one line. */
    private fun attempt(work: () -> Unit) {
        try {
            work()
        } catch (e: Exception) {
            if (failure == null) failure = e
        }
    }

    /**
     * Finishes the file and waits for the writer thread, so call it from a thread that may wait. Throws
     * [CaptureIncompleteException] when a record was lost or the writer is still busy after [closeMillis], because
     * a reader of the file would otherwise take a part of the capture for the whole.
     */
    override fun close() {
        thread.execute { attempt { out.close() } }
        thread.shutdown()
        if (!thread.awaitTermination(closeMillis, TimeUnit.MILLISECONDS)) {
            thread.shutdownNow()
            try {
                if (opened.isInitialized()) out.close()
            } catch (_: IOException) {
                // Already closed by the interrupt. Either way no more is written.
            }
            throw CaptureIncompleteException("The capture's writer was still busy after $closeMillis ms.")
        }
        failure?.let { throw CaptureIncompleteException("A record of the capture was not written.", it) }
    }
}

private fun line(time: Long, type: String, data: JsonObject, tick: Int? = null) = buildJsonObject {
    put("t", time)
    // Absent on a capture recorded before client ticks were stamped, which a replay must not guess.
    if (tick != null) put("tick", tick)
    put("type", type)
    put("data", data)
}.toString()

/** The line of a capture file that holds [event], in the form [readCaptureLine] reads. [tick] is omitted when the capture has none. */
fun captureLine(time: Long, event: GameEvent, tick: Int? = null): String {
    val json = Json.encodeToJsonElement(GameEvent.serializer(), event).jsonObject
    return line(time, json.getValue("type").jsonPrimitive.content, JsonObject(json - "type"), tick)
}

/** The event on a capture [line], or null for a raw record, because no feature can see one. */
fun readCaptureLine(line: String): Captured? {
    val json = Json.parseToJsonElement(line).jsonObject
    val type = json.getValue("type")
    if (type.jsonPrimitive.content !in eventTypes) return null
    val event = JsonObject(json.getValue("data").jsonObject + ("type" to type))
    val tick = (json["tick"] as? JsonPrimitive)?.intOrNull
    return Captured(json.getValue("t").jsonPrimitive.long, Json.decodeFromJsonElement(GameEvent.serializer(), event), tick)
}

/** The events in a capture. Raw records are skipped, because no feature can see them. */
fun readCapture(file: Path): List<Captured> =
    Files.readAllLines(file).filter { it.isNotBlank() }.mapNotNull(::readCaptureLine)
