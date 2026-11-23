package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Colors
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.FILL_ALPHA
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.core.world.Waypoint
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.gizmos.GizmoProperties
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.FormattedCharSequence
import net.minecraft.network.chat.Style
import net.minecraft.util.ARGB
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

private const val BEAM_HEIGHT = 64.0
private const val LINE_WIDTH = 2f

private const val TRACER_AHEAD = 1.0

// @keep Up close a label is as big as a vanilla name tag, and farther off it keeps the size of the game's own text on screen.
private const val NAME_TAG_SCALE = 0.025f
private const val LINE = 10f
private const val LABEL_GAP = 2f

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
    internal class ShownBeam(entity: Int, val label: WorldLabel) : Follow(entity) {
        var labelAt: Vec3? = null
    }
    internal class ShownTracer(entity: Int, val color: Int) : Follow(entity)
    internal class ShownWaypoint(val at: Vec3, val top: Vec3?, val label: WorldLabel, val box: ShownBox?, val onTop: Boolean) : Shown {
        val labelAt: Vec3 = at.add(0.0, 1.0, 0.0)
    }

    /** @keep Every text the mod draws in the world is one of these, so world text has one look: a name in its color over the
     * distance in muted, each traced in its own color darkened, as a sign dyed with a glow ink sac is, which keeps it readable on
     * sky and ground alike. Text on the HUD and on screens keeps vanilla's drop shadow instead. */
    internal class WorldLabel(val name: String, val color: Int, private val distance: Boolean = true) {
        var meters = -1
        private val font get() = Minecraft.getInstance().font
        val title: FormattedCharSequence? = name.takeIf { it.isNotEmpty() }?.let { FormattedCharSequence.forward(it, Style.EMPTY) }
        val titleWidth = title?.let(font::width) ?: 0
        var meterLine: FormattedCharSequence? = null
        var meterWidth = 0
        val titleEdge = outline(color)
        val lines = (if (title != null) 1 else 0) + (if (distance) 1 else 0)

        fun at(meters: Int) {
            if (meters == this.meters || !distance) return
            this.meters = meters
            val text = "${meters}m"
            meterLine = FormattedCharSequence.forward(text, Style.EMPTY)
            meterWidth = font.width(text)
        }
    }

    class FrameBox(box: AABB, role: Role, style: BoxStyle, width: Float, internal val onTop: Boolean, label: String?) {
        // @keep A box drawn behind the world lies on the block's faces, and would flicker against them.
        val box: AABB = if (onTop) box else box.inflate(0.002)
        internal val style: GizmoStyle = when (style) {
            BoxStyle.OUTLINE -> GizmoStyle.stroke(role.argb(), width)
            BoxStyle.FILLED -> GizmoStyle.fill(role.argb(FILL_ALPHA))
            BoxStyle.BOTH -> GizmoStyle.strokeAndFill(role.argb(), width, role.argb(FILL_ALPHA))
        }
        internal val top = Vec3(box.center.x, box.maxY, box.center.z)
        internal val label = label?.let { WorldLabel(it, role.argb(), distance = false) }
    }

    private val marks = LinkedHashMap<String, List<Shown>>()
    internal var shown = emptyList<Shown>()
    private val beams = LinkedHashMap<String, List<ShownBeam>>()
    internal var beamsShown = emptyList<ShownBeam>()
    private val tracers = LinkedHashMap<String, List<ShownTracer>>()
    internal var tracersShown = emptyList<ShownTracer>()
    private var frame: FrameBox? = null
    private var pixel = 0f

    fun show(id: String, marks: List<Mark>) {
        beams[id] = marks.filterIsInstance<EntityBeam>().map { ShownBeam(it.entity, WorldLabel(it.label, it.role.argb())) }
        tracers[id] = marks.filterIsInstance<Tracer>().map { ShownTracer(it.entity, it.role.argb()) }
        this.marks[id] = marks.mapNotNull { mark ->
            when (mark) {
                is Waypoint -> waypoint(mark)
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
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            if (shown.isNotEmpty() || beamsShown.isNotEmpty() || frame != null) game.metrics.time("world.labels", Metrics.FRAME) { labels(context) }
        }
        Ticks.end { client ->
            val level = client.level ?: return@end
            val camera = client.gameRenderer.mainCamera().position()
            find(level)
            for (i in shown.indices) {
                when (val mark = shown[i]) {
                    is ShownBox -> Gizmos.cuboid(mark.box, mark.style)
                    is ShownWaypoint -> {
                        mark.label.at(camera.distanceTo(mark.at).toInt())
                        mark.box?.let { box -> Gizmos.cuboid(box.box, box.style).onTop(mark.onTop) }
                        mark.top?.let { top -> Gizmos.line(above(mark.label, mark.labelAt, camera), top, mark.label.color, LINE_WIDTH * 2).onTop(mark.onTop) }
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
            mark.labelAt = null
            val entity = mark.found ?: continue
            if (entity.isRemoved) continue
            val at = entity.getPosition(partial)
            val top = at.add(0.0, entity.bbHeight.toDouble(), 0.0)
            mark.label.at(eye.distanceTo(at).toInt())
            mark.labelAt = top
            Gizmos.line(above(mark.label, top, eye), top.add(0.0, BEAM_HEIGHT, 0.0), mark.label.color, LINE_WIDTH * 2).setAlwaysOnTop()
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
        if (mark.label != null) frame = mark
    }

    /** @keep Labels are placed each frame, because a gizmo's text has no outline, and the size that keeps a label readable at
     * any distance follows the camera. A frame box's label is drawn in the frame that placed the box. */
    private fun labels(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        val camera = context.levelState().cameraRenderState
        pixel = client.window.guiScale.toFloat() * 2f / (client.window.height * camera.projectionMatrix.m11())
        for (i in shown.indices) {
            val mark = shown[i] as? ShownWaypoint ?: continue
            label(context, camera.pos, camera.orientation, mark.label, mark.labelAt, mark.onTop)
        }
        for (i in beamsShown.indices) {
            val mark = beamsShown[i]
            label(context, camera.pos, camera.orientation, mark.label, mark.labelAt ?: continue, true)
        }
        // @keep A miss label says why the warp fails, often a block in the way, so it shows through that block.
        frame?.let { box -> box.label?.let { label(context, camera.pos, camera.orientation, it, box.top, true) } }
        frame = null
    }

    private fun label(context: LevelRenderContext, eye: Vec3, facing: org.joml.Quaternionfc, label: WorldLabel, at: Vec3, onTop: Boolean) {
        if (label.lines == 0) return
        val scale = scale(eye, at)
        val pose = context.poseStack()
        pose.pushPose()
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z)
        pose.mulPose(facing)
        pose.scale(scale, -scale, scale)
        var y = -label.lines * LINE - LABEL_GAP
        label.title?.let { title ->
            line(context, pose, onTop, -label.titleWidth / 2f, y, title, label.color, label.titleEdge)
            y += LINE
        }
        label.meterLine?.let { meters -> line(context, pose, onTop, -label.meterWidth / 2f, y, meters, MUTED, MUTED_EDGE) }
        pose.popPose()
    }

    /** @keep A see-through line goes to the pass that draws gizmos on top, which runs after the world. The pass for text runs
     * before Sodium's terrain, which would paint over it. */
    private fun line(context: LevelRenderContext, pose: PoseStack, onTop: Boolean, x: Float, y: Float, text: FormattedCharSequence, color: Int, edge: Int) {
        val last = (context.submitNodeCollector().order(0) as? SubmitNodeCollection)?.alwaysOnTop?.takeIf { onTop }
        if (last == null) {
            val mode = if (onTop) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
            return context.submitNodeCollector().submitText(pose, x, y, text, false, mode, LightCoordsUtil.FULL_BRIGHT, color, 0, edge)
        }
        last.submit(TextFeatureRenderer.Submit(Matrix4f(pose.last().pose()), x, y, text, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, color, 0, edge))
    }

    private fun scale(eye: Vec3, at: Vec3) = (pixel * eye.distanceTo(at).toFloat()).coerceAtLeast(NAME_TAG_SCALE)

    /** @keep A beam rises from the top of its label, so it never crosses the text. The label's size on screen is fixed, so its
     * height in the world follows the distance, which a tick's beam takes from the last frame. */
    private fun above(label: WorldLabel, at: Vec3, eye: Vec3): Vec3 =
        if (label.lines == 0) at else at.add(0.0, ((label.lines * LINE + LABEL_GAP + 2) * scale(eye, at)).toDouble(), 0.0)

    // @keep Vanilla's glowing sign darkens its text color to 40% for the outline.
    private fun outline(color: Int) = ARGB.opaque(ARGB.scaleRGB(color, 0.4f))

    private val MUTED = Role.MUTED.argb()
    private val MUTED_EDGE = outline(MUTED)

    private fun waypoint(mark: Waypoint): ShownWaypoint {
        val at = mark.at.vec()
        val solid = Colors.rgb(mark.color) or (Colors.OPAQUE shl 24)
        val box = mark.look.box?.let { style ->
            val cube = AABB(mark.at.x - 0.5, mark.at.y, mark.at.z - 0.5, mark.at.x + 0.5, mark.at.y + 1, mark.at.z + 0.5)
            // @keep A box drawn behind the world lies on the block's faces, and would flicker against them.
            ShownBox(if (mark.throughWalls) cube else cube.inflate(0.002), when (style) {
                BoxStyle.OUTLINE -> GizmoStyle.stroke(solid, LINE_WIDTH)
                BoxStyle.FILLED -> GizmoStyle.fill(mark.color)
                BoxStyle.BOTH -> GizmoStyle.strokeAndFill(solid, LINE_WIDTH, mark.color)
            })
        }
        val top = if (mark.look.beam) at.add(0.0, BEAM_HEIGHT, 0.0) else null
        return ShownWaypoint(at, top, WorldLabel(mark.label, solid, mark.distance), box, mark.throughWalls)
    }

    private fun GizmoProperties.onTop(onTop: Boolean) {
        if (onTop) setAlwaysOnTop()
    }

    private fun Point.vec() = Vec3(x, y, z)
}
