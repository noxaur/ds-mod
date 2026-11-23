package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Stored
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
    account: String = "local",
    startWriter: () -> ExecutorService = { Executors.newSingleThreadExecutor { Thread(it, "dsmod-save").apply { isDaemon = true } } },
) {
    private val profiled = game.readouts.mapNotNull { readout -> readout.feature.id.takeIf { readout.feature.state?.profiled == true } }
    private val states: List<Pair<String, Stored>> =
        game.readouts.mapNotNull { readout -> readout.feature.state?.takeUnless { it.profiled }?.let { readout.feature.id to it } } +
            ("places" to game.places) + ("hidden" to game.hidden) + ("players/$account" to game.player)
    private val writer = lazy(startWriter)
    private val failures = ConcurrentLinkedQueue<Pair<String, Exception>>()

    fun load() {
        for ((id, state) in states) read(id, state::load)
        for (id in profiled) {
            val file = dir.resolve("$id.json")
            if (read(id) { game.player.takeOld(id, it) } == true) file.moveTo(dir.resolve("$id.json.$MOVED"))
        }
    }

    private fun <T> read(id: String, take: (String) -> T): T? {
        val file = dir.resolve("$id.json")
        if (!file.exists()) return null
        var taken: T? = null
        game.metrics.time("$id.load") {
            try {
                taken = take(file.readText())
            } catch (e: Exception) {
                file.moveTo(file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}"))
                throw e
            }
        }
        return taken
    }

    fun flush() {
        generateSequence(failures::poll).forEach { (id, error) ->
            game.metrics.fail("$id.save", error)
            states.first { it.first == id }.second.unwritten()
        }
        for ((id, state) in states) {
            val encode = state.takeChanges() ?: continue
            writer.value.execute {
                try {
                    val file = dir.resolve("$id.json")
                    Files.createDirectories(file.parent)
                    val temporary = file.resolveSibling("${file.fileName}.tmp")
                    temporary.writeText(encode())
                    temporary.moveTo(file, ATOMIC_MOVE)
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

    private companion object {
        const val MOVED = "moved-to-profile"
    }
}
