package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

private val worldText = setOf(
    "net.minecraft.gizmos.Gizmos" to "billboardText",
    "net.minecraft.gizmos.Gizmos" to "billboardTextOverBlock",
    "net.minecraft.gizmos.Gizmos" to "billboardTextOverMob",
    "net.minecraft.client.renderer.SubmitNodeCollector" to "submitText",
    "net.minecraft.client.renderer.SubmitNodeCollector" to "submitNameTag",
    "net.minecraft.client.renderer.OrderedSubmitNodeCollector" to "submitText",
    "net.minecraft.client.renderer.OrderedSubmitNodeCollector" to "submitNameTag",
)

private val drawsWorldText = describe<JavaAccess<*>>("draws text in the world") { access ->
    access.target.owner.name to access.target.name in worldText
}

private fun worldText(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().accessTargetWhere(drawsWorldText).because(
    "world text has one look. Hand the text to `World` as a `Waypoint`, an `EntityBeam` or a `FrameBox` label, " +
        "and `World.WorldLabel` draws it",
)

private class Gizmo { fun run() = Gizmos.billboardText("Nest", Vec3.ZERO, TextGizmo.Style.whiteAndCentered()) }
private class NoGizmoText { fun run() = Gizmos.line(Vec3.ZERO, Vec3.ZERO, 0) }

class WorldTextTest {
    @Test
    fun `only World draws text in the world`() {
        worldText(outside("edge/World.kt")).check(releaseClasses)
    }

    @Test
    fun `the rule catches world text drawn outside World`() {
        assertEquals(listOf(Gizmo::class.java), flags(::worldText, Gizmo::class.java, NoGizmoText::class.java))
    }
}
