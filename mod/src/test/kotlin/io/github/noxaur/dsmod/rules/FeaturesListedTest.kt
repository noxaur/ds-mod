package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.core.domain.JavaClass.Predicates.implement
import com.tngtech.archunit.core.domain.JavaModifier
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.HasFeature
import kotlin.test.Test
import kotlin.test.assertEquals

class FeaturesListedTest {
    @Test
    fun `every feature class is placed by the folder above it`() {
        val listed = SettingsTree(mac = true).owners.map { it.javaClass.name }.toSet()
        val missing = releaseClasses.that(implement(HasFeature::class.java))
            .filter { !it.isInterface && JavaModifier.ABSTRACT !in it.modifiers && it.name !in listed }
            .map { "${it.simpleName}, in the file of core/feature/${it.packageName.removePrefix("io.github.noxaur.dsmod.core.feature.").substringBeforeLast('.').replace('.', '/')}/" }
        assertEquals(
            emptyList(),
            missing,
            "A feature that no folder above it places never runs. Place each one in the file named after the folder above its own: " +
                "in a section's file as a group of its own with `group(Feature(c))`, or in a group's file as a row with `feature(Feature(c))`:\n" +
                missing.joinToString("\n") + "\n",
        )
    }
}
