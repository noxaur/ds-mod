package io.github.noxaur.dsmod.core.contribute

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * The redacted recordings a player's machine keeps after a failed upload, in one folder. A name carries the request
 * it answers, so `/dsmod submit` can send it later. Nothing else belongs in the folder: a raw capture holds names
 * and chat, and a `.part` is a redaction that was cut short.
 */
object Kept {
    private const val SEPARATOR = '~'
    private const val SUFFIX = ".gz"

    /** The name of the kept file for [rawName], a capture of [requestId]. */
    fun name(requestId: String, rawName: String) = "$requestId$SEPARATOR$rawName$SUFFIX"

    /** The request a kept [file] answers, or null when it is not one. */
    fun requestId(file: Path): String? =
        file.name.takeIf { it.endsWith(SUFFIX) && SEPARATOR in it }?.substringBefore(SEPARATOR)?.takeIf { it.isNotEmpty() }

    /** The kept recordings in [dir], oldest first. Call it off the client thread. */
    fun waiting(dir: Path) = files(dir).filter { requestId(it) != null }.sorted()

    /**
     * Deletes everything in [dir] that is not a kept recording, which only a start of the game may do: a run may be
     * writing one. It tries every file, then throws when one would not go.
     */
    fun sweep(dir: Path) {
        val failed = files(dir).filter { requestId(it) == null }.filter { runCatching { Files.deleteIfExists(it) }.isFailure }
        if (failed.isNotEmpty()) throw java.io.IOException("Could not delete $failed")
    }

    private fun files(dir: Path): List<Path> = if (Files.isDirectory(dir)) Files.list(dir).use { it.toList() } else emptyList()
}
