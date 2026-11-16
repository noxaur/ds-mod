package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Keeps each feature's [io.github.noxaur.dsmod.core.feature.Saved] state in `<dir>/<feature id>.json`, and the game's
 * own in `places.json`, the places the player has been, and `hidden.json`, the features that told them they hide a
 * line. [load] reads the files once at start. [flush] hands the changed ones to a writer thread, which writes a temporary file and
 * renames it over the old one, so the client thread never waits on the disk and a crash cannot leave half a file.
 */
class SavedFiles(private val dir: Path, private val game: Game) {
    private val states = game.readouts.mapNotNull { readout -> readout.feature.state?.let { readout.feature.id to it } } +
        ("places" to game.places) + ("hidden" to game.hidden)
    private val writer = lazy { Executors.newSingleThreadExecutor { Thread(it, "dsmod-save").apply { isDaemon = true } } }
    private val failures = ConcurrentLinkedQueue<Pair<String, Exception>>()

    /** A file that does not parse is renamed `<id>.json.corrupt-<time>`, and its feature starts from its default. */
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

    /** Writes every state that changed since the last call. A write that failed earlier reaches `Metrics` here and is tried again. */
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

    /** Flushes, then waits briefly for the writer, because the game is closing and nothing flushes after. */
    fun close() {
        flush()
        if (!writer.isInitialized()) return
        writer.value.shutdown()
        writer.value.awaitTermination(5, TimeUnit.SECONDS)
    }
}
