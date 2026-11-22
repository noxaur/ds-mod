package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SavedFiles(
    private val dir: Path,
    private val game: Game,
    startWriter: () -> ExecutorService = { Executors.newSingleThreadExecutor { Thread(it, "dsmod-save").apply { isDaemon = true } } },
) {
    private val states = game.readouts.mapNotNull { readout -> readout.feature.state?.let { readout.feature.id to it } } +
        ("places" to game.places) + ("hidden" to game.hidden)
    private val writer = lazy(startWriter)
    private val failures = ConcurrentLinkedQueue<Pair<String, Exception>>()

    fun load() {
        for ((id, state) in states) {
            val file = dir.resolve("$id.json")
            if (!file.exists()) continue
            game.metrics.time("$id.load") {
                try {
                    state.load(file.readText())
                } catch (e: Exception) {
                    file.moveTo(dir.resolve("$id.json.corrupt-${System.currentTimeMillis()}"))
                    throw e
                }
            }
        }
    }

    fun flush() {
        generateSequence(failures::poll).forEach { (id, error) ->
            game.metrics.fail("$id.save", error)
            states.first { it.first == id }.second.unwritten()
        }
        for ((id, state) in states) {
            val json = state.takeChanges() ?: continue
            writer.value.execute {
                try {
                    Files.createDirectories(dir)
                    val temporary = dir.resolve("$id.json.tmp")
                    temporary.writeText(json)
                    temporary.moveTo(dir.resolve("$id.json"), ATOMIC_MOVE)
                } catch (e: Exception) {
                    failures += id to e
                }
            }
        }
    }

    fun close() {
        flush()
        if (!writer.isInitialized()) return
        writer.value.shutdown()
        writer.value.awaitTermination(5, TimeUnit.SECONDS)
    }
}
