package io.github.noxaur.dragen.core.capture

import io.github.noxaur.dragen.core.game.GameEvent
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

class CaptureIncompleteException(message: String, cause: Throwable? = null) : IOException(message, cause)

val eventTypes = GameEvent.serializer().descriptor.getElementDescriptor(1).let { subclasses ->
    (0 until subclasses.elementsCount).map(subclasses::getElementName).toSet()
}

data class Captured(val time: Long, val event: GameEvent, val tick: Int? = null)

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
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "dragen-capture").apply { isDaemon = true } }

    @Volatile
    private var failure: Throwable? = null

    private var tick = 0

    fun record(time: Long, event: GameEvent) {
        val tick = this.tick
        write { captureLine(time, event, tick) }
    }

    fun record(type: RawRecord, data: () -> JsonObject) {
        val time = clock()
        val tick = this.tick
        write { line(time, type.type, data(), tick) }
    }

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

    private fun attempt(work: () -> Unit) {
        try {
            work()
        } catch (e: Exception) {
            if (failure == null) failure = e
        }
    }

    override fun close() {
        thread.execute { attempt { out.close() } }
        thread.shutdown()
        if (!thread.awaitTermination(closeMillis, TimeUnit.MILLISECONDS)) {
            thread.shutdownNow()
            val busy = CaptureIncompleteException("The capture's writer was still busy after $closeMillis ms.")
            try {
                if (opened.isInitialized()) out.close()
            } catch (e: IOException) {
                busy.addSuppressed(e)
            }
            throw busy
        }
        failure?.let { throw CaptureIncompleteException("A record of the capture was not written.", it) }
    }
}

private fun line(time: Long, type: String, data: JsonObject, tick: Int? = null) = buildJsonObject {
    put("t", time)
    if (tick != null) put("tick", tick)
    put("type", type)
    put("data", data)
}.toString()

fun captureLine(time: Long, event: GameEvent, tick: Int? = null): String {
    val json = Json.encodeToJsonElement(GameEvent.serializer(), event).jsonObject
    return line(time, json.getValue("type").jsonPrimitive.content, JsonObject(json - "type"), tick)
}

fun readCaptureLine(line: String): Captured? {
    val json = Json.parseToJsonElement(line).jsonObject
    val type = json.getValue("type")
    if (type.jsonPrimitive.content !in eventTypes) return null
    val event = JsonObject(json.getValue("data").jsonObject + ("type" to type))
    val tick = (json["tick"] as? JsonPrimitive)?.intOrNull
    return Captured(json.getValue("t").jsonPrimitive.long, Json.decodeFromJsonElement(GameEvent.serializer(), event), tick)
}
