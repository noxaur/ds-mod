package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.BlockBox
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Path
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Ring
import io.github.noxaur.dsmod.core.world.Waypoint
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val BEAM_HEIGHT = 64.0
private const val LINE_WIDTH = 2f
private const val FILL_ALPHA = 70

/**
 * Marks drawn in the world. Positions are converted once when shown. They are handed to Minecraft once
 * per tick, and it replays them every frame, so a mark costs nothing in the frame loop. A [FrameBox] is the one
 * exception, for a box that follows the view: its owner hands it over on each frame with [draw].
 */
object World {
    private sealed interface Shown
    private class ShownBox(val box: AABB, val style: GizmoStyle) : Shown
    private class ShownPath(val points: List<Vec3>, val color: Int) : Shown
    private class ShownRing(val center: Vec3, val radius: Float, val style: GizmoStyle) : Shown
    private class ShownWaypoint(val at: Vec3, val top: Vec3, val name: String, val color: Int, val box: ShownBox) : Shown {
        var meters = -1
        var label = name
        var labelAt = at
        var style: TextGizmo.Style = TextGizmo.Style.forColorAndCentered(color)
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

    /** Draws [marks] until [hide], replacing what [id] showed before. */
    fun show(id: String, marks: List<Mark>) {
        this.marks[id] = marks.map { mark ->
            val color = mark.role.argb()
            when (mark) {
                is BlockBox -> box(mark.x.toDouble(), mark.y.toDouble(), mark.z.toDouble(), mark)
                is Path -> ShownPath(mark.points.map { it.vec() }, color)
                is Ring -> ShownRing(mark.center.vec(), mark.radius, GizmoStyle.stroke(color, LINE_WIDTH))
                is Waypoint -> ShownWaypoint(
                    mark.at.vec(),
                    mark.at.vec().add(0.0, BEAM_HEIGHT, 0.0),
                    mark.label,
                    color,
                    box(mark.at.x - 0.5, mark.at.y, mark.at.z - 0.5, mark),
                )
            }
        }
        shown = this.marks.values.flatten()
    }

    fun hide(id: String) {
        marks.remove(id)
        shown = marks.values.flatten()
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (shown.isEmpty() || client.level == null) return@register
            val camera = client.gameRenderer.mainCamera().position()
            for (i in shown.indices) {
                when (val mark = shown[i]) {
                    is ShownBox -> Gizmos.cuboid(mark.box, mark.style)
                    is ShownRing -> Gizmos.circle(mark.center, mark.radius, mark.style)
                    is ShownPath -> for (j in 1 until mark.points.size) {
                        Gizmos.line(mark.points[j - 1], mark.points[j], mark.color, LINE_WIDTH)
                    }
                    is ShownWaypoint -> {
                        val meters = camera.distanceTo(mark.at).toInt()
                        if (meters != mark.meters) {
                            mark.meters = meters
                            mark.label = "${mark.name} ${meters}m"
                            // Text is sized in blocks, so it grows with distance to stay the same size on screen.
                            val scale = (meters * 0.5f).coerceAtLeast(1.5f)
                            mark.style = TextGizmo.Style.forColorAndCentered(mark.color).withScale(TextGizmo.Style.DEFAULT_SCALE * scale)
                            mark.labelAt = mark.at.add(0.0, 1.5 + meters / 12.0, 0.0)
                        }
                        Gizmos.cuboid(mark.box.box, mark.box.style).setAlwaysOnTop()
                        Gizmos.line(mark.at, mark.top, mark.color, LINE_WIDTH * 2).setAlwaysOnTop()
                        Gizmos.billboardText(mark.label, mark.labelAt, mark.style).setAlwaysOnTop()
                    }
                }
            }
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
            // Text is sized in blocks, so it grows with distance to stay the same size on screen, and rises to stay clear of the box.
            val scale = (meters * 0.25f).coerceAtLeast(1f)
            mark.labelStyle = TextGizmo.Style.forColorAndCentered(mark.color).withScale(TextGizmo.Style.DEFAULT_SCALE * scale)
            mark.labelAt = mark.top.add(0.0, 0.5 + meters / 16.0, 0.0)
        }
        Gizmos.billboardText(label, mark.labelAt, mark.labelStyle).setAlwaysOnTop()
    }

    private fun box(x: Double, y: Double, z: Double, mark: Mark) =
        ShownBox(AABB(x, y, z, x + 1, y + 1, z + 1), GizmoStyle.strokeAndFill(mark.role.argb(), LINE_WIDTH, mark.role.argb(FILL_ALPHA)))

    private fun Point.vec() = Vec3(x, y, z)
}
