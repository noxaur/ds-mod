package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Appends one JSON line per event to a new file in [dir]. Events are built and
 * written on a background thread, so [record] costs the caller no disk time.
 */
class CaptureWriter(dir: Path, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    val file: Path = Files.createDirectories(dir)
        .resolve(Instant.ofEpochMilli(clock()).toString().replace(':', '-') + ".jsonl")
    private val out = Files.newBufferedWriter(file)
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "dsmod-capture").apply { isDaemon = true } }

    fun record(type: String, data: () -> JsonObject) {
        val time = clock()
        thread.execute {
            val line = buildJsonObject {
                put("t", time)
                put("type", type)
                put("data", data())
            }
            out.write(line.toString())
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
