package io.github.noxaur.dragen.core.contribute

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** @keep Nothing else belongs in the folder: a raw capture holds names and chat, and a `.part` is a redaction that was cut
 * short. */
object Kept {
    private const val SEPARATOR = '~'
    private const val SUFFIX = ".gz"

    fun name(requestId: String, rawName: String) = "$requestId$SEPARATOR$rawName$SUFFIX"

    fun requestId(file: Path): String? =
        file.name.takeIf { it.endsWith(SUFFIX) && SEPARATOR in it }?.substringBefore(SEPARATOR)?.takeIf { it.isNotEmpty() }

    fun waiting(dir: Path) = files(dir).filter { requestId(it) != null }.sorted()

    fun sweep(dir: Path) {
        val failed = files(dir).filter { requestId(it) == null }.filter { runCatching { Files.deleteIfExists(it) }.isFailure }
        if (failed.isNotEmpty()) throw java.io.IOException("Could not delete $failed")
    }

    private fun files(dir: Path): List<Path> = if (Files.isDirectory(dir)) Files.list(dir).use { it.toList() } else emptyList()
}
