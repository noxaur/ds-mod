package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Feature
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each concrete class in [classes] that extends [Feature], directly or not, and is not in [listed]. */
fun unlisted(classes: List<ClassNode>, listed: Set<String>): List<String> {
    val supers = classes.associate { it.name to it.superName }
    fun isFeature(name: String?): Boolean = name != null && (name == Type.getInternalName(Feature::class.java) || isFeature(supers[name]))
    return classes.filter { it.access and Opcodes.ACC_ABSTRACT == 0 && isFeature(it.superName) && it.name !in listed }
        .map { it.name.replace('/', '.') }
}

class FeaturesListedTest {
    @Test
    fun `every feature class is placed in SettingsTree`() {
        val listed = SettingsTree().features.map { Type.getInternalName(it.javaClass) }.toSet()
        val missing = unlisted(releaseClasses(), listed)
        assertEquals(
            emptyList(),
            missing,
            "A feature that SettingsTree places nowhere never runs. In core/config/SettingsTree.kt, give each one a group of its " +
                "own with `group(Feature(c))`, or a row among others with `feature(Feature(c))`:\n" + missing.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a feature through an abstract parent and skips the parent`() {
        fun node(name: String, superName: String, access: Int = Opcodes.ACC_PUBLIC) = ClassNode().apply {
            this.name = "io/github/noxaur/dsmod/core/feature/$name"
            this.superName = superName
            this.access = access
        }
        val feature = Type.getInternalName(Feature::class.java)
        val classes = listOf(
            node("Listed", feature),
            node("Direct", feature),
            node("Base", feature, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT),
            node("Indirect", "io/github/noxaur/dsmod/core/feature/Base"),
            node("Other", "java/lang/Object"),
        )
        assertEquals(
            listOf("Direct", "Indirect").map { "io.github.noxaur.dsmod.core.feature.$it" },
            unlisted(classes, setOf("io/github/noxaur/dsmod/core/feature/Listed")),
        )
    }
}
