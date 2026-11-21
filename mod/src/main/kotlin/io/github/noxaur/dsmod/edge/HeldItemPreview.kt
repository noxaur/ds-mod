package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import io.github.noxaur.dsmod.core.hand.Frame
import io.github.noxaur.dsmod.core.hand.HandPreview
import io.github.noxaur.dsmod.core.hand.HeldItem
import io.github.noxaur.dsmod.core.hand.ScreenView
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.edge.mixin.GuiGraphicsAccessor
import io.github.noxaur.dsmod.edge.mixin.ItemLayerAccessor
import io.github.noxaur.dsmod.edge.mixin.ItemRenderStateAccessor
import io.github.noxaur.dsmod.edge.ui.Box
import io.github.noxaur.dsmod.edge.ui.BoxEditor
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.SettingRows
import it.unimi.dsi.fastutil.floats.FloatArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

private const val HAND_FOV = 70f
private const val HAND_NEAR = 0.05f
private const val HAND_FAR = 100f


class HeldItemPicture(
    val stack: ItemStack,
    val arm: HumanoidArm,
    val attack: Float,
    val frame: Frame,
    val aspect: Float,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int,
    private val scissor: ScreenRectangle?,
) : PictureInPictureRenderState {
    override fun x0() = left
    override fun y0() = top
    override fun x1() = right
    override fun y1() = bottom
    override fun scale() = 1f
    override fun scissorArea() = scissor
    override fun bounds(): ScreenRectangle? = PictureInPictureRenderState.getBounds(left, top, right, bottom, scissor)
}

class HeldItemPictureRenderer : PictureInPictureRenderer<HeldItemPicture>() {
    private val projection = Projection()
    private val buffer = ProjectionMatrixBuffer("PIP - held item")
    private val matrix = Matrix4f()
    private val crop = Matrix4f()
    private val item = ItemStackRenderState()

    override fun getRenderStateClass() = HeldItemPicture::class.java

    override fun getTextureLabel() = "held item"

    override fun renderToTexture(state: HeldItemPicture, poseStack: PoseStack, collector: SubmitNodeCollector) {
        val client = Minecraft.getInstance()
        client.gameRenderer.lighting().setupFor(if (client.level != null) Lighting.Entry.LEVEL else Lighting.Entry.ITEMS_3D)
        projection.setupPerspective(HAND_NEAR, HAND_FAR, HAND_FOV, state.aspect, 1f)
        val f = state.frame
        crop.identity().scale(1 / f.width, 1 / f.height, 1f).translate(1 - f.left - f.right, f.top + f.bottom - 1, 0f)
        RenderSystem.setProjectionMatrix(buffer.getBuffer(crop.mul(projection.getMatrix(matrix), matrix)), ProjectionType.PERSPECTIVE)
        val pose = PoseStack()
        HeldItems.firstPerson(pose, state.arm, state.stack, state.attack)
        HeldItemPreview.resolve(item, state.stack, state.arm)
        HeldItems.drawing = true
        try {
            item.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
        } finally {
            HeldItems.drawing = false
        }
    }

    override fun close() {
        super.close()
        buffer.close()
    }
}

class HeldItemPreview : BoxEditor {
    override val box = Box()

    private val buttonX = IntArray(HandPreview.entries.size)
    private val buttonWidth = IntArray(HandPreview.entries.size)

    private val measured = ItemStackRenderState()
    private val point = Vector3f()
    private val clip = Vector4f()
    private val pose = PoseStack()
    private val projection = Matrix4f()

    override val capturing get() = false

    override fun layout(x: Int, y: Int, width: Int): Int {
        box.place(x, y, width)
        var at = 0
        for (view in HandPreview.entries) {
            buttonX[view.ordinal] = at
            buttonWidth[view.ordinal] = Kit.font.width(view.toString()) + BUTTON_PAD * 2
            at += buttonWidth[view.ordinal] + BUTTON_GAP
        }
        box.height = HEIGHT
        return HEIGHT
    }

    override fun refresh() = Unit

    private fun buttonAt(mouseX: Double, mouseY: Double): HandPreview? =
        HandPreview.entries.firstOrNull { box.isOver(mouseX, mouseY, box.x + buttonX[it.ordinal], buttonWidth[it.ordinal], BUTTON_HEIGHT) }

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        val y = box.origin + box.top
        if (y + HEIGHT <= box.clipTop || y >= box.clipBottom) return null
        val hovered = buttonAt(mouseX.toDouble(), mouseY.toDouble())
        for (view in HandPreview.entries) {
            val state = if (view == shown) Kit.State.DOWN else if (view == hovered) Kit.State.HOVER else Kit.State.IDLE
            Kit.button(g, box.x + buttonX[view.ordinal], y, buttonWidth[view.ordinal], BUTTON_HEIGHT, state, view.toString(), Kit.font.width(view.toString()))
        }
        val top = y + BUTTON_HEIGHT + GAP
        Kit.well(g, box.x, top, box.width, VIEW_HEIGHT)
        val left = box.x + 1
        val right = box.x + box.width - 1
        val bottom = top + VIEW_HEIGHT - 1
        val clipTop = maxOf(top + 1, box.clipTop)
        val clipBottom = minOf(bottom, box.clipBottom)
        if (clipBottom <= clipTop) return null

        val client = Minecraft.getInstance()
        val window = client.window
        val aspect = window.width.toFloat() / window.height
        val shape = (right - left).toFloat() / (bottom - top - 1) / aspect
        val arm = client.player?.mainArm ?: HumanoidArm.RIGHT
        val stack = held()
        val ticks = HeldItems.previewTicks(stack)
        val item = itemFrame(stack, arm, if (shown == HandPreview.HELD) 0 else ticks)
        val frame = when {
            item == null -> ScreenView.frame(Frame(0.6f, 0.6f, 1f, 1f), arm == HumanoidArm.RIGHT, shape)
            shown == HandPreview.SCREEN -> ScreenView.frame(item, arm == HumanoidArm.RIGHT, shape)
            else -> ScreenView.fit(item, shape)
        }
        g.enableScissor(left, clipTop, right, clipBottom)
        if (shown == HandPreview.SCREEN) screen(g, frame, left, top + 1, right - left, bottom - top - 1, window.guiScaledWidth)
        g.disableScissor()
        val attack = if (shown == HandPreview.HELD) 0f else HeldItem.previewSwing(System.nanoTime(), ticks)
        (g as GuiGraphicsAccessor).`dsmod$guiRenderState`().addPicturesInPictureState(
            HeldItemPicture(stack, arm, attack, frame, aspect, left, top + 1, right, bottom, ScreenRectangle(left, clipTop, right - left, clipBottom - clipTop)),
        )
        return null
    }

    private fun screen(g: GuiGraphicsExtractor, f: Frame, left: Int, top: Int, width: Int, height: Int, guiWidth: Int) {
        fun x(at: Float) = left + ((at - f.left) / f.width * width).toInt()
        fun y(at: Float) = top + ((at - f.top) / f.height * height).toInt()
        val sx = x(0f)
        val sy = y(0f)
        val sw = x(1f) - sx
        val sh = y(1f) - sy
        Kit.gameScreen(g, sx, sy, sw, sh, sw.toFloat() / guiWidth)
        if (!f.onScreen) Kit.ring(g, sx - 1, sy - 1, sw + 2, sh + 2, Kit.theme.accent)
    }

    private fun held(): ItemStack {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: ItemStack.EMPTY
        return if (stack.isEmpty || stack.has(DataComponents.MAP_ID)) SWORD else stack
    }

    private fun itemFrame(stack: ItemStack, arm: HumanoidArm, ticks: Int): Frame? {
        val window = Minecraft.getInstance().window
        projection.setPerspective(HAND_FOV * DEGREES, window.width.toFloat() / window.height, HAND_NEAR, HAND_FAR)
        resolve(measured, stack, arm)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        val samples = if (ticks == 0) 0 else SWING_SAMPLES
        HeldItems.drawing = true
        try {
            outline.clear()
            visitOutline(measured) {
                outline.add(it.x)
                outline.add(it.y)
                outline.add(it.z)
            }
            for (i in 0..samples) {
                pose.setIdentity()
                HeldItems.firstPerson(pose, arm, stack, if (samples == 0) 0f else i.toFloat() / (samples + 1))
                val matrix = pose.last().pose()
                for (at in 0 until outline.size step 3) {
                    matrix.transformPosition(point.set(outline.getFloat(at), outline.getFloat(at + 1), outline.getFloat(at + 2)))
                    projection.transform(clip.set(point, 1f))
                    if (clip.w > HAND_NEAR) {
                        val x = (clip.x / clip.w + 1) / 2
                        val y = (1 - clip.y / clip.w) / 2
                        left = minOf(left, x)
                        right = maxOf(right, x)
                        top = minOf(top, y)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
        } finally {
            HeldItems.drawing = false
        }
        return if (left > right) null else Frame(left, top, right, bottom)
    }

    private val outline = FloatArrayList()
    private val layerPose = PoseStack.Pose()
    private val corner = Vector3f()

    private fun visitOutline(state: ItemStackRenderState, visit: (Vector3f) -> Unit) {
        val layers = state as ItemRenderStateAccessor
        var visited = false
        for (i in 0 until layers.`dsmod$activeLayerCount`()) {
            val layer = layers.`dsmod$layers`()[i] as ItemLayerAccessor
            val quads = layer.`dsmod$quads`()
            val edges = quads.filter { it.direction().axis != Direction.Axis.Z }.ifEmpty { quads }
            if (edges.isEmpty()) continue
            layerPose.setIdentity()
            layer.`dsmod$applyTransform`(layerPose)
            for (quad in edges) for (vertex in 0 until BakedQuad.VERTEX_COUNT) visit(corner.set(quad.position(vertex)).mulPosition(layerPose.pose()))
            visited = true
        }
        if (!visited) state.visitExtents { visit(corner.set(it)) }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val view = buttonAt(event.x, event.y) ?: return false
        shown = view
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double) = false

    override fun mouseReleased(event: MouseButtonEvent) = false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean) = false

    override fun keyPressed(event: KeyEvent): Boolean = when {
        event.isLeft -> true.also { shown = shown.step(-1) }
        event.isRight -> true.also { shown = shown.step(1) }
        else -> false
    }

    override fun narrate(output: NarrationElementOutput): Boolean {
        output.add(NarratedElementType.TITLE, Narration.join("Preview", shown.toString(), Narration.of(shown.ordinal, HandPreview.entries.size)))
        output.add(NarratedElementType.HINT, "Left and Right change the view.")
        return true
    }

    companion object {
        var shown = HandPreview.HELD

        fun resolve(state: ItemStackRenderState, stack: ItemStack, arm: HumanoidArm) {
            val client = Minecraft.getInstance()
            val player = client.player
            val context = if (arm == HumanoidArm.RIGHT) ItemDisplayContext.FIRST_PERSON_RIGHT_HAND else ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            client.itemModelResolver.updateForTopItem(state, stack, context, player?.level(), player, 0)
        }

        private val SWORD by lazy { ItemStack(Items.DIAMOND_SWORD) }
        private const val DEGREES = (Math.PI / 180).toFloat()
        private const val SWING_SAMPLES = 10
        private const val BUTTON_HEIGHT = 16
        private const val BUTTON_PAD = 6
        private const val BUTTON_GAP = 2
        private const val GAP = 4
        private const val VIEW_HEIGHT = 120
        const val HEIGHT = BUTTON_HEIGHT + GAP + VIEW_HEIGHT


    }
}
