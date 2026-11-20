package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.feature.Feature
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals

private val FEATURE = Type.getInternalName(Feature::class.java)

/** The getters that read a feature's switch: its value, and the switch row itself, whose value the edge could then read. */
private val SWITCH_GETTERS = setOf("getEnabled", "getSwitch")

/**
 * Every place in [classes] outside `core` that reads a feature's switch itself, as `file:line`. `Game` keeps the mod to
 * dragsim.co for code in `core`, and nothing does for the edge, so the edge asks the feature whether it [Feature.runs].
 */
fun switchReads(classes: List<ClassNode>): List<String> =
    classes.filter { "/core/" !in it.name }.flatMap { node ->
        node.methods.flatMap { method ->
            var line = 0
            method.instructions.mapNotNull { insn ->
                if (insn is LineNumberNode) line = insn.line
                fun getter(owner: String, name: String) = name in SWITCH_GETTERS && owner == FEATURE
                // A method reference such as Java's `feature::getEnabled` is a handle among the bootstrap arguments.
                val handles = (insn as? InvokeDynamicInsnNode)?.bsmArgs.orEmpty().filterIsInstance<Handle>()
                val reads = insn is MethodInsnNode && getter(insn.owner, insn.name) || handles.any { getter(it.owner, it.name) }
                if (reads) "${node.name.substringBeforeLast('/')}/${node.sourceFile}:$line" else null
            }
        }
    }.distinct()

class SwitchesTest {
    @Test
    fun `outside core a feature's switch is read only through the feature`() {
        val reads = switchReads(releaseClasses())
        assertEquals(
            emptyList(),
            reads,
            "Each line reads a feature's switch, which stays on off dragsim.co. Ask the feature instead: " +
                "`DsModClient.runs<CopyChat>()`, or `DsModClient.INSTANCE.runs(CopyChat.class)` from a mixin.\n" +
                reads.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a read of enabled, of the switch row and a reference to either`() {
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
        // What javac emits for `BooleanSupplier on = feature::getEnabled`.
        fun reference(target: Handle) = InvokeDynamicInsnNode(
            "getAsBoolean",
            "(L$FEATURE;)Ljava/util/function/BooleanSupplier;",
            Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;" +
                    "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false,
            ),
            Type.getMethodType("()Z"),
            target,
            Type.getMethodType("()Z"),
        )
        val reads = switchReads(
            listOf(
                reading("Switch", MethodInsnNode(Opcodes.INVOKEVIRTUAL, FEATURE, "getSwitch", "()Lio/github/noxaur/dsmod/core/config/Switch;")),
                reading("Enabled", MethodInsnNode(Opcodes.INVOKEVIRTUAL, FEATURE, "getEnabled", "()Z")),
                reading("Runs", MethodInsnNode(Opcodes.INVOKEVIRTUAL, FEATURE, "getRuns", "()Z")),
                reading("Reference", reference(Handle(Opcodes.H_INVOKEVIRTUAL, FEATURE, "getEnabled", "()Z", false))),
                reading("RunsReference", reference(Handle(Opcodes.H_INVOKEVIRTUAL, FEATURE, "getRuns", "()Z", false))),
            ),
        )
        assertEquals(
            listOf("Switch", "Enabled", "Reference").map { "io/github/noxaur/dsmod/edge/$it.kt:7" },
            reads,
        )
    }
}
