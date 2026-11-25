package io.github.noxaur.dragen.rules

import io.github.noxaur.dragen.core.capture.PlayerNames
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

/** @keep The repository is public and a fixture is cut from a capture of real players, so wherever [PlayerNames] knows a name stands, a fixture holds an alias, even for a name the server also writes as its own. */
class FixtureLeakTest {
    @Test
    fun `no fixture holds a player name from a capture on this machine`() {
        val captures = Path.of("run/dragen/captures").takeIf { it.exists() }?.listDirectoryEntries("*.jsonl").orEmpty()
        val names = PlayerNames.of(*captures.toTypedArray())
        val leaks = Files.walk(Path.of("src/test/resources/features")).asSequence().filter { it.isRegularFile() }.flatMap { file ->
            val found = PlayerNames.leaked(names, file.readText())
            found.map { "${file.invariantSeparatorsPathString}: holds the player name $it. Replace it with Player1, Player2 and so on." }
        }.toList()
        assertEquals(emptyList(), leaks, leaks.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun `every name a fixture holds where a player's name stands is an alias`() {
        val fixtures = Files.walk(Path.of("src/test/resources/features")).asSequence().filter { it.toString().endsWith(".jsonl") }.toList()
        val reformatted = fixtures.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.substringAfter("\"type\":\"", "").substringBefore('"') != Json.parseToJsonElement(line).jsonObject["type"]?.jsonPrimitive?.content
            }.map { (i, _) -> "${file.invariantSeparatorsPathString}:${i + 1}: is not in the form cutFixture writes. Cut the fixture again." }
        }
        assertEquals(emptyList(), reformatted, reformatted.joinToString("\n", postfix = "\n"))
        val leaks = fixtures.flatMap { file ->
            PlayerNames.learn(file).first.filterNot { it.matches(PlayerNames.alias) }.sorted().map {
                "${file.invariantSeparatorsPathString}: holds $it where a player's name stands. Cut the fixture again with " +
                    "cutFixture, which replaces it with Player1, Player2 and so on."
            }
        }
        assertEquals(emptyList(), leaks, leaks.joinToString("\n", postfix = "\n"))
    }
}
