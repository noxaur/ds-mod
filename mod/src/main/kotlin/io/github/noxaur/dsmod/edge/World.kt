package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.core.world.Waypoint
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val BEAM_HEIGHT = 64.0
private const val LINE_WIDTH = 2f
private const val FILL_ALPHA = 70

private const val TRACER_AHEAD = 1.0

object World {
    private lateinit var game: Game

    fun bind(game: Game) {
        this.game = game
    }

    internal sealed interface Shown
    internal class ShownBox(val box: AABB, val style: GizmoStyle) : Shown
    internal abstract class Follow(val entity: Int) {
        var found: Entity? = null
    }
    internal class ShownBeam(entity: Int, val label: BeamLabel) : Follow(entity)
    internal class ShownTracer(entity: Int, val color: Int) : Follow(entity)
    internal class ShownWaypoint(val at: Vec3, val top: Vec3, val label: BeamLabel, val box: ShownBox) : Shown {
        var labelAt = at
    }

    internal class BeamLabel(val name: String, val color: Int) {
        var meters = -1
        var text = name
        var style: TextGizmo.Style = TextGizmo.Style.forColorAndCentered(color)

        fun at(meters: Int): Boolean {
            if (meters == this.meters) return false
            this.meters = meters
            text = "$name ${meters}m"
            style = labelStyle(color, meters * 0.5f, 1.5f)
            return true
        }
    }

    class FrameBox(box: AABB, role: Role, style: BoxStyle, width: Float, internal val onTop: Boolean, internal val label: String?) {
        // @keep A box drawn behind the world lies on the block's faces, and would flicker against them.
        val box: AABB = if (onTop) box else box.inflate(0.002)
        internal val style: GizmoStyle = when (style) {
            BoxStyle.OUTLINE -> GizmoStyle.stroke(role.argb(), width)
            BoxStyle.FILLED -> GizmoStyle.fill(role.argb(FILL_ALPHA))
            BoxStyle.BOTH -> GizmoStyle.strokeAndFill(role.argb(), width, role.argb(FILL_ALPHA))
        }
        internal val color = role.argb()
        internal val top = Vec3(box.center.x, box.maxY, box.center.z)
        internal var meters = -1
        internal var labelAt = top
        internal var labelStyle: TextGizmo.Style = TextGizmo.Style.forColorAndCentered(color)
    }

    private val marks = LinkedHashMap<String, List<Shown>>()
    internal var shown = emptyList<Shown>()
    private val beams = LinkedHashMap<String, List<ShownBeam>>()
    internal var beamsShown = emptyList<ShownBeam>()
    private val tracers = LinkedHashMap<String, List<ShownTracer>>()
    internal var tracersShown = emptyList<ShownTracer>()

    fun show(id: String, marks: List<Mark>) {
        beams[id] = marks.filterIsInstance<EntityBeam>().map { ShownBeam(it.entity, BeamLabel(it.label, it.role.argb())) }
        tracers[id] = marks.filterIsInstance<Tracer>().map { ShownTracer(it.entity, it.role.argb()) }
        this.marks[id] = marks.mapNotNull { mark ->
            val color = mark.role.argb()
            when (mark) {
                is Waypoint -> ShownWaypoint(
                    mark.at.vec(),
                    mark.at.vec().add(0.0, BEAM_HEIGHT, 0.0),
                    BeamLabel(mark.label, color),
                    box(mark.at.x - 0.5, mark.at.y, mark.at.z - 0.5, mark),
                )
                is EntityBeam, is Tracer -> null
            }
        }
        flatten()
        Minecraft.getInstance().level?.let(::find)
    }

    fun hide(id: String) {
        marks.remove(id)
        beams.remove(id)
        tracers.remove(id)
        flatten()
    }

    private fun flatten() {
        shown = marks.values.flatten()
        beamsShown = beams.values.flatten()
        tracersShown = tracers.values.flatten()
    }

    fun worldChanged() {
        marks.clear()
        beams.clear()
        tracers.clear()
        flatten()
    }

    fun register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { context, _ ->
            if (beamsShown.isNotEmpty() || tracersShown.isNotEmpty()) game.metrics.time("world.follow", Metrics.FRAME) { follow(context) }
        }
        Ticks.end { client ->
            val level = client.level ?: return@end
            val camera = client.gameRenderer.mainCamera().position()
            find(level)
            for (i in shown.indices) {
                when (val mark = shown[i]) {
                    is ShownBox -> Gizmos.cuboid(mark.box, mark.style)
                    is ShownWaypoint -> {
                        if (mark.label.at(camera.distanceTo(mark.at).toInt())) mark.labelAt = labelAt(mark.at, mark.label)
                        Gizmos.cuboid(mark.box.box, mark.box.style).setAlwaysOnTop()
                        beam(mark.at, mark.top, mark.label, mark.labelAt)
                    }
                }
            }
        }
    }

    private fun find(level: ClientLevel) {
        for (i in beamsShown.indices) beamsShown[i].let { it.found = level.getEntity(it.entity) }
        for (i in tracersShown.indices) tracersShown[i].let { it.found = level.getEntity(it.entity) }
    }

    private fun follow(context: LevelExtractionContext) {
        val camera = context.camera()
        val eye = camera.position()
        val partial = context.deltaTracker().getGameTimeDeltaPartialTick(false)
        for (i in beamsShown.indices) {
            val mark = beamsShown[i]
            val entity = mark.found ?: continue
            if (entity.isRemoved) continue
            val at = entity.getPosition(partial)
            val top = at.add(0.0, entity.bbHeight.toDouble(), 0.0)
            mark.label.at(eye.distanceTo(at).toInt())
            beam(top, top.add(0.0, BEAM_HEIGHT, 0.0), mark.label, labelAt(top, mark.label))
        }
        if (tracersShown.isEmpty()) return
        val forward = camera.forwardVector()
        val start = eye.add(forward.x() * TRACER_AHEAD, forward.y() * TRACER_AHEAD, forward.z() * TRACER_AHEAD)
        for (i in tracersShown.indices) {
            val mark = tracersShown[i]
            val entity = mark.found ?: continue
            if (entity.isRemoved) continue
            Gizmos.line(start, entity.getPosition(partial).add(0.0, entity.bbHeight / 2.0, 0.0), mark.color, LINE_WIDTH).setAlwaysOnTop()
        }
    }

    /** @keep Call it on each frame while the game gathers the frame's shapes, from Fabric's `AFTER_BLOCK_OUTLINE_EXTRACTION`,
     * which runs on the client thread before they are handed to the renderer. */
    fun draw(mark: FrameBox) {
        val box = Gizmos.cuboid(mark.box, mark.style)
        if (mark.onTop) box.setAlwaysOnTop()
        val label = mark.label ?: return
        val meters = Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(mark.top).toInt()
        if (meters != mark.meters) {
            mark.meters = meters
            mark.labelStyle = labelStyle(mark.color, meters * 0.25f, 1f)
            mark.labelAt = mark.top.add(0.0, 0.5 + meters / 16.0, 0.0)
        }
        Gizmos.billboardText(label, mark.labelAt, mark.labelStyle).setAlwaysOnTop()
    }

    private fun beam(from: Vec3, to: Vec3, label: BeamLabel, labelAt: Vec3) {
        Gizmos.line(from, to, label.color, LINE_WIDTH * 2).setAlwaysOnTop()
        Gizmos.billboardText(label.text, labelAt, label.style).setAlwaysOnTop()
    }

    private fun labelAt(base: Vec3, label: BeamLabel) = base.add(0.0, 1.5 + label.meters / 12.0, 0.0)

    private fun labelStyle(color: Int, grown: Float, least: Float) =
        TextGizmo.Style.forColorAndCentered(color).withScale(TextGizmo.Style.DEFAULT_SCALE * grown.coerceAtLeast(least))

    private fun box(x: Double, y: Double, z: Double, mark: Mark) =
        ShownBox(AABB(x, y, z, x + 1, y + 1, z + 1), GizmoStyle.strokeAndFill(mark.role.argb(), LINE_WIDTH, mark.role.argb(FILL_ALPHA)))

    private fun Point.vec() = Vec3(x, y, z)
}
