package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.HasFeature
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each class in [classes] that is a feature, one that implements [HasFeature], and is not in [listed]. */
fun unlisted(classes: List<ClassNode>, listed: Set<String>): List<String> =
    classes.filter { Type.getInternalName(HasFeature::class.java) in it.interfaces && it.name !in listed }.map { it.name.replace('/', '.') }

class FeaturesListedTest {
    @Test
    fun `every feature class is placed in SettingsTree`() {
        val listed = SettingsTree(mac = true).owners.map { Type.getInternalName(it.javaClass) }.toSet()
        val missing = unlisted(releaseClasses(), listed)
        assertEquals(
            emptyList(),
            missing,
            "A feature that SettingsTree places nowhere never runs. In core/config/SettingsTree.kt, give each one a group of its " +
                "own with `group(Feature(c))`, or a row among others with `feature(Feature(c))`:\n" + missing.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches each class that holds a feature and skips the rest`() {
        fun node(name: String, vararg interfaces: String) = ClassNode().apply {
            this.name = "io/github/noxaur/dsmod/core/feature/$name"
            superName = "java/lang/Object"
            access = Opcodes.ACC_PUBLIC
            this.interfaces = interfaces.toMutableList()
        }
        val feature = Type.getInternalName(HasFeature::class.java)
        val classes = listOf(
            node("Listed", feature),
            node("Unlisted", feature),
            node("Other"),
            node("Elsewise", "java/lang/Runnable"),
        )
        assertEquals(
            listOf("io.github.noxaur.dsmod.core.feature.Unlisted"),
            unlisted(classes, setOf("io/github/noxaur/dsmod/core/feature/Listed")),
        )
    }
}
