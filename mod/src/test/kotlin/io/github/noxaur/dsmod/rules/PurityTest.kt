package io.github.noxaur.dsmod.rules

import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CORE = "io/github/noxaur/dsmod/core/"

private val OUTSIDE = listOf("net/minecraft/", "com/mojang/", "net/fabricmc/", "io/github/noxaur/dsmod/edge/")

fun impurities(classes: List<ClassNode>): List<String> = classes.filter { it.name.startsWith(CORE) }.flatMap { node ->
    val named = sortedSetOf<String>()
    node.accept(
        ClassRemapper(
            ClassNode(),
            object : Remapper(Opcodes.ASM9) {
                override fun map(internalName: String): String = internalName.also { if (OUTSIDE.any(it::startsWith)) named += it }
            },
        ),
    )
    named.map { "${node.name} -> $it" }
}

class PurityTest {
    @Test
    fun `core names nothing from Minecraft, Fabric, Mojang or edge`() {
        val impure = impurities(releaseClasses())
        assertEquals(
            emptyList(),
            impure,
            "core takes plain data and returns plain models. Read Minecraft in the edge package and pass the values in, " +
                "and return what the player should see for the edge to draw.\n" + impure.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a fully qualified call that has no import`() {
        fun core(vararg members: Any) = ClassNode().apply {
            name = "${CORE}feature/Sneaky"
            superName = "java/lang/Object"
            members.forEach { if (it is MethodNode) methods.add(it) else fields.add(it as FieldNode) }
        }
        val call = MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/client/Minecraft", "getInstance", "()Lnet/minecraft/client/Minecraft;"))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val field = FieldNode(Opcodes.ACC_PUBLIC, "edge", "Lio/github/noxaur/dsmod/edge/Chat;", null, null)
        val library = MethodNode(Opcodes.ACC_PUBLIC, "run", "(Lcom/mojang/brigadier/Command;)V", null, null)
        val fine = MethodNode(Opcodes.ACC_PUBLIC, "run", "(Ljava/lang/String;Lio/github/noxaur/dsmod/core/text/Text;)V", null, null)
        assertEquals(
            listOf(
                "${CORE}feature/Sneaky -> net/minecraft/client/Minecraft",
                "${CORE}feature/Sneaky -> io/github/noxaur/dsmod/edge/Chat",
                "${CORE}feature/Sneaky -> com/mojang/brigadier/Command",
            ).sorted(),
            impurities(listOf(core(call), core(field), core(library), core(fine))).sorted().distinct(),
        )
    }
}
