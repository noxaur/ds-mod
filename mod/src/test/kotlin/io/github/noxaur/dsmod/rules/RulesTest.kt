package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.game.Area
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun exception(rule: Rule) = Regex("""rule:${Regex.escape(rule.id)} \S""")

/** [text] breaks [rule] unless an exception comment covers the match. A [Span.FILE] rule may match across lines. */
fun violations(rule: Rule, text: String): List<Int> {
    val lines = text.lines()
    if (rule.span == Span.LINE) {
        return lines.withIndex().filter { (_, line) ->
            rule.forbid.containsMatchIn(line) && !exception(rule).containsMatchIn(line)
        }.map { it.index + 1 }
    }
    return rule.forbid.findAll(text).mapNotNull { match ->
        val start = text.substring(0, match.range.first).count { it == '\n' }
        val end = start + match.value.count { it == '\n' }
        if (lines.subList(start, end + 1).any { exception(rule).containsMatchIn(it) }) null else start + 1
    }.distinct().toList()
}

/** A line breaks [rule] unless it carries `rule:<id> <reason>`. */
fun breaks(rule: Rule, line: String) = violations(rule, line).isNotEmpty()

class RulesTest {
    @Test
    fun `the codebase follows every rule`() {
        val root = Path.of("").toAbsolutePath()
        val violations = Files.walk(root.resolve("src")).asSequence().filter { it.isRegularFile() }.flatMap { file ->
            val path = file.relativeTo(root)
            fun matches(glob: String) = FileSystems.getDefault().getPathMatcher("glob:$glob").matches(path)
            rules.filter { matches(it.files) && it.except?.let(::matches) != true }.flatMap { rule ->
                violations(rule, file.readText()).map {
                    "${path.invariantSeparatorsPathString}:$it: [${rule.id}] ${rule.fix}"
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
        rules.forEach { rule ->
            (listOf(rule.example) + rule.examples).forEach { sample ->
                assertTrue(breaks(rule, sample), "${rule.id} does not catch: $sample")
            }
        }
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
        val missing = SettingsTree().features.map { it.title }.filter { "### $it\n" !in docs }
        assertEquals(emptyList(), missing, "docs/user/features.md needs a `### <Name>` section that tells the player what each of these shows: $missing\n")
    }

    @Test
    fun `every feature has a line in the README`() {
        val readme = Path.of("../README.md").readText()
        val missing = SettingsTree().features.map { it.title }.filter { "- **$it** – " !in readme }
        assertEquals(emptyList(), missing, "README.md needs a line `- **<Name>** – <one short sentence>` under a group in Features for each of these. Screenshots go in docs/user/features.md: $missing\n")
    }

    @Test
    fun `every setting's description fits the settings screen at full size`() {
        val long = SettingsTree().sections.flatMap { it.walk() }.filter { it.description.length > Setting.DESCRIPTION_MAX }.map { "${it.name}: ${it.description}" }
        assertEquals(emptyList(), long, "Cut each description in SettingsTree to one short sentence of at most ${Setting.DESCRIPTION_MAX} characters. The player docs hold the rest: $long\n")
    }

    @Test
    fun `a feature in an area's folder is locked to that area`() {
        val wrong = SettingsTree().owners.filter { owner ->
            val folder = owner.javaClass.packageName.substringAfterLast('.')
            owner.feature.area != (Area.entries.firstOrNull { it.folder == folder } ?: Area.ANYWHERE)
        }.map { "${it.javaClass.simpleName} is ${it.feature.area}" }
        assertEquals(
            emptyList(),
            wrong,
            "A feature in core/feature/<folder>/ passes the `Area` with that folder to `Feature(..)`, and a feature locked to an " +
                "area lives in its folder. One that runs everywhere lives in another folder and passes `Area.ANYWHERE`: $wrong\n",
        )
    }

    /** Names each id that appears twice, and says to give the new rule its own id. Null when every id is unique. */
    fun ruleIdFailure(ids: List<String>): String? {
        val repeated = ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (repeated.isEmpty()) return null
        return "Rule id ${repeated.joinToString()} is already used. Give the new rule its own id."
    }

    @Test
    fun `rule ids are unique`() {
        val failure = ruleIdFailure(rules.map { it.id })
        assertEquals(null, failure, failure)
    }

    @Test
    fun `a repeated rule id names itself and says to give the new rule its own id`() {
        assertEquals(
            "Rule id chat-by-line is already used. Give the new rule its own id.",
            ruleIdFailure(listOf("chat-by-line", "text-by-role", "chat-by-line")),
        )
    }
}
