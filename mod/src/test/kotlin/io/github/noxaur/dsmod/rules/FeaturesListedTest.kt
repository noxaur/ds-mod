package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.core.domain.JavaClass.Predicates.implement
import com.tngtech.archunit.core.domain.JavaModifier
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.HasFeature
import kotlin.test.Test
import kotlin.test.assertEquals

class FeaturesListedTest {
    @Test
    fun `every feature class is placed in SettingsTree`() {
        val listed = SettingsTree(mac = true).owners.map { it.javaClass.name }.toSet()
        val missing = releaseClasses.that(implement(HasFeature::class.java))
            .filter { !it.isInterface && JavaModifier.ABSTRACT !in it.modifiers && it.name !in listed }.map { it.name }
        assertEquals(
            emptyList(),
            missing,
            "A feature that SettingsTree places nowhere never runs. In core/config/SettingsTree.kt, give each one a group of its " +
                "own with `group(Feature(c))`, or a row among others with `feature(Feature(c))`:\n" + missing.joinToString("\n") + "\n",
        )
    }
}
