package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.features
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KMutableProperty0
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val FEATURE = Type.getInternalName(Feature::class.java)

// The rarity editor shows the player how their switch is set, which is the same on any server.
private val except = setOf("io/github/noxaur/dsmod/edge/RarityStyleScreen")

/** Each feature's switch, as the config class that holds it and the field's name. */
private fun switches() = features(DsModConfig()).map { feature ->
    val toggle = Feature::class.java.getDeclaredField("toggle").apply { isAccessible = true }.get(feature) as KMutableProperty0<*>
    Type.getInternalName((toggle as CallableReference).boundReceiver.javaClass) to toggle.name
}.toSet()

/**
 * Every place in [classes] outside `core` that reads a feature's switch itself, as `file:line`. `Game` keeps the mod to
 * dragsim.co for code in `core`, and nothing does for the edge, so the edge asks the feature whether it [Feature.runs].
 */
fun switchReads(classes: List<ClassNode>, switches: Set<Pair<String, String>>, subclasses: Set<String>): List<String> =
    classes.filter { "/core/" !in it.name && it.name.substringBefore('$') !in except }.flatMap { node ->
        node.methods.flatMap { method ->
            var line = 0
            method.instructions.mapNotNull { insn ->
                if (insn is LineNumberNode) line = insn.line
                val reads = insn is FieldInsnNode && insn.opcode == Opcodes.GETFIELD && (insn.owner to insn.name) in switches ||
                    insn is MethodInsnNode && insn.name == "getEnabled" && (insn.owner == FEATURE || insn.owner in subclasses)
                if (reads) "${node.name.substringBeforeLast('/')}/${node.sourceFile}:$line" else null
            }
        }
    }.distinct()

class SwitchesTest {
    @Test
    fun `outside core a feature's switch is read only through the feature`() {
        val classes = listOf("build/classes/kotlin/main", "build/classes/java/main").map(Path::of).filter { it.exists() }.flatMap { dir ->
            Files.walk(dir).asSequence().filter { it.extension == "class" }
                .map { ClassNode().also { node -> ClassReader(it.readBytes()).accept(node, ClassReader.SKIP_FRAMES) } }.toList()
        }
        assertTrue(classes.size > 100, "SwitchesTest found ${classes.size} release classes. Build the main source set first.")
        val subclasses = features(DsModConfig()).map { Type.getInternalName(it.javaClass) }.toSet()
        val reads = switchReads(classes, switches(), subclasses)
        assertEquals(
            emptyList(),
            reads,
            "Each line reads a feature's switch, which stays on off dragsim.co. Ask the feature instead: " +
                "`DsModClient.runs<CopyChat>()`, or `DsModClient.INSTANCE.runs(CopyChat.class)` from a mixin.\n" +
                reads.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a switch read and a read of enabled`() {
        fun reading(name: String, insn: AbstractInsnNode) = ClassNode().apply {
            this.name = "io/github/noxaur/dsmod/edge/$name"
            sourceFile = "$name.kt"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                instructions.add(LineNumberNode(7, org.objectweb.asm.tree.LabelNode()))
                instructions.add(InsnNode(Opcodes.ACONST_NULL))
                instructions.add(insn)
                instructions.add(InsnNode(Opcodes.RETURN))
            })
        }
        val reads = switchReads(
            listOf(
                reading("Switch", FieldInsnNode(Opcodes.GETFIELD, "io/github/noxaur/dsmod/core/config/DsModConfig\$Chat", "copyChat", "Z")),
                reading("Enabled", MethodInsnNode(Opcodes.INVOKEVIRTUAL, FEATURE, "getEnabled", "()Z")),
                reading("Runs", MethodInsnNode(Opcodes.INVOKEVIRTUAL, FEATURE, "getRuns", "()Z")),
            ),
            switches(),
            emptySet(),
        )
        assertEquals(listOf("io/github/noxaur/dsmod/edge/Switch.kt:7", "io/github/noxaur/dsmod/edge/Enabled.kt:7"), reads)
    }
}
