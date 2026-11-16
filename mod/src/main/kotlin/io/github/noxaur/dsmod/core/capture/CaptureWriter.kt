package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.GameEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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

    /** Records an event the features saw, in the form [readCapture] gives back. */
    fun record(time: Long, event: GameEvent) = write { captureLine(time, event) }

    /** Records raw data that no [GameEvent] carries yet. */
    fun record(type: RawRecord, data: () -> JsonObject) {
        val time = clock()
        write { line(time, type.type, data()) }
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

private fun line(time: Long, type: String, data: JsonObject) = buildJsonObject {
    put("t", time)
    put("type", type)
    put("data", data)
}.toString()

/** The line of a capture file that holds [event], in the form [readCaptureLine] reads. */
fun captureLine(time: Long, event: GameEvent): String {
    val json = Json.encodeToJsonElement(GameEvent.serializer(), event).jsonObject
    return line(time, json.getValue("type").jsonPrimitive.content, JsonObject(json - "type"))
}

/** The event on a capture [line] with its time, or null for a raw record, because no feature can see one. */
fun readCaptureLine(line: String): Pair<Long, GameEvent>? {
    val json = Json.parseToJsonElement(line).jsonObject
    val type = json.getValue("type")
    if (type.jsonPrimitive.content !in eventTypes) return null
    val event = JsonObject(json.getValue("data").jsonObject + ("type" to type))
    return json.getValue("t").jsonPrimitive.long to Json.decodeFromJsonElement(GameEvent.serializer(), event)
}

/** The events in a capture, each with its time. Raw records are skipped, because no feature can see them. */
fun readCapture(file: Path): List<Pair<Long, GameEvent>> =
    Files.readAllLines(file).filter { it.isNotBlank() }.mapNotNull(::readCaptureLine)
