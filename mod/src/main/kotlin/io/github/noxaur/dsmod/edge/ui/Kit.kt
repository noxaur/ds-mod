package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

/** A white sprite in `textures/gui/sprites/`, which [Kit.icon] tints. */
enum class Icon(val width: Int, val height: Int) {
    CHECK(7, 6),
    CROSS(7, 5),
    WARN(7, 7),
    UP(5, 3),
    DOWN(5, 3),
    SEARCH(6, 7),
    ;

    val sprite: Identifier = Identifier.fromNamespaceAndPath("dsmod", name.lowercase())
}

/**
 * Draws ds-mod's chrome in the player's [Theme]. Every shape is a few fills in GUI pixels and keeps no state,
 * so a screen calls these every frame with numbers it worked out in `init`. `kit.md` beside the ui skill
 * describes each one.
 */
object Kit {
    enum class State { IDLE, HOVER, DOWN, OFF }

    enum class Ink { NORMAL, MUTED, OFF }

    const val BUTTON_HEIGHT = 16
    const val SLOT_SIZE = 18
    const val BAR_HEIGHT = 6
    const val SCROLLBAR_WIDTH = 6
    const val TAB_WIDTH = 22
    const val TAB_HEIGHT = 21
    const val CHECKBOX_SIZE = 10
    const val DIVIDER_HEIGHT = 2
    const val GAP = 4
    const val INSET = 7

    val theme: Theme get() = DsModClient.settings.hud.theme
    val font: Font get() = Minecraft.getInstance().font

    fun state(active: Boolean, hovered: Boolean, pressed: Boolean) = when {
        !active -> State.OFF
        pressed -> State.DOWN
        hovered -> State.HOVER
        else -> State.IDLE
    }

    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        frame(g, x, y, width, height, t.outline, t.panel, t.panelLight, t.panelShade)
    }

    /** A button with [label] centered, where [labelWidth] is the label's width, measured once. */
    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, label: String, labelWidth: Int) {
        face(g, x, y, width, height, state)
        val dy = if (state == State.DOWN) 1 else 0
        label(g, label, x + (width - labelWidth) / 2, y + (height - 8) / 2 + dy, if (state == State.OFF) Ink.OFF else Ink.NORMAL)
    }

    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, icon: Icon) {
        face(g, x, y, width, height, state)
        val t = theme
        val off = state == State.OFF
        val dy = if (state == State.DOWN) 1 else 0
        icon(g, icon, x + (width - icon.width) / 2, y + (height - icon.height) / 2 + dy, if (off) t.inkOff else t.ink, !off && t.inkShadow)
    }

    fun well(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        bevel(g, x, y, width, height, t.well, t.wellShade, t.wellLight)
    }

    /** An 18x18 slot, with the item's 16x16 at one pixel in. */
    fun slot(g: GuiGraphicsExtractor, x: Int, y: Int) {
        val t = theme
        bevel(g, x, y, SLOT_SIZE, SLOT_SIZE, t.slot, t.slotShade, t.slotLight)
    }

    fun plate(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x + 1, y + 1, width - 2, height - 2, t.plate)
        ring(g, x, y, width, height, t.plateEdge)
    }

    /** A 6 tall well filled to [fraction] in [color], with a lit top row and a shaded bottom row. */
    fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, fraction: Float, color: Int) {
        well(g, x, y, width, BAR_HEIGHT)
        val filled = ((width - 2) * fraction).roundToInt()
        rect(g, x + 1, y + 1, filled, 4, color)
        rect(g, x + 1, y + 1, filled, 1, mix(color, ARGB.white(1f), 0.45f))
        rect(g, x + 1, y + 4, filled, 1, mix(color, ARGB.black(1f), 0.35f))
    }

    /** A 6 wide well with its thumb [thumbY] pixels below the top of the track. */
    fun scrollbar(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int, thumbY: Int, thumbHeight: Int) {
        val t = theme
        well(g, x, y, SCROLLBAR_WIDTH, height)
        bevel(g, x + 1, y + 1 + thumbY, SCROLLBAR_WIDTH - 2, thumbHeight, t.thumb, t.thumbLight, t.raisedShade)
    }

    /**
     * A tab on the top edge of the panel whose top is [panelY]. Draw the tabs that are not [selected] before the
     * panel, and the selected one after it, so it joins the panel. Its content starts at the returned y.
     */
    fun tab(g: GuiGraphicsExtractor, x: Int, panelY: Int, selected: Boolean): Int {
        val t = theme
        val w = TAB_WIDTH
        val h = TAB_HEIGHT
        val y = panelY - h + 2
        if (!selected) {
            frame(g, x, y + 2, w, h - 3, t.outline, t.tabOff, t.panelShade, t.wellShade)
            return y + 3
        }
        rect(g, x + 1, y, w - 2, 1, t.outline)
        rect(g, x, y + 1, 1, h - 3, t.outline)
        rect(g, x + w - 1, y + 1, 1, h - 3, t.outline)
        rect(g, x + 1, y + 1, w - 2, h - 1, t.panel)
        rect(g, x + 1, y + 1, w - 3, 1, t.panelLight)
        rect(g, x + 1, y + 1, 1, h - 2, t.panelLight)
        rect(g, x + w - 2, y + 2, 1, h - 3, t.panelShade)
        return y + 2
    }

    /** A 10x10 well holding the check when [checked]. Hover and focus ring it. */
    fun checkbox(g: GuiGraphicsExtractor, x: Int, y: Int, checked: Boolean, state: State) {
        val t = theme
        well(g, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE)
        if (state == State.HOVER || state == State.DOWN) ring(g, x - 1, y - 1, CHECKBOX_SIZE + 2, CHECKBOX_SIZE + 2, t.hoverOutline)
        if (checked) icon(g, Icon.CHECK, x + 2, y + 2, if (state == State.OFF) t.inkOff else GOOD, state != State.OFF)
    }

    fun divider(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
        val t = theme
        rect(g, x, y, width, 1, t.panelShade)
        rect(g, x, y + 1, width, 1, t.panelLight)
    }

    /** A 1px line around a box, with its corners left out. */
    fun ring(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        rect(g, x + 1, y, width - 2, 1, color)
        rect(g, x + 1, y + height - 1, width - 2, 1, color)
        rect(g, x, y + 1, 1, height - 2, color)
        rect(g, x + width - 1, y + 1, 1, height - 2, color)
    }

    fun icon(g: GuiGraphicsExtractor, icon: Icon, x: Int, y: Int, color: Int, shadow: Boolean = true) {
        if (shadow) g.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, x + 1, y + 1, icon.width, icon.height, ARGB.scaleRGB(color, 0.25f))
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, x, y, icon.width, icon.height, color)
    }

    /** A plain string in the theme's ink, which is chrome. Text that means something is built with `text { }`. */
    fun label(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, ink: Ink = Ink.NORMAL) {
        val t = theme
        val color = when (ink) {
            Ink.NORMAL -> t.ink
            Ink.MUTED -> t.inkMuted
            Ink.OFF -> t.inkOff
        }
        g.text(font, text, x, y, color, ink != Ink.OFF && t.inkShadow)
    }

    /** Fills the 16x16 item area at [x], [y] behind the item. */
    fun slotFill(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) = rect(g, x, y, 16, 16, color)

    /** Frames the 16x16 item area at [x], [y] with a full 18x18 square. */
    fun slotBorder(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        rect(g, x - 1, y - 1, 18, 1, color)
        rect(g, x - 1, y + 16, 18, 1, color)
        rect(g, x - 1, y, 1, 16, color)
        rect(g, x + 16, y, 1, 16, color)
    }

    /** Darkens the item at [x], [y], drawn over it. */
    fun slotDim(g: GuiGraphicsExtractor, x: Int, y: Int) = rect(g, x, y, 16, 16, ARGB.color(DIM_ALPHA, theme.well))

    private fun face(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
        when (state) {
            State.IDLE -> frame(g, x, y, width, height, t.outline, t.raised, t.raisedLight, t.raisedShade)
            State.HOVER -> frame(g, x, y, width, height, t.hoverOutline, t.hover, t.hoverLight, t.raisedShade)
            State.DOWN -> frame(g, x, y, width, height, t.outline, t.down, t.wellShade, t.down)
            State.OFF -> frame(g, x, y, width, height, t.outline, t.panel, t.panel, t.panel)
        }
    }

    /** A raised thing: a notched outline with a bevel inside, light on the top and left. */
    private fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, outline: Int, face: Int, light: Int, shade: Int) {
        ring(g, x, y, width, height, outline)
        bevel(g, x + 1, y + 1, width - 2, height - 2, face, light, shade)
    }

    /** A face with [topLeft] along its top and left and [bottomRight] along the rest. Its two odd corners keep the face. */
    private fun bevel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, face: Int, topLeft: Int, bottomRight: Int) {
        rect(g, x, y, width, height, face)
        rect(g, x, y, width - 1, 1, topLeft)
        rect(g, x, y, 1, height - 1, topLeft)
        rect(g, x + 1, y + height - 1, width - 1, 1, bottomRight)
        rect(g, x + width - 1, y + 1, 1, height - 1, bottomRight)
    }

    private fun rect(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (width > 0 && height > 0) g.fill(x, y, x + width, y + height, color)
    }

    private fun mix(from: Int, to: Int, amount: Float) = ARGB.color(
        ARGB.alpha(from),
        channel(ARGB.red(from), ARGB.red(to), amount),
        channel(ARGB.green(from), ARGB.green(to), amount),
        channel(ARGB.blue(from), ARGB.blue(to), amount),
    )

    private fun channel(from: Int, to: Int, amount: Float) = (from + (to - from) * amount).roundToInt()

    private const val DIM_ALPHA = 184
    private val GOOD = Role.GOOD.argb()
}
