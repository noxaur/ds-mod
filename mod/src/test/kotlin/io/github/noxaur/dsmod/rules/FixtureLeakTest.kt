package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.capture.PlayerNames
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fixture is cut from a capture of real players, and the repository is public. This reads every capture
 * on this machine for player names and fails when a fixture or a transcript still holds one. A checkout
 * without captures has nothing to compare with, so the test passes there.
 */
class FixtureLeakTest {
    @Test
    fun `no fixture holds a player name from a capture on this machine`() {
        val captures = Path.of("run/dsmod/captures").takeIf { it.exists() }?.listDirectoryEntries("*.jsonl").orEmpty()
        // A fixture holds aliases, so a player who is called one cannot be told from it here.
        val names = PlayerNames.of(*captures.toTypedArray()).filterNot { it.matches(Regex("""Player\d+""")) }
        val leaks = Files.walk(Path.of("src/test/resources/features")).asSequence().filter { it.isRegularFile() }.flatMap { file ->
            val text = file.readText()
            names.filter { Regex("""(?<!\w)${Regex.escape(it)}(?!\w)""").containsMatchIn(text) }
                .map { "${file.invariantSeparatorsPathString}: holds the player name $it. Replace it with Player1, Player2 and so on." }
        }.toList()
        assertEquals(emptyList(), leaks, leaks.joinToString("\n", postfix = "\n"))
    }
}
