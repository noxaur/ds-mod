package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.capture.PlayerNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fixture is cut from a capture of real players, and the repository is public. The first test reads every capture
 * on this machine for player names and fails when a fixture or a transcript still holds one, which finds a name
 * anywhere but needs the captures. The second needs none, so it holds in CI: wherever [PlayerNames] knows a player's
 * name stands, a fixture holds an alias, even for a name the server also writes as its own.
 */
class FixtureLeakTest {
    @Test
    fun `no fixture holds a player name from a capture on this machine`() {
        val captures = Path.of("run/dsmod/captures").takeIf { it.exists() }?.listDirectoryEntries("*.jsonl").orEmpty()
        // A fixture holds aliases, so a player who is called one cannot be told from it here.
        val names = PlayerNames.of(*captures.toTypedArray()).filterNot { it.matches(ALIAS) }
        // A Minecraft name is letters, digits and underscores, so it stands in a fixture as one whole word. Reading each
        // file's words once keeps the test as fast with a thousand names as with ten.
        val (plain, other) = names.partition { it.matches(WORD) }
        val wanted = plain.toSet()
        val leaks = Files.walk(Path.of("src/test/resources/features")).asSequence().filter { it.isRegularFile() }.flatMap { file ->
            val text = file.readText()
            val found = WORD.findAll(text).map { it.value }.filter { it in wanted }.toSortedSet() +
                other.filter { Regex("""(?<!\w)${Regex.escape(it)}(?!\w)""").containsMatchIn(text) }
            found.map { "${file.invariantSeparatorsPathString}: holds the player name $it. Replace it with Player1, Player2 and so on." }
        }.toList()
        assertEquals(emptyList(), leaks, leaks.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun `every name a fixture holds where a player's name stands is an alias`() {
        val fixtures = Files.walk(Path.of("src/test/resources/features")).asSequence().filter { it.toString().endsWith(".jsonl") }.toList()
        // PlayerNames reads a line's type from its text, without parsing the line, so a type written any other way is unread.
        val reformatted = fixtures.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.substringAfter("\"type\":\"", "").substringBefore('"') != Json.parseToJsonElement(line).jsonObject["type"]?.jsonPrimitive?.content
            }.map { (i, _) -> "${file.invariantSeparatorsPathString}:${i + 1}: is not in the form cutFixture writes. Cut the fixture again." }
        }
        assertEquals(emptyList(), reformatted, reformatted.joinToString("\n", postfix = "\n"))
        val leaks = fixtures.flatMap { file ->
            PlayerNames.found(file).filterNot { it.matches(ALIAS) }.sorted().map {
                "${file.invariantSeparatorsPathString}: holds $it where a player's name stands. Cut the fixture again with " +
                    "cutFixture, which replaces it with Player1, Player2 and so on."
            }
        }
        assertEquals(emptyList(), leaks, leaks.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        val ALIAS = Regex("""Player\d+""")
        val WORD = Regex("""\w+""")
    }
}
