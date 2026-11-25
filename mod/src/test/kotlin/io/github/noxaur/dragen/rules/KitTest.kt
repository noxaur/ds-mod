package io.github.noxaur.dragen.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

private val shapes = setOf("fill", "fillGradient", "outline", "blit", "blitSprite", "horizontalLine", "verticalLine", "textHighlight", "textWithBackdrop")

private val drawsShape = describe<JavaAccess<*>>("draws a shape on the graphics") { access ->
    val target = access.target
    target.name in shapes && target.owner.isAssignableTo(GuiGraphicsExtractor::class.java)
}

private fun kit(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().accessTargetWhere(drawsShape).because(
    "the kit draws chrome on the grid in the player's theme. Draw it with `Kit` in edge/ui, and if the kit lacks a shape, " +
        "add it there and to the gallery",
)

private class Fills { fun run(g: GuiGraphicsExtractor) = g.fill(0, 0, 1, 1, 0) }
private class Refers { fun run(g: GuiGraphicsExtractor): (Int, Int, Int, Int, Int) -> Unit = g::fill }
private class Measures { fun run(g: GuiGraphicsExtractor) = g.guiWidth() }

class KitTest {
    @Test
    fun `only the kit draws shapes`() {
        kit(outside("edge/ui/Kit.kt")).check(releaseClasses)
    }

    @Test
    fun `the rule catches a call and a reference, and passes the rest of the graphics`() {
        val drawing = flags(::kit, Fills::class.java, Refers::class.java, JavaFillReference::class.java, Measures::class.java)
        assertEquals(listOf(Fills::class.java, Refers::class.java, JavaFillReference::class.java), drawing)
    }
}
