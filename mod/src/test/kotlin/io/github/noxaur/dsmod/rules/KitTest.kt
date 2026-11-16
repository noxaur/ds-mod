package io.github.noxaur.dsmod.rules

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor"
private const val UI = "io/github/noxaur/dsmod/edge/ui/"

/** The calls that draw a shape. Text and items are drawn with `Text` and the item layers. */
private val shapes = setOf("fill", "fillGradient", "outline", "blit", "blitSprite", "horizontalLine", "verticalLine", "textHighlight", "textWithBackdrop")

/** Every place in [classes] outside `Kit` that draws a shape on the graphics itself, as `file:line`. */
fun shapesOutsideKit(classes: List<ClassNode>): List<String> = classes.filter { !(it.name.startsWith(UI) && it.sourceFile == "Kit.kt") }.flatMap { node ->
    node.methods.flatMap { method ->
        var line = 0
        method.instructions.mapNotNull { insn ->
            if (insn is LineNumberNode) line = insn.line
            // A method reference such as `graphics::fill` is a handle among the bootstrap arguments.
            val draws = insn is MethodInsnNode && insn.owner == GRAPHICS && insn.name in shapes ||
                insn is InvokeDynamicInsnNode && insn.bsmArgs.filterIsInstance<Handle>().any { it.owner == GRAPHICS && it.name in shapes }
            if (draws) "${node.name.substringBeforeLast('/')}/${node.sourceFile}:$line" else null
        }
    }
}.distinct()

class KitTest {
    @Test
    fun `only the kit draws shapes`() {
        val found = shapesOutsideKit(releaseClasses())
        assertEquals(
            emptyList(),
            found,
            "Draw chrome with `Kit` in edge/ui, so it follows the grid and the player's theme. If the kit lacks a shape, " +
                "add it there, to kit.md and to the gallery.\n" + found.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a call split over several lines`() {
        fun calling(file: String, name: String, owner: String = GRAPHICS, insn: AbstractInsnNode = MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, "(IIIII)V")) = ClassNode().apply {
            this.name = "io/github/noxaur/dsmod/edge/${file.removeSuffix(".kt")}"
            sourceFile = file
            methods.add(
                MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                    instructions.add(LineNumberNode(9, LabelNode()))
                    instructions.add(insn)
                    instructions.add(InsnNode(Opcodes.RETURN))
                },
            )
        }
        val reference = InvokeDynamicInsnNode(
            "draw",
            "(L$GRAPHICS;)Ljava/lang/Runnable;",
            Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Handle(Opcodes.H_INVOKEVIRTUAL, GRAPHICS, "fill", "(IIIII)V", false),
        )
        assertEquals(
            listOf("io/github/noxaur/dsmod/edge/Split.kt:9", "io/github/noxaur/dsmod/edge/Reference.kt:9"),
            shapesOutsideKit(
                listOf(
                    calling("Split.kt", "fill"),
                    calling("Other.kt", "text"),
                    calling("Cells.kt", "fill", "java/util/Arrays"),
                    calling("Reference.kt", "fill", insn = reference),
                ),
            ),
        )
    }
}
