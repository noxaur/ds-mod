package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.feature.Feature
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals

private val FEATURE = Type.getInternalName(Feature::class.java)

private val SWITCH_GETTERS = setOf("getEnabled", "getSwitch")

fun switchReads(classes: List<ClassNode>): List<String> = placesWhere(classes.filter { "/core/" !in it.name }) { insn ->
    fun getter(owner: String, name: String) = name in SWITCH_GETTERS && owner == FEATURE
    val handles = handlesOf(insn)
    insn is MethodInsnNode && getter(insn.owner, insn.name) || handles.any { getter(it.owner, it.name) }
}

class SwitchesTest {
    @Test
    fun `outside core a feature's switch is read only through the feature`() {
        assertNoPlaces(
            switchReads(releaseClasses()),
            "Each line reads a feature's switch, which stays on off dragsim.co. Ask the feature instead: " +
                "`DsModClient.runs<CopyChat>()`, or `DsModClient.INSTANCE.runs(CopyChat.class)` from a mixin.",
        )
    }

    @Test
    fun `the scan catches a read of enabled, of the switch row and a reference to either`() {
        fun reading(name: String, insn: AbstractInsnNode) = edgeClass(name, 7, InsnNode(Opcodes.ACONST_NULL), insn)
        fun reference(target: Handle) =
            lambdaReference("getAsBoolean", "(L$FEATURE;)Ljava/util/function/BooleanSupplier;", "()Z", target, "()Z")
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
