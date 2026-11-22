package io.github.noxaur.dsmod.rules

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor"
private const val UI = "io/github/noxaur/dsmod/edge/ui/"

private val shapes = setOf("fill", "fillGradient", "outline", "blit", "blitSprite", "horizontalLine", "verticalLine", "textHighlight", "textWithBackdrop")

fun shapesOutsideKit(classes: List<ClassNode>): List<String> {
    val supers = classes.associate { it.name to it.superName }
    // @keep A call through a mod class that extends the graphics names that class as its owner.
    fun graphics(owner: String): Boolean = owner == GRAPHICS || supers[owner]?.let(::graphics) == true
    return placesWhere(classes.filter { !(it.name.startsWith(UI) && it.sourceFile == "Kit.kt") }) { insn ->
        insn is MethodInsnNode && graphics(insn.owner) && insn.name in shapes ||
            handlesOf(insn).any { graphics(it.owner) && it.name in shapes }
    }
}

class KitTest {
    @Test
    fun `only the kit draws shapes`() {
        assertNoPlaces(
            shapesOutsideKit(releaseClasses()),
            "Draw chrome with `Kit` in edge/ui, so it follows the grid and the player's theme. If the kit lacks a shape, " +
                "add it there, to kit.md and to the gallery.",
        )
    }

    @Test
    fun `the scan catches a call split over several lines`() {
        fun calling(name: String, call: String, owner: String = GRAPHICS, insn: AbstractInsnNode = MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, call, "(IIIII)V")) =
            edgeClass(name, 9, insn)
        val reference = InvokeDynamicInsnNode(
            "draw",
            "(L$GRAPHICS;)Ljava/lang/Runnable;",
            Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Handle(Opcodes.H_INVOKEVIRTUAL, GRAPHICS, "fill", "(IIIII)V", false),
        )
        assertEquals(
            listOf("Split", "Reference", "Subclass").map { "io/github/noxaur/dsmod/edge/$it.kt:9" },
            shapesOutsideKit(
                listOf(
                    calling("Split", "fill"),
                    calling("Other", "text"),
                    calling("Cells", "fill", "java/util/Arrays"),
                    calling("Reference", "fill", insn = reference),
                    calling("Subclass", "fill", "io/github/noxaur/dsmod/edge/Custom"),
                    ClassNode().apply { name = "io/github/noxaur/dsmod/edge/Custom"; superName = GRAPHICS; sourceFile = "Custom.kt" },
                ),
            ),
        )
    }
}
