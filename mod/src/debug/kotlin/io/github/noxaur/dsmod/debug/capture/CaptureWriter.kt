package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.game.GameEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The `type` of every line that holds a [GameEvent]. Any other type is a raw record that only a reader of the file uses. */
private val eventTypes = GameEvent.serializer().descriptor.getElementDescriptor(1).let { subclasses ->
    (0 until subclasses.elementsCount).map(subclasses::getElementName).toSet()
}

/**
 * Appends one JSON line per event to a new file in [dir]. Events are built and
 * written on a background thread, so [record] costs the caller no disk time.
 */
class CaptureWriter(dir: Path, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    val file: Path = Files.createDirectories(dir)
        .resolve(Instant.ofEpochMilli(clock()).toString().replace(':', '-') + ".jsonl")
    private val out = Files.newBufferedWriter(file)
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "dsmod-capture").apply { isDaemon = true } }

    /** Records an event the features saw, in the form [readCapture] gives back. */
    fun record(time: Long, event: GameEvent) = write { captureLine(time, event) }

    /** Records raw data that no [GameEvent] carries yet. */
    fun record(type: String, data: () -> JsonObject) {
        check(type !in eventTypes) { "'$type' is a GameEvent. Post it to Game, and the capture records it from there." }
        val time = clock()
        write { line(time, type, data()) }
    }

    private fun write(line: () -> String) {
        thread.execute {
            out.write(line())
            out.newLine()
            out.flush()
        }
    }

    override fun close() {
        thread.shutdown()
        thread.awaitTermination(5, TimeUnit.SECONDS)
        out.close()
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
