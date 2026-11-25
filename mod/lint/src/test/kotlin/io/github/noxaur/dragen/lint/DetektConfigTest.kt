package io.github.noxaur.dragen.lint

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetProvider
import dev.detekt.core.config.YamlConfig
import dev.detekt.test.lint
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import java.io.File
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.readText
import kotlin.io.path.reader
import kotlin.test.Test
import kotlin.test.assertEquals

class DetektConfigTest {
    private class Instance(val id: String, val rule: Rule, val config: Config)

    private val instances: List<Instance> by lazy {
        val config = YamlConfig.load(Path.of(System.getProperty("dragen.detekt")).reader())
        val rules = ServiceLoader.load(RuleSetProvider::class.java).associate { it.ruleSetId.value to it.instance().rules }
        config.subConfigKeys().filter { it != "config" }.flatMap { set ->
            val sub = config.subConfig(set)
            sub.subConfigKeys().map { id ->
                val create = rules[set]?.get(RuleName(id.substringBefore('/'))) ?: error("detekt.yml names $set > $id, which no rule set holds")
                Instance("$set > $id", create(sub.subConfig(id)), sub.subConfig(id))
            }
        }
    }

    private val environment by lazy {
        KotlinEnvironmentContainer(emptyList(), Path.of(System.getProperty("dragen.classpath")).readText().split(File.pathSeparator).map(Path::of))
    }

    private fun Instance.flags(sample: String): Boolean {
        val code = if (Regex("""(?m)^(class|fun|val|import) """).containsMatchIn(sample)) sample else "fun example() {\n$sample\n}"
        val findings = if (rule is RequiresAnalysisApi) {
            lintTyped(rule, code)
        } else {
            rule.lint(code, compile = false)
        }
        return findings.isNotEmpty()
    }

    private fun <T> Instance.lintTyped(rule: T, code: String) where T : Rule, T : RequiresAnalysisApi =
        rule.lintWithContext(environment, code, allowCompilationErrors = false)

    private fun Instance.samples(key: String): List<String> = config.valueOrDefault(key, emptyList())

    @Test
    fun `every rule in detekt yml flags each of its examples and none of its counter-examples`() {
        val wrong = instances.flatMap { instance ->
            val examples = instance.samples("examples")
            listOfNotNull("${instance.id} needs an example of the code it flags".takeIf { examples.isEmpty() }) +
                examples.filterNot { instance.flags(it) }.map { "${instance.id} does not flag:\n$it" } +
                instance.samples("counterExamples").filter { instance.flags(it) }.map { "${instance.id} flags what it must pass:\n$it" }
        }
        assertEquals(emptyList(), wrong, wrong.joinToString("\n\n"))
    }
}
