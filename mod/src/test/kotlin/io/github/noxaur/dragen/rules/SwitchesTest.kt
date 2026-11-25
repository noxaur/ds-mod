package io.github.noxaur.dragen.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.noxaur.dragen.core.feature.Feature
import kotlin.test.Test
import kotlin.test.assertEquals

private val readsSwitch = describe<JavaAccess<*>>("reads a feature's switch") { access ->
    val target = access.target
    target.owner.isEquivalentTo(Feature::class.java) && target.name in setOf("getEnabled", "getSwitch")
}

private fun switches(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().accessTargetWhere(readsSwitch).because(
    "a feature's switch stays on off dragsim.co. Ask the feature instead: `copyChat.feature.runs`, or " +
        "`copyChat.getFeature().getRuns()` from Java",
)

private class Enabled { fun run(feature: Feature) = feature.enabled }
private class SwitchRow { fun run(feature: Feature) = feature.switch }
private class EnabledReference { fun run(feature: Feature): () -> Boolean = feature::enabled }
private class Runs { fun run(feature: Feature) = feature.runs }

class SwitchesTest {
    @Test
    fun `outside core a feature's switch is read only through the feature`() {
        switches(resideOutsideOfPackage("io.github.noxaur.dragen.core..")).check(releaseClasses)
    }

    @Test
    fun `the rule catches a read of enabled, of the switch row and a reference to either`() {
        val reads = flags(::switches, Enabled::class.java, SwitchRow::class.java, EnabledReference::class.java, JavaEnabledReference::class.java, Runs::class.java)
        assertEquals(listOf(Enabled::class.java, SwitchRow::class.java, EnabledReference::class.java, JavaEnabledReference::class.java), reads)
    }
}
