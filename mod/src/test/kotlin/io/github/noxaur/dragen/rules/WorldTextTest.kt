package io.github.noxaur.dragen.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals

private val worldText = setOf(
    "net.minecraft.gizmos.Gizmos" to "billboardText",
    "net.minecraft.gizmos.Gizmos" to "billboardTextOverBlock",
    "net.minecraft.gizmos.Gizmos" to "billboardTextOverMob",
    "net.minecraft.gizmos.TextGizmo" to "<init>",
    "net.minecraft.client.renderer.feature.TextFeatureRenderer\$Submit" to "<init>",
    "net.minecraft.client.renderer.feature.NameTagFeatureRenderer\$Submit" to "<init>",
)

private val drawsWorldText = describe<JavaAccess<*>>("draws text in the world") { access ->
    val target = access.target
    target.owner.name to target.name in worldText ||
        target.name in setOf("submitText", "submitNameTag") && collector(target.owner.name)
}

private fun collector(name: String) =
    runCatching { OrderedSubmitNodeCollector::class.java.isAssignableFrom(Class.forName(name, false, OrderedSubmitNodeCollector::class.java.classLoader)) }.getOrDefault(false)

private fun worldText(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().accessTargetWhere(drawsWorldText).because(
    "world text has one look. Hand the text to `World` as a `Waypoint`, an `EntityBeam` or a `FrameBox` label, " +
        "and `World.WorldLabel` draws it",
)

private class Gizmo { fun run() = Gizmos.billboardText("Nest", Vec3.ZERO, TextGizmo.Style.whiteAndCentered()) }
private class DirectGizmo { fun run() = Gizmos.addGizmo(TextGizmo(Vec3.ZERO, "Nest", TextGizmo.Style.whiteAndCentered())) }
private class DirectSubmit {
    fun run() = TextFeatureRenderer.Submit(Matrix4f(), 0f, 0f, FormattedCharSequence.EMPTY, false, Font.DisplayMode.NORMAL, 0, 0, 0, 0)
}
private class Concrete {
    fun run(c: SubmitNodeCollection) = c.submitNameTag(PoseStack(), Vec3.ZERO, 0, Component.empty(), false, 0, CameraRenderState())
}
private class NoGizmoText { fun run() = Gizmos.line(Vec3.ZERO, Vec3.ZERO, 0) }

class WorldTextTest {
    @Test
    fun `only World draws text in the world`() {
        worldText(outside("edge/World.kt")).check(releaseClasses)
    }

    @Test
    fun `the rule catches world text drawn outside World`() {
        assertEquals(
            listOf(Gizmo::class.java, DirectGizmo::class.java, DirectSubmit::class.java, Concrete::class.java),
            flags(::worldText, Gizmo::class.java, DirectGizmo::class.java, DirectSubmit::class.java, Concrete::class.java, NoGizmoText::class.java),
        )
    }
}
