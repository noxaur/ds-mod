package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.Area
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A line breaks [rule] unless it carries `rule:<id> <reason>`. */
fun breaks(rule: Rule, line: String) =
    rule.forbid.containsMatchIn(line) && !Regex("""rule:${Regex.escape(rule.id)} \S""").containsMatchIn(line)

class RulesTest {
    @Test
    fun `the codebase follows every rule`() {
        val root = Path.of("").toAbsolutePath()
        val violations = Files.walk(root.resolve("src")).asSequence().filter { it.isRegularFile() }.flatMap { file ->
            val path = file.relativeTo(root)
            fun matches(glob: String) = FileSystems.getDefault().getPathMatcher("glob:$glob").matches(path)
            rules.filter { matches(it.files) && it.except?.let(::matches) != true }.flatMap { rule ->
                file.readLines().withIndex().filter { breaks(rule, it.value) }.map {
                    "${path.invariantSeparatorsPathString}:${it.index + 1}: [${rule.id}] ${rule.fix}"
                }
            }
        }.toList()

        assertEquals(
            emptyList(),
            violations,
            "Fix each line, or end it with a comment `rule:<id> <reason>` if the user agreed to an exception.\n" +
                violations.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `every rule catches its own example`() {
        rules.forEach { assertTrue(breaks(it, it.example), "${it.id} does not catch: ${it.example}") }
    }

    @Test
    fun `an exception needs a reason`() {
        val rule = rules.first()
        assertTrue(!breaks(rule, "${rule.example} // rule:${rule.id} the user asked for this"))
        assertTrue(breaks(rule, "${rule.example} // rule:${rule.id}"))
    }

    @Test
    fun `every feature has a section in the player docs`() {
        val docs = Path.of("../docs/user/features.md").readText()
        val missing = features(DsModConfig()).map { it.title }.filter { "### $it\n" !in docs }
        assertEquals(emptyList(), missing, "docs/user/features.md needs a `### <Name>` section that tells the player what each of these shows: $missing\n")
    }

    @Test
    fun `a feature in an area's folder is locked to that area`() {
        val wrong = features(DsModConfig()).filter { feature ->
            val folder = feature.javaClass.packageName.substringAfterLast('.')
            feature.area != (Area.entries.firstOrNull { it.folder == folder } ?: Area.ANYWHERE)
        }.map { "${it.javaClass.simpleName} is ${it.area}" }
        assertEquals(
            emptyList(),
            wrong,
            "A feature in core/feature/<folder>/ passes the `Area` with that folder to `Feature(..)`, and a feature locked to an " +
                "area lives in its folder. One that runs everywhere lives in another folder and passes `Area.ANYWHERE`: $wrong\n",
        )
    }

    @Test
    fun `rule ids are unique`() {
        assertEquals(rules.map { it.id }.distinct(), rules.map { it.id })
    }
}
