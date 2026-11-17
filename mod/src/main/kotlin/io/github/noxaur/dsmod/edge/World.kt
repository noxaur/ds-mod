package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.BlockBox
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Path
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Ring
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.core.world.Waypoint
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
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

/** How far in front of the camera a tracer starts, in blocks, so it starts at the crosshair and not behind the view. */
private const val TRACER_AHEAD = 1.0

/**
 * Marks drawn in the world. Positions are converted once when shown. They are handed to Minecraft once
 * per tick, and it replays them every frame, so a mark costs nothing in the frame loop. A mark that follows an
 * entity, and a [FrameBox], which follows the view, are drawn on each frame instead, because one handed over
 * once a tick trails what it follows by up to 50 ms. A [FrameBox]'s owner hands it over with [draw].
 */
object World {
    private sealed interface Shown
    private class ShownBox(val box: AABB, val style: GizmoStyle) : Shown
    private class ShownPath(val points: List<Vec3>, val color: Int) : Shown
    private class ShownRing(val center: Vec3, val radius: Float, val style: GizmoStyle) : Shown
    /**
     * A mark on the entity with id [entity], which [find] looks up once a tick so a frame only places it. What [follow]
     * reads is a `@JvmField`: it runs too seldom on a slow machine to be compiled, and there each getter is a call.
     */
    private abstract class Follow(val entity: Int) {
        @JvmField var found: Entity? = null
    }
    private class ShownBeam(entity: Int, @JvmField val label: BeamLabel) : Follow(entity)
    private class ShownTracer(entity: Int, @JvmField val color: Int) : Follow(entity)
    private class ShownWaypoint(val at: Vec3, val top: Vec3, val label: BeamLabel, val box: ShownBox) : Shown {
        var labelAt = at
    }

    /**
     * A beam's [name] and the distance to it, built again only when the whole meters change, and how far over the beam's
     * base it floats: higher the farther it is, so the growing text clears what it names.
     */
    private class BeamLabel(val name: String, @JvmField val color: Int) {
        @JvmField var meters = -1
        @JvmField var text = name
        @JvmField var lift = 1.5
        @JvmField var style: TextGizmo.Style = TextGizmo.Style.forColorAndCentered(color)

        /** Builds the label for [meters] away, and returns whether it changed. */
        fun at(meters: Int): Boolean {
            if (meters == this.meters) return false
            this.meters = meters
            text = "$name ${meters}m"
            lift = 1.5 + meters / 12.0
            style = labelStyle(color, meters * 0.5f, 1.5f)
            return true
        }
    }

    /**
     * A box drawn for one frame at a time, built once while it stays the same: in [style], with edges [width] wide,
     * in front of what hides it when [onTop], and with a [label] above it.
     */
    class FrameBox(box: AABB, role: Role, style: BoxStyle, width: Float, internal val onTop: Boolean, internal val label: String?) {
        // A box drawn behind the world lies on the block's faces, and would flicker against them.
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
    private var shown = emptyList<Shown>()
    private val beams = LinkedHashMap<String, List<ShownBeam>>()
    private var beamsShown = emptyList<ShownBeam>()
    private val tracers = LinkedHashMap<String, List<ShownTracer>>()
    private var tracersShown = emptyList<ShownTracer>()

    /** Draws [marks] until [hide], replacing what [id] showed before. */
    fun show(id: String, marks: List<Mark>) {
        beams[id] = marks.filterIsInstance<EntityBeam>().map { ShownBeam(it.entity, BeamLabel(it.label, it.role.argb())) }
        tracers[id] = marks.filterIsInstance<Tracer>().map { ShownTracer(it.entity, it.role.argb()) }
        this.marks[id] = marks.mapNotNull { mark ->
            val color = mark.role.argb()
            when (mark) {
                is BlockBox -> box(mark.x.toDouble(), mark.y.toDouble(), mark.z.toDouble(), mark)
                is Path -> ShownPath(mark.points.map { it.vec() }, color)
                is Ring -> ShownRing(mark.center.vec(), mark.radius, GizmoStyle.stroke(color, LINE_WIDTH))
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

    /** Marks the next end-of-tick draw will submit. A world change has to drop these before that draw. */
    fun marksQueued() = shown.size + beamsShown.size + tracersShown.size

    /** Drops every mark. The level-change path calls this, because the next draw runs before the new world's readouts. */
    fun worldChanged() {
        marks.clear()
        beams.clear()
        tracers.clear()
        flatten()
    }

    fun register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { context, _ ->
            if (beamsShown.isNotEmpty() || tracersShown.isNotEmpty()) DsModClient.game.metrics.time("world.follow") { follow(context) }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val level = client.level ?: return@register
            val camera = client.gameRenderer.mainCamera().position()
            find(level)
            for (i in shown.indices) {
                when (val mark = shown[i]) {
                    is ShownBox -> Gizmos.cuboid(mark.box, mark.style)
                    is ShownRing -> Gizmos.circle(mark.center, mark.radius, mark.style)
                    is ShownPath -> for (j in 1 until mark.points.size) {
                        Gizmos.line(mark.points[j - 1], mark.points[j], mark.color, LINE_WIDTH)
                    }
                    is ShownWaypoint -> {
                        val label = mark.label
                        if (label.at(camera.distanceTo(mark.at).toInt())) mark.labelAt = mark.at.add(0.0, label.lift, 0.0)
                        Gizmos.cuboid(mark.box.box, mark.box.style).setAlwaysOnTop()
                        Gizmos.line(mark.at, mark.top, label.color, LINE_WIDTH * 2).setAlwaysOnTop()
                        Gizmos.billboardText(label.text, mark.labelAt, label.style).setAlwaysOnTop()
                    }
                }
            }
        }
    }

    /** Finds the entity each followed mark is on. It runs once a tick, and when the marks change, so a frame need not. */
    private fun find(level: ClientLevel) {
        for (i in beamsShown.indices) beamsShown[i].let { it.found = level.getEntity(it.entity) }
        for (i in tracersShown.indices) tracersShown[i].let { it.found = level.getEntity(it.entity) }
    }

    /**
     * Draws the marks that follow an entity where this frame shows it, between the last tick's place and the next. It
     * is one method: a slow machine draws a frame or two a tick, so this runs interpreted, on caches the frame before
     * emptied, and each method it enters costs a few misses.
     */
    private fun follow(context: LevelExtractionContext) {
        val camera = context.camera()
        val eye = camera.position()
        val partial = context.deltaTracker().getGameTimeDeltaPartialTick(false)
        val beams = beamsShown
        for (i in beams.indices) {
            val mark = beams[i]
            val entity = mark.found ?: continue
            if (entity.isRemoved) continue
            val at = entity.getPosition(partial)
            val label = mark.label
            val meters = eye.distanceTo(at).toInt()
            if (meters != label.meters) label.at(meters)
            val x = at.x
            val y = at.y + entity.bbHeight
            val z = at.z
            Gizmos.line(Vec3(x, y, z), Vec3(x, y + BEAM_HEIGHT, z), label.color, LINE_WIDTH * 2).setAlwaysOnTop()
            Gizmos.billboardText(label.text, Vec3(x, y + label.lift, z), label.style).setAlwaysOnTop()
        }
        val tracers = tracersShown
        if (tracers.isEmpty()) return
        val forward = camera.forwardVector()
        val start = Vec3(eye.x + forward.x() * TRACER_AHEAD, eye.y + forward.y() * TRACER_AHEAD, eye.z + forward.z() * TRACER_AHEAD)
        for (i in tracers.indices) {
            val mark = tracers[i]
            val entity = mark.found ?: continue
            if (entity.isRemoved) continue
            val at = entity.getPosition(partial)
            Gizmos.line(start, Vec3(at.x, at.y + entity.bbHeight / 2.0, at.z), mark.color, LINE_WIDTH).setAlwaysOnTop()
        }
    }

    /**
     * Draws [mark] on this frame only. Call it on each frame while the game gathers the frame's shapes, from Fabric's
     * `AFTER_BLOCK_OUTLINE_EXTRACTION`, which runs on the client thread before they are handed to the renderer.
     */
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

    /** Text is sized in blocks, so it grows with distance, [grown] at least [least], to stay the same size on screen. */
    private fun labelStyle(color: Int, grown: Float, least: Float) =
        TextGizmo.Style.forColorAndCentered(color).withScale(TextGizmo.Style.DEFAULT_SCALE * grown.coerceAtLeast(least))

    private fun box(x: Double, y: Double, z: Double, mark: Mark) =
        ShownBox(AABB(x, y, z, x + 1, y + 1, z + 1), GizmoStyle.strokeAndFill(mark.role.argb(), LINE_WIDTH, mark.role.argb(FILL_ALPHA)))

    private fun Point.vec() = Vec3(x, y, z)
}
