package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Piece
import io.github.noxaur.dsmod.core.ui.SettingsSearch
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.TabStrip
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** A white sprite in `textures/gui/sprites/`, which [Kit.icon] tints. */
enum class Icon(val width: Int, val height: Int) {
    CHECK(7, 6),
    CROSS(7, 5),
    WARN(7, 7),
    UP(5, 3),
    DOWN(5, 3),
    SEARCH(6, 7),
    ARROW(7, 7),
    LOCK(5, 7),
    LEFT(3, 5),
    RIGHT(3, 5),
    BACK(7, 7),
    BOOK(7, 7),
    STAR(7, 7),
    ;

    val sprite: Identifier = Identifier.fromNamespaceAndPath("dsmod", name.lowercase())
}

/** A texture of [width] by [height] that the kit blits cells from. */
class Atlas(val id: Identifier, val width: Int, val height: Int)

/**
 * Draws ds-mod's chrome in the player's [Theme]. Every shape is a few fills in GUI pixels and keeps no state,
 * so a screen calls these every frame with numbers it worked out in `init`. `kit.md` beside the ui skill
 * describes each one.
 */
object Kit {
    enum class State { IDLE, HOVER, DOWN, OFF }

    /** Ink by job: a label, a secondary one, a disabled one, and the words a search matched. */
    enum class Ink { NORMAL, MUTED, OFF, MATCH }

    /**
     * A label with the stretches a search matched, cut and measured once when the query or the label changes, so a
     * frame only draws it. [label] draws the matches in [Ink.MATCH] and the rest in the ink it is given.
     */
    class Marked(pieces: List<Piece>) {
        val text = pieces.joinToString("") { it.text }
        internal val parts = Array(pieces.size) { pieces[it].text }
        internal val matches = BooleanArray(pieces.size) { pieces[it].match }
        internal val offsets = IntArray(pieces.size)

        init {
            var x = 0
            for (i in parts.indices) {
                offsets[i] = x
                x += font.width(parts[i])
            }
        }

        companion object {
            /** [text] marked where it holds one of [words], or null when it holds none, so it draws as a plain label. */
            fun of(text: String, words: List<String>): Marked? =
                if (words.isEmpty()) null else SettingsSearch.pieces(text, words).takeIf { p -> p.any { it.match } }?.let(::Marked)
        }
    }

    const val BUTTON_HEIGHT = 16
    const val SLOT_SIZE = 18
    const val RESULT_SLOT_SIZE = 26
    const val BAR_HEIGHT = 6
    const val SCROLLBAR_WIDTH = 6
    const val TAB_WIDTH = TabLayout.TAB_WIDTH
    const val TAB_HEIGHT = 21
    const val CHECKBOX_SIZE = 10
    const val DIVIDER_HEIGHT = 2

    /** The height of a line of text. */
    const val LABEL_HEIGHT = 8
    const val GAP = 4
    const val INSET = 7
    const val INVENTORY_WIDTH = 176
    const val INVENTORY_HEIGHT = 166
    const val TAB_BAR_HEIGHT = 18
    const val NEW_TAB_WIDTH = TabStrip.NEW
    const val SWITCH_WIDTH = 22
    const val GRIP_WIDTH = 3
    const val GRIP_HEIGHT = 7
    const val SWITCH_HEIGHT = 12
    const val SWATCH_WIDTH = 22
    const val SWATCH_HEIGHT = 13
    private const val DROPDOWN_PAD = 4
    private const val DIM_WASH = 150
    private const val PAGE_TAB_PAD = 5

    /** A page tab's swatch or icon, and its ×, each in a square this wide, the × mirroring the swatch at the other end. */
    const val PAGE_TAB_LEAD = 9
    private const val PAGE_TAB_LEAD_UP = 12
    private const val PAGE_TAB_TEXT_UP = 11

    val theme: Theme get() = DsModClient.settings.hud.theme
    val font: Font get() = Minecraft.getInstance().font

    fun state(active: Boolean, hovered: Boolean, pressed: Boolean) = when {
        !active -> State.OFF
        pressed -> State.DOWN
        hovered -> State.HOVER
        else -> State.IDLE
    }

    /** A panel, as solid as [alpha] from 0 to 255 says, for a HUD block the player lets the world show through. */
    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        val t = theme
        frame(g, x, y, width, height, fade(t.outline, alpha), fade(t.panel, alpha), fade(t.panelLight, alpha), fade(t.panelShade, alpha))
    }

    /**
     * A button with [label] centered, where [labelWidth] is the label's width, measured once. On a [glass] menu it is a
     * pane of glass raised off it instead, with its label in [GLASS_INK].
     */
    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, label: String, labelWidth: Int, glass: Boolean = false) {
        buttonFace(g, x, y, width, height, state, glass)
        val dy = if (state == State.DOWN) 1 else 0
        val lx = x + (width - labelWidth) / 2
        val ly = y + (height - 8) / 2 + dy
        label(g, label, lx, ly, if (state == State.OFF) Ink.OFF else Ink.NORMAL, glass)
    }

    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, icon: Icon, glass: Boolean = false) {
        buttonFace(g, x, y, width, height, state, glass)
        val t = theme
        val off = state == State.OFF
        val dy = if (state == State.DOWN) 1 else 0
        val ink = when {
            glass -> if (off) GLASS_INK_OFF else GLASS_INK
            else -> if (off) t.inkOff else t.ink
        }
        icon(g, icon, x + (width - icon.width) / 2, y + (height - icon.height) / 2 + dy, ink, !off && (glass || t.inkShadow))
    }

    fun well(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        bevel(g, x, y, width, height, t.well, t.wellShade, t.wellLight)
    }

    /** A text field's well, ringed in the hover outline under the mouse or with focus, as a checkbox is. */
    fun field(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        well(g, x, y, width, height)
        focusRing(g, x, y, width, height, state)
    }

    /**
     * An 18x18 slot, with the item's 16x16 at one pixel in, or a crafting result's 26x26 with it at five in. A menu draws
     * dozens of the small one each frame, so [register] paints it once per theme into [SLOT_TEXTURE] and it is drawn as
     * one blit instead of five fills. Until the paint is done it is drawn with the fills.
     */
    fun slot(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int = SLOT_SIZE, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        val t = theme
        if (size != SLOT_SIZE || t !== slotTheme) return bevel(g, x, y, size, size, fade(t.slot, alpha), fade(t.slotShade, alpha), fade(t.slotLight, alpha))
        slotBlit(g, SLOT_TEXTURE, x, y, alpha)
    }

    // A texture made or uploaded while a frame is being built broke every frame after it in the game tests, so the
    // paint happens at the end of a tick, between frames.
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            val t = theme
            if (t === slotTheme) return@register
            slotTexture = paintSlot(slotTexture, SLOT_TEXTURE, t.slot, t.slotShade, t.slotLight)
            glassSlotTexture = paintSlot(glassSlotTexture, GLASS_SLOT_TEXTURE, glassFace(t), glassShade(t), glassLight(t))
            slotTheme = t
        }
    }

    private fun paintSlot(made: DynamicTexture?, id: Identifier, face: Int, topLeft: Int, bottomRight: Int): DynamicTexture {
        val texture = made ?: DynamicTexture({ "dsmod $id" }, SLOT_SIZE, SLOT_SIZE, false).also {
            Minecraft.getInstance().textureManager.register(id, it)
        }
        bevelRects(0, 0, SLOT_SIZE, SLOT_SIZE, face, topLeft, bottomRight, checkNotNull(texture.pixels)::fillRect)
        texture.upload()
        return texture
    }

    /**
     * Glass in place of a [panel]: the HUD plate's dark fill at [alpha], from 0 for clear to 255, inside its notched
     * ring. It is dark in both themes, so a menu's labels on it take [GLASS_INK].
     */
    fun glass(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Int) {
        val t = theme
        if (alpha > 0) rect(g, x + 1, y + 1, width - 2, height - 2, ARGB.color(alpha, t.plate))
        ring(g, x, y, width, height, t.plateEdge)
    }

    /** A sunk square of glass, see-through whatever the glass's own alpha, so a clear menu still shows its grid. */
    fun glassWell(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        bevel(g, x, y, width, height, glassFace(t), glassShade(t), glassLight(t))
    }

    /** A [slot] on glass: a [glassWell] the size of a slot, painted once per theme as [slot] is. */
    fun glassSlot(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int = SLOT_SIZE, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        if (size != SLOT_SIZE || theme !== slotTheme) return glassWell(g, x, y, size, size)
        slotBlit(g, GLASS_SLOT_TEXTURE, x, y, alpha)
    }

    /**
     * A [tab] on a [glass] panel, filled at the panel's [alpha]. It stops at the panel's ring instead of reaching into
     * the panel, since two see-through fills on each other would show a darker band. The [selected] one stands 2px
     * taller, and one under the mouse is ringed in `hoverOutline`. Its content starts where a [tab]'s does.
     */
    fun glassTab(
        g: GuiGraphicsExtractor, x: Int, panelY: Int, selected: Boolean, alpha: Int, edge: TabEdge = TabEdge.TOP,
        hovered: Boolean = false, width: Int = TAB_WIDTH,
    ): Int {
        val t = theme
        val color = if (hovered) t.hoverOutline else t.plateEdge
        val fill = ARGB.color(alpha, t.plate)
        // The rows the tab stands out from the panel, its ring included.
        val out = if (selected) TAB_HEIGHT - 2 else TAB_HEIGHT - 4
        val inner = width - 2
        if (edge == TabEdge.BOTTOM) {
            if (alpha > 0) rect(g, x + 1, panelY, inner, out - 1, fill)
            rect(g, x, panelY, 1, out - 1, color)
            rect(g, x + width - 1, panelY, 1, out - 1, color)
            rect(g, x + 1, panelY + out - 1, inner, 1, color)
            return if (selected) panelY + 1 else panelY
        }
        val top = panelY - out
        if (alpha > 0) rect(g, x + 1, top + 1, inner, out - 1, fill)
        tabOutline(g, x, top, width, out - 1, color)
        return panelY - TAB_HEIGHT + if (selected) 4 else 5
    }

    /** The top and the two sides of a tab at [x], [top], [width] wide, the sides [side] tall below the top. */
    private fun tabOutline(g: GuiGraphicsExtractor, x: Int, top: Int, width: Int, side: Int, color: Int) {
        rect(g, x + 1, top, width - 2, 1, color)
        rect(g, x, top + 1, 1, side, color)
        rect(g, x + width - 1, top + 1, 1, side, color)
    }

    // Glass's sunk parts, from the plate's tokens so they follow the theme: a light lower edge and a dark upper one.
    private fun glassFace(t: Theme) = ARGB.color(GLASS_FACE_ALPHA, t.outline)
    private fun glassShade(t: Theme) = ARGB.color(GLASS_SHADE_ALPHA, t.outline)
    private fun glassLight(t: Theme) = ARGB.color(GLASS_LIGHT_ALPHA, t.plateEdge)

    /**
     * The backing of a tooltip whose text fills [width] by [height] at [x], [y]: the HUD's plate, nearly opaque so
     * the slots behind it do not show through, reaching four pixels past the text as vanilla's does.
     */
    fun tooltip(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x - 3, y - 3, width + 6, height + 6, ARGB.color(TOOLTIP_ALPHA, t.plate))
        ring(g, x - 4, y - 4, width + 8, height + 8, t.plateEdge)
    }

    /**
     * A strip of [cells] slots cut from the game's own hotbar sprite, so it looks as the player's hotbar does under any
     * resource pack. A cell is [HOTBAR_CELL] wide, and the strip [hotbarWidth] by [HOTBAR_HEIGHT]. Its item goes 3px in.
     */
    fun hotbar(g: GuiGraphicsExtractor, x: Int, y: Int, cells: Int) {
        val body = 1 + cells * HOTBAR_CELL
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_WIDTH, HOTBAR_HEIGHT, 0, 0, x, y, body, HOTBAR_HEIGHT)
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_WIDTH - 1, 0, x + body, y, 1, HOTBAR_HEIGHT)
    }

    fun hotbarWidth(cells: Int) = cells * HOTBAR_CELL + 2

    fun plate(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x + 1, y + 1, width - 2, height - 2, t.plate)
        ring(g, x, y, width, height, t.plateEdge)
    }

    /** A 6 tall well filled to [fraction] in [color], with a lit top row and a shaded bottom row. */
    fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, fraction: Float, color: Int) {
        well(g, x, y, width, BAR_HEIGHT)
        barFill(g, x + 1, y + 1, ((width - 2) * fraction).roundToInt(), 4, color)
    }

    fun barFill(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        rect(g, x, y, width, height, color)
        stripe(g, x, y, width, color, ARGB.white(1f), 0.45f)
        if (height > 2) stripe(g, x, y + height - 1, width, color, ARGB.black(1f), 0.35f)
    }

    /** A 6 wide well with its thumb [thumbY] pixels below the top of the track. */
    fun scrollbar(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int, thumbY: Int, thumbHeight: Int) {
        well(g, x, y, SCROLLBAR_WIDTH, height)
        thumb(g, x + 1, y + 1 + thumbY, SCROLLBAR_WIDTH - 2, thumbHeight, State.IDLE)
    }

    fun thumb(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, glass: Boolean = false) {
        val t = theme
        if (glass) return glassFace(g, x, y, width, height, if (state == State.DOWN) State.HOVER else state)
        val face = when (state) {
            State.OFF -> t.panel
            State.IDLE -> t.thumb
            else -> t.hover
        }
        val light = when (state) {
            State.OFF -> t.panelLight
            State.IDLE -> t.thumbLight
            else -> t.hoverLight
        }
        bevel(g, x, y, width, height, face, light, if (state == State.OFF) t.panelShade else t.raisedShade)
    }

    fun arrow(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, fraction: Float, color: Int, down: Boolean = false, glass: Boolean = false) {
        val length = if (down) height else width
        val breadth = if (down) width else height
        val head = (breadth + 1) / 2
        var thick = maxOf(2, breadth / 3)
        if ((breadth - thick) % 2 != 0) thick++
        arrowPart(g, x, y, length, length - head, breadth, thick, down, sunk(glass))
        arrowPart(g, x, y, (length * fraction.coerceIn(0f, 1f)).roundToInt(), length - head, breadth, thick, down, color)
    }

    private fun arrowPart(g: GuiGraphicsExtractor, x: Int, y: Int, upTo: Int, shaft: Int, breadth: Int, thick: Int, down: Boolean, color: Int) {
        arrowRun(g, x, y, 0, minOf(upTo, shaft), (breadth - thick) / 2, thick, down, color)
        for (i in 0 until upTo - shaft) arrowRun(g, x, y, shaft + i, 1, i, breadth - 2 * i, down, color)
    }

    private fun arrowRun(g: GuiGraphicsExtractor, x: Int, y: Int, at: Int, length: Int, offset: Int, size: Int, down: Boolean, color: Int) =
        if (down) rect(g, x + offset, y + at, size, length, color) else rect(g, x + at, y + offset, length, size, color)

    fun flame(g: GuiGraphicsExtractor, x: Int, y: Int, fraction: Float, color: Int, glass: Boolean = false) {
        val empty = sunk(glass)
        val lit = FLAME_SIZE - (FLAME_SIZE * fraction.coerceIn(0f, 1f)).roundToInt()
        for (row in 0 until FLAME_SIZE) {
            val from = FLAME[row * 2]
            rect(g, x + from, y + row, FLAME[row * 2 + 1] - from, 1, if (row >= lit) color else empty)
        }
    }

    fun bubbles(g: GuiGraphicsExtractor, x: Int, y: Int, fraction: Float, color: Int, glass: Boolean = false) {
        val empty = sunk(glass)
        val lit = BUBBLES_HEIGHT - (BUBBLES_HEIGHT * fraction.coerceIn(0f, 1f)).roundToInt()
        for (i in 0 until BUBBLES.size / 2) {
            val top = BUBBLES[i * 2 + 1]
            rect(g, x + BUBBLES[i * 2], y + top, BUBBLE, BUBBLE, if (top >= lit) color else empty)
        }
    }

    private fun sunk(glass: Boolean) = if (glass) glassFace(theme) else theme.well

    /**
     * A tab on the [edge] of a panel, where [panelY] is that edge: the panel's top, or its bottom for a tab under it.
     * The [selected] one joins the panel, so draw it after the panel. The rest sit 2px further out in `tabOff`, or in
     * `hover` while [hovered]. A tab is [width] wide, and a wider one holds a field. Its 16px content starts at the
     * returned y.
     */
    fun tab(g: GuiGraphicsExtractor, x: Int, panelY: Int, selected: Boolean, edge: TabEdge = TabEdge.TOP, hovered: Boolean = false, width: Int = TAB_WIDTH): Int =
        tab(g, x, panelY, selected, edge, hovered, width, TAB_HEIGHT)

    fun tab(g: GuiGraphicsExtractor, x: Int, panelY: Int, selected: Boolean, edge: TabEdge, hovered: Boolean, width: Int, height: Int): Int {
        val t = theme
        val w = width
        val h = height
        val face = if (hovered) t.hover else t.tabOff
        val y = panelY - h + 2
        if (!selected) {
            val bottom = edge == TabEdge.BOTTOM
            frame(g, x, if (bottom) panelY - 1 else y + 2, w, h - 3, t.outline, face, t.panelShade, t.wellShade)
            return if (bottom) panelY else y + 3
        }
        if (edge == TabEdge.BOTTOM) {
            rect(g, x, panelY, 1, h - 3, t.outline)
            rect(g, x + w - 1, panelY, 1, h - 3, t.outline)
            rect(g, x + 1, panelY + h - 3, w - 2, 1, t.outline)
            rect(g, x + 1, panelY - 2, w - 2, h - 1, t.panel)
            rect(g, x + 1, panelY - 2, 1, h - 2, t.panelLight)
            rect(g, x + w - 2, panelY - 2, 1, h - 1, t.panelShade)
            rect(g, x + 2, panelY + h - 4, w - 3, 1, t.panelShade)
            return panelY + 1
        }
        tabOutline(g, x, y, w, h - 3, t.outline)
        rect(g, x + 1, y + 1, w - 2, h - 1, t.panel)
        rect(g, x + 1, y + 1, w - 3, 1, t.panelLight)
        rect(g, x + 1, y + 1, 1, h - 2, t.panelLight)
        rect(g, x + w - 2, y + 2, 1, h - 3, t.panelShade)
        return y + 2
    }

    /** A row of browser tabs across the top of a pane: a strip in `side` with a 1px outline along its bottom, which the tabs sit on. */
    fun tabBar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
        val t = theme
        rect(g, x, y, width, TAB_BAR_HEIGHT - 1, t.side)
        rect(g, x, y + TAB_BAR_HEIGHT - 1, width, 1, t.outline)
    }

    /**
     * A browser tab on a [tabBar] whose bottom line is at [lineY]: 15 tall in `tabOff`, or `hover` under the mouse,
     * with a muted [label]. The [selected] one is 17 tall in the panel's color and covers the line, so it joins the
     * pane below, and DOWN is that tab while it is dragged, outlined as hover is. Before the label sits a [swatch]
     * color, or an [icon], or nothing when both are left out. [close] shows the × in its state, or hides it when null.
     * The label is cut to [pageTabLabel] beforehand.
     */
    fun pageTab(
        g: GuiGraphicsExtractor, x: Int, lineY: Int, width: Int, state: State, selected: Boolean, label: String,
        swatch: Int = 0, icon: Icon? = null, close: State? = null,
    ) {
        val t = theme
        val lifted = selected || state == State.DOWN
        tabFace(g, x, lineY, width, state, lifted)
        val ink = if (lifted || state == State.HOVER) Ink.NORMAL else Ink.MUTED
        var textX = x + PAGE_TAB_PAD
        val leadY = lineY - PAGE_TAB_LEAD_UP
        if (swatch != 0) {
            swatch(g, textX, leadY, PAGE_TAB_LEAD, swatch)
            textX += PAGE_TAB_LEAD + GAP
        } else if (icon != null) {
            leadIcon(g, icon, textX, leadY, if (ink == Ink.NORMAL) t.ink else t.inkMuted)
            textX += PAGE_TAB_LEAD + GAP
        }
        label(g, label, textX, lineY - PAGE_TAB_TEXT_UP, ink)
        if (close == null) return
        val cx = pageTabCloseX(x, width)
        val cy = pageTabCloseY(lineY)
        if (close == State.HOVER) {
            button(g, cx, cy, PAGE_TAB_LEAD, PAGE_TAB_LEAD, State.HOVER, Icon.CROSS)
        } else {
            leadIcon(g, Icon.CROSS, cx, cy, t.inkMuted)
        }
    }

    private fun leadIcon(g: GuiGraphicsExtractor, icon: Icon, x: Int, y: Int, color: Int) =
        icon(g, icon, x + (PAGE_TAB_LEAD - icon.width) / 2, y + (PAGE_TAB_LEAD - icon.height) / 2, color, theme.inkShadow)

    /** The room a [pageTab] [width] wide leaves its label, with a swatch or icon before it when [lead], and a × after it when [close]. */
    fun pageTabLabel(width: Int, lead: Boolean, close: Boolean) =
        width - PAGE_TAB_PAD - (if (lead) PAGE_TAB_LEAD + GAP else 0) - (if (close) GAP + PAGE_TAB_LEAD else 0) - PAGE_TAB_PAD

    /** Where the [PAGE_TAB_LEAD] square × of a [pageTab] at [x], [width] wide, starts: as far in from the right as the swatch is from the left. */
    fun pageTabCloseX(x: Int, width: Int) = x + width - PAGE_TAB_PAD - PAGE_TAB_LEAD

    fun pageTabCloseY(lineY: Int) = lineY - PAGE_TAB_LEAD_UP

    /** Role text on a [tabBar], on the line its tabs' titles sit on, such as the brand at its end. It has a text shadow in both themes. */
    fun tag(g: GuiGraphicsExtractor, x: Int, lineY: Int, text: Component) {
        g.text(font, text, x, lineY - PAGE_TAB_TEXT_UP, -1, true)
    }

    /** The New Tab button after the tabs of a [tabBar]: a 15 wide tab holding a plus. */
    fun newTab(g: GuiGraphicsExtractor, x: Int, lineY: Int, state: State) {
        tabFace(g, x, lineY, NEW_TAB_WIDTH, state, false)
        label(g, "+", x + (NEW_TAB_WIDTH - font.width("+")) / 2 + 1, lineY - PAGE_TAB_TEXT_UP, if (state == State.HOVER) Ink.NORMAL else Ink.MUTED)
    }

    private fun tabFace(g: GuiGraphicsExtractor, x: Int, lineY: Int, width: Int, state: State, lifted: Boolean) {
        val t = theme
        if (lifted) {
            val y = lineY - 16
            val edge = if (state == State.DOWN) t.hoverOutline else t.outline
            tabOutline(g, x, y, width, 16, edge)
            // Down to the line and over it, so the tab and the pane are one surface.
            rect(g, x + 1, y + 1, width - 2, 16, t.panel)
            rect(g, x + 1, y + 1, width - 3, 1, t.panelLight)
            rect(g, x + 1, y + 1, 1, 15, t.panelLight)
            rect(g, x + width - 2, y + 2, 1, 14, t.panelShade)
            return
        }
        val y = lineY - 15
        tabOutline(g, x, y, width, 14, t.outline)
        rect(g, x + 1, y + 1, width - 2, 14, if (state == State.HOVER) t.hover else t.tabOff)
        rect(g, x + 1, y + 1, width - 3, 1, t.panelShade)
        rect(g, x + 1, y + 1, 1, 13, t.panelShade)
        rect(g, x + width - 2, y + 2, 1, 13, t.wellShade)
    }

    /** A 10x10 well holding the check when [checked]. Hover and focus ring it. */
    fun checkbox(g: GuiGraphicsExtractor, x: Int, y: Int, checked: Boolean, state: State) {
        val t = theme
        well(g, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE)
        focusRing(g, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, state)
        if (checked) icon(g, Icon.CHECK, x + 2, y + 2, if (state == State.OFF) t.inkOff else GOOD, state != State.OFF)
    }

    /**
     * A switch: a 22x12 sunk track with a raised 10x10 knob, which sits on the right in the good role when [on].
     * Hover and focus outline the knob. Off, as for a switch the player cannot turn now, its knob is the panel's.
     */
    fun switch(g: GuiGraphicsExtractor, x: Int, y: Int, on: Boolean, state: State) {
        val t = theme
        val lit = state == State.HOVER || state == State.DOWN
        if (state == State.OFF) {
            // The track keeps a trace of the good role, so an on switch that cannot turn still reads as on.
            if (on) bevel(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, mix(t.well, GOOD, 0.15f), mix(t.wellShade, GOOD, 0.08f), mix(t.wellLight, GOOD, 0.2f)) else well(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT)
            frame(g, if (on) x + SWITCH_WIDTH - 11 else x + 1, y + 1, 10, 10, t.outline, t.panel, t.panel, t.panel)
            return
        }
        if (on) {
            bevel(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, mix(t.well, GOOD, 0.3f), mix(t.wellShade, GOOD, 0.15f), mix(t.wellLight, GOOD, 0.45f))
            frame(g, x + SWITCH_WIDTH - 11, y + 1, 10, 10, if (lit) t.hoverOutline else t.outline, GOOD, mix(GOOD, ARGB.white(1f), 0.5f), mix(GOOD, ARGB.black(1f), 0.45f))
        } else {
            well(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT)
            if (lit) frame(g, x + 1, y + 1, 10, 10, t.hoverOutline, t.hover, t.hoverLight, t.raisedShade) else frame(g, x + 1, y + 1, 10, 10, t.outline, t.raised, t.raisedLight, t.raisedShade)
        }
    }

    /** A setting's changed marker: a 3x3 dot in the accent color, ringed under the mouse because a press resets it. */
    fun marker(g: GuiGraphicsExtractor, x: Int, y: Int, state: State) {
        val t = theme
        rect(g, x, y, 3, 3, t.accent)
        focusRing(g, x, y, 3, 3, state)
    }

    /**
     * A dropdown's raised face: [label] after a [lead] wide preview, if any, and the down arrow at the end, in a square
     * that mirrors the label's start. It is lit while its menu is open.
     */
    fun dropdown(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, label: String, lead: Int = 0, marked: Marked? = null) {
        val t = theme
        face(g, x, y, width, height, state)
        val dy = if (state == State.DOWN) 1 else 0
        val textX = x + DROPDOWN_PAD + leadRoom(lead)
        val ink = if (state == State.OFF) Ink.OFF else Ink.NORMAL
        if (marked != null) label(g, marked, textX, y + (height - 8) / 2 + dy, ink) else label(g, label, textX, y + (height - 8) / 2 + dy, ink)
        val arrow = Icon.DOWN
        icon(g, arrow, x + width - DROPDOWN_PAD - arrow.width, y + (height - arrow.height) / 2 + dy, if (state == State.OFF) t.inkOff else t.inkMuted, t.inkShadow)
    }

    private fun leadRoom(lead: Int) = if (lead > 0) lead + GAP - 1 else 0

    /** The room a [dropdown] [width] wide leaves its label after a [lead] wide preview. */
    fun dropdownLabel(width: Int, lead: Int = 0) = width - DROPDOWN_PAD * 2 - Icon.DOWN.width - GAP - leadRoom(lead)

    /** How wide a [dropdown] must be to hold a [label] wide label after a [lead] wide preview. */
    fun dropdownWidth(label: Int, lead: Int = 0) = label + DROPDOWN_PAD * 2 + Icon.DOWN.width + GAP + leadRoom(lead)

    /** A lift of a well under the mouse or the keyboard, behind a row of a settings group. */
    fun wellRow(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, mix(theme.well, BODY, 0.05f))

    /** A wash in the well's color over something dimmed, such as a setting under a switch that is off. It stays editable. */
    fun dim(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(DIM_WASH, theme.well))

    /** Two columns of four dots on a well, which say the line they start can be dragged. [lit] brightens them under the mouse or the keys. */
    fun grip(g: GuiGraphicsExtractor, x: Int, y: Int, lit: Boolean) {
        val color = mix(theme.well, BODY, if (lit) 0.8f else 0.4f)
        for (row in 0 until 4) for (column in 0 until 2) rect(g, x + column * 2, y + row * 2, 1, 1, color)
    }

    /** A 1px line down the left of rows that open under another, joining them to it. */
    fun guide(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int) = rect(g, x, y, 1, height, mix(theme.well, BODY, 0.18f))

    /** A small panel in [look]'s colors with a well in it, as a preview of that theme. */
    fun themeSwatch(g: GuiGraphicsExtractor, x: Int, y: Int, look: Theme) {
        frame(g, x, y, SWATCH_WIDTH, SWATCH_HEIGHT, look.outline, look.panel, look.panelLight, look.panelShade)
        bevel(g, x + 3, y + 3, 9, SWATCH_HEIGHT - 6, look.well, look.wellShade, look.wellLight)
        frame(g, x + 13, y + 3, 6, SWATCH_HEIGHT - 6, look.outline, look.raised, look.raisedLight, look.raisedShade)
    }

    /** A tile of a menu of previews, ringed in the accent when [selected] and in the hover outline when lit. */
    fun tile(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, lit: Boolean, selected: Boolean) {
        val t = theme
        if (lit) rect(g, x + 1, y + 1, width - 2, height - 2, t.hover)
        if (selected || lit) ring(g, x, y, width, height, if (selected) t.accent else t.hoverOutline)
    }

    /**
     * A switched-on stretch of a range slider's track: a raised face in the accent color, which lifts under the
     * mouse and sinks while it is dragged.
     */
    fun span(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
        val face = when (state) {
            State.HOVER -> mix(t.accent, ARGB.white(1f), 0.2f)
            State.DOWN -> mix(t.accent, ARGB.black(1f), 0.25f)
            State.OFF -> t.raised
            State.IDLE -> t.accent
        }
        val light = mix(face, ARGB.white(1f), 0.45f)
        val shade = mix(face, ARGB.black(1f), 0.4f)
        val outline = if (state == State.HOVER) t.hoverOutline else t.outline
        if (state == State.DOWN) frame(g, x, y, width, height, outline, face, shade, face) else frame(g, x, y, width, height, outline, face, light, shade)
    }

    /** A white wash over something the player just acted on, fading as [strength] goes from 1 to 0. */
    fun flash(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, strength: Float) =
        rect(g, x, y, width, height, ARGB.white(strength.coerceIn(0f, 1f)))

    /** A 1px mark across a track, where a scale changes. */
    fun tick(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int) = rect(g, x, y, 1, height, theme.wellLight)

    fun divider(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
        val t = theme
        rect(g, x, y, width, 1, t.panelShade)
        rect(g, x, y + 1, width, 1, t.panelLight)
    }

    /** A 1px line across a panel, or across a well between its rows when [onWell], faint enough to sit under text. */
    fun rule(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, onWell: Boolean = false) {
        val t = theme
        rect(g, x, y, width, 1, if (onWell) mix(t.well, BODY, 0.07f) else mix(t.panel, t.ink, 0.12f))
    }

    /** A panel's face with no frame, for a screen that fills the window and has no outer panel. */
    fun backdrop(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, theme.panel)

    /** A screen's side strip, such as a settings sidebar: a step back from the panel, with an outline down its right edge. */
    fun side(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x, y, width - 1, height, t.side)
        rect(g, x + width - 1, y, 1, height, t.outline)
    }

    /**
     * A line of a navigation tree on a [side]. Under the mouse or the keyboard its face lifts a little. The
     * [selected] one lifts further and has a 2px bar in ink down its left edge, so it is told by two signals.
     */
    fun navRow(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, selected: Boolean) {
        val t = theme
        when {
            selected -> {
                rect(g, x, y, width, height, mix(t.side, t.panelLight, 0.8f))
                rect(g, x, y + 2, 2, height - 4, t.ink)
            }
            state == State.HOVER || state == State.DOWN -> rect(g, x, y, width, height, mix(t.side, t.panelLight, 0.4f))
            else -> Unit
        }
    }

    /** A square of [color] in a 1px outline, such as a section's placeholder icon or a color to pick. */
    fun swatch(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) {
        rect(g, x, y, size, size, theme.outline)
        rect(g, x + 1, y + 1, size - 2, size - 2, color)
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

    /** A 1px line of whole pixels from one point to another, with a dark line beside it so it reads over anything. */
    fun line(g: GuiGraphicsExtractor, fromX: Int, fromY: Int, toX: Int, toY: Int, color: Int) {
        val shadow = ARGB.scaleRGB(color, 0.25f)
        val dx = abs(toX - fromX)
        val dy = -abs(toY - fromY)
        val sx = if (fromX < toX) 1 else -1
        val sy = if (fromY < toY) 1 else -1
        var error = dx + dy
        var x = fromX
        var y = fromY
        while (true) {
            rect(g, x + 1, y, 1, 1, shadow)
            rect(g, x, y, 1, 1, color)
            if (x == toX && y == toY) return
            val twice = error * 2
            if (twice >= dy) {
                error += dy
                x += sx
            }
            if (twice <= dx) {
                error += dx
                y += sy
            }
        }
    }

    /** A plain string in the theme's ink, which is chrome. Text that means something is built with `text { }`. */
    fun label(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, ink: Ink = Ink.NORMAL) {
        g.text(font, text, x, y, color(ink), ink != Ink.OFF && theme.inkShadow)
    }

    fun label(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, ink: Ink, glass: Boolean) {
        if (!glass) return label(g, text, x, y, ink)
        g.text(font, text, x, y, if (ink == Ink.OFF) GLASS_INK_OFF else GLASS_INK, ink != Ink.OFF)
    }

    /** A [Marked] label: what a search matched in [Ink.MATCH], and the rest in [ink]. */
    fun label(g: GuiGraphicsExtractor, text: Marked, x: Int, y: Int, ink: Ink = Ink.NORMAL) {
        for (i in text.parts.indices) label(g, text.parts[i], x + text.offsets[i], y, if (text.matches[i]) Ink.MATCH else ink)
    }

    fun markedLabel(g: GuiGraphicsExtractor, marked: Marked?, text: String, x: Int, y: Int) {
        if (marked != null) label(g, marked, x, y) else label(g, text, x, y)
    }

    private fun color(ink: Ink) = when (ink) {
        Ink.NORMAL -> theme.ink
        Ink.MUTED -> theme.inkMuted
        Ink.OFF -> theme.inkOff
        Ink.MATCH -> theme.inkMatch
    }

    /** Fills the 16x16 item area at [x], [y] behind the item. */
    fun slotFill(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) = rect(g, x, y, 16, 16, color)

    /**
     * The game's own cooldown wash over the 16x16 item at [x], [y]: half-white over the bottom [left] of it, so it drains
     * down as the wait runs out, the way an ender pearl's does. The same in both themes, because it lies on the item.
     */
    fun cooldown(g: GuiGraphicsExtractor, x: Int, y: Int, left: Float) {
        if (left <= 0f) return
        val top = y + floor(16 * (1 - left.coerceAtMost(1f))).toInt()
        rect(g, x, top, 16, y + 16 - top, ARGB.white(COOLDOWN_ALPHA))
    }

    /** Frames the 16x16 item area at [x], [y] with a full 18x18 square, such as the two slots of a link. */
    fun slotBorder(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        rect(g, x - 1, y - 1, 18, 1, color)
        rect(g, x - 1, y + 16, 18, 1, color)
        rect(g, x - 1, y, 1, 16, color)
        rect(g, x + 16, y, 1, 16, color)
    }

    /**
     * Rings the 16x16 item area at [x], [y] as the slot the player holds, in the theme's accent, which reads on a light
     * panel and a dark one where white is lost on light gray, or over the inventory's own texture in the Vanilla theme's.
     */
    fun held(g: GuiGraphicsExtractor, x: Int, y: Int, vanilla: Boolean) =
        slotBorder(g, x, y, if (vanilla) Theme.VANILLA.accent else theme.accent)

    /** Draws cell [cell] of row [row] of a rarity atlas behind the 16x16 item area at [x], [y], covering the slot's whole 18x18. */
    fun slotArt(g: GuiGraphicsExtractor, x: Int, y: Int, atlas: Atlas, cell: Int, row: Int = 0) = cellBlit(g, atlas, x - 1, y - 1, cell, row, SlotArt.SIZE)

    /** Draws cell [cell]'s gem over the item at [x], [y], from the item's top-left pixel, in an atlas whose gems are [rows] slots down. */
    fun slotGem(g: GuiGraphicsExtractor, x: Int, y: Int, atlas: Atlas, cell: Int, rows: Int = 1) = cellBlit(g, atlas, x, y, cell, rows, SlotArt.GEM)

    private fun cellBlit(g: GuiGraphicsExtractor, atlas: Atlas, x: Int, y: Int, cell: Int, row: Int, size: Int) = g.blit(
        RenderPipelines.GUI_TEXTURED, atlas.id, x, y, (cell * SlotArt.SIZE).toFloat(), (row * SlotArt.SIZE).toFloat(), size, size, atlas.width, atlas.height,
    )

    /**
     * The player's inventory as the resource pack draws it, 176x166, so a preview of their slots matches the
     * real screen. [slotAt] draws one empty slot of it instead, for a preview that shows a slot alone.
     */
    fun inventory(g: GuiGraphicsExtractor, x: Int, y: Int) =
        g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, x, y, 0f, 0f, INVENTORY_WIDTH, INVENTORY_HEIGHT, 256, 256)

    fun slotAt(g: GuiGraphicsExtractor, x: Int, y: Int, alpha: Int = OPAQUE) {
        if (alpha > 0) g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, x, y, EMPTY_SLOT_U, EMPTY_SLOT_V, SLOT_SIZE, SLOT_SIZE, 256, 256, ARGB.white(alpha))
    }

    /**
     * A panel of any size cut from the player's inventory as the resource pack draws it, the vanilla counterpart of
     * [panel]: its corners as they are, and its edges and face stretched from one pixel's width of them.
     */
    fun inventoryPanel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        val color = ARGB.white(alpha)
        val c = PANEL_CORNER
        val w = width - 2 * c
        val h = height - 2 * c
        val right = INVENTORY_WIDTH - c
        val bottom = INVENTORY_HEIGHT - c
        // Each part: where it goes, from the panel's corner, and the region of the texture stretched over it.
        fun part(dx: Int, dy: Int, u: Int, v: Int, width: Int, height: Int, regionWidth: Int, regionHeight: Int) = g.blit(
            RenderPipelines.GUI_TEXTURED, INVENTORY, x + dx, y + dy, u.toFloat(), v.toFloat(), width, height, regionWidth, regionHeight, 256, 256, color,
        )
        part(0, 0, 0, 0, c, c, c, c)
        part(c, 0, c, 0, w, c, 1, c)
        part(c + w, 0, right, 0, c, c, c, c)
        part(0, c, 0, c, c, h, c, 1)
        part(c, c, c, c, w, h, 1, 1)
        part(c + w, c, right, c, c, h, c, 1)
        part(0, c + h, 0, bottom, c, c, c, c)
        part(c, c + h, c, bottom, w, c, 1, c)
        part(c + w, c + h, right, bottom, c, c, c, c)
    }

    /** Darkens the item at [x], [y], drawn over it. */
    fun slotDim(g: GuiGraphicsExtractor, x: Int, y: Int) = rect(g, x, y, 16, 16, ARGB.color(DIM_ALPHA, theme.well))

    /** A wash in the panel's color over a part of a screen that is waiting or has nothing to show, so a notice reads over it. */
    fun veil(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(VEIL_ALPHA, theme.panel))

    /** A block that stands in for text or an icon in a layout still loading, so the screen keeps its shape. */
    fun bone(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(BONE_ALPHA, theme.inkMuted))

    /** The wash over the slot under the mouse in a grid the mod draws, as a menu lights its own: white, half through. */
    fun slotHover(g: GuiGraphicsExtractor, x: Int, y: Int) = rect(g, x, y, 16, 16, ARGB.white(HOVER_STRENGTH))

    private fun face(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
        when (state) {
            State.IDLE -> frame(g, x, y, width, height, t.outline, t.raised, t.raisedLight, t.raisedShade) // rule:clone a table of each state's colors
            State.HOVER -> frame(g, x, y, width, height, t.hoverOutline, t.hover, t.hoverLight, t.raisedShade) // rule:clone a table of each state's colors
            State.DOWN -> frame(g, x, y, width, height, t.outline, t.down, t.wellShade, t.down) // rule:clone a table of each state's colors
            State.OFF -> frame(g, x, y, width, height, t.outline, t.panel, t.panel, t.panel) // rule:clone a table of each state's colors
        }
    }

    fun buttonFace(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, glass: Boolean) {
        if (glass) glassFace(g, x, y, width, height, state) else face(g, x, y, width, height, state)
    }

    private fun focusRing(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        if (state == State.HOVER || state == State.DOWN) ring(g, x - 1, y - 1, width + 2, height + 2, theme.hoverOutline)
    }

    private fun slotBlit(g: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, alpha: Int) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, ARGB.white(alpha))
    }

    private fun stripe(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, color: Int, tint: Int, amount: Float) = rect(g, x, y, width, 1, mix(color, tint, amount))

    /**
     * A button's face on glass: a lighter pane in the plate's ring with a lit top and left, which the mouse lightens and
     * rings in `hoverOutline`, and which sinks into a [glassWell] while pressed. Its alpha is its own, so it reads on
     * clear glass too.
     */
    private fun glassFace(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
        when (state) {
            State.IDLE, State.HOVER -> {
                val hover = state == State.HOVER
                frame(g, x, y, width, height, if (hover) t.hoverOutline else t.plateEdge, ARGB.white(if (hover) GLASS_BUTTON_HOVER else GLASS_BUTTON), glassLight(t), glassShade(t))
            }
            State.DOWN -> {
                ring(g, x, y, width, height, t.plateEdge)
                glassWell(g, x + 1, y + 1, width - 2, height - 2)
            }
            State.OFF -> ring(g, x, y, width, height, t.plateEdge)
        }
    }

    /** A raised thing: a notched outline with a bevel inside, light on the top and left. */
    private fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, outline: Int, face: Int, light: Int, shade: Int) {
        ring(g, x, y, width, height, outline)
        bevel(g, x + 1, y + 1, width - 2, height - 2, face, light, shade)
    }

    /** A face with [topLeft] along its top and left and [bottomRight] along the rest. Its two odd corners keep the face. */
    private fun bevel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, face: Int, topLeft: Int, bottomRight: Int) =
        bevelRects(x, y, width, height, face, topLeft, bottomRight) { rx, ry, rw, rh, color -> rect(g, rx, ry, rw, rh, color) }

    /** The rectangles of [bevel], in drawing order, handed to [fill] as x, y, width, height and color. */
    private inline fun bevelRects(
        x: Int, y: Int, width: Int, height: Int, face: Int, topLeft: Int, bottomRight: Int, fill: (Int, Int, Int, Int, Int) -> Unit,
    ) {
        fill(x, y, width, height, face)
        fill(x, y, width - 1, 1, topLeft)
        fill(x, y, 1, height - 1, topLeft)
        fill(x + 1, y + height - 1, width - 1, 1, bottomRight)
        fill(x + width - 1, y + 1, 1, height - 1, bottomRight)
    }

    /** [color] with its own alpha scaled by [alpha], from 0 to 255. */
    private fun fade(color: Int, alpha: Int) = if (alpha >= OPAQUE) color else ARGB.color(ARGB.alpha(color) * alpha / OPAQUE, color)

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
    private const val COOLDOWN_ALPHA = 0.5f
    const val OPAQUE = 255
    private const val HOVER_STRENGTH = 0.5f
    private const val VEIL_ALPHA = 190
    private const val BONE_ALPHA = 90
    private val SLOT_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("dsmod", "kit/slot")
    private val GLASS_SLOT_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("dsmod", "kit/glass_slot")
    private const val GLASS_FACE_ALPHA = 80
    private const val GLASS_SHADE_ALPHA = 170
    private const val GLASS_LIGHT_ALPHA = 140

    /** Ink on glass, which is dark in both themes, so it is the light ink of the HUD, with its shadow. */
    val GLASS_INK = Role.BODY.argb()
    val GLASS_INK_OFF = Role.MUTED.argb()
    private const val GLASS_BUTTON = 0.16f
    private const val GLASS_BUTTON_HOVER = 0.3f

    /** The game's hotbar: a 1px rim around 9 cells. */
    private val HOTBAR: Identifier = Identifier.withDefaultNamespace("hud/hotbar")
    private const val HOTBAR_WIDTH = 182
    const val HOTBAR_HEIGHT = 22
    const val HOTBAR_CELL = 20
    private var slotTexture: DynamicTexture? = null
    private var glassSlotTexture: DynamicTexture? = null
    private var slotTheme: Theme? = null
    private const val TOOLTIP_ALPHA = 240

    private val INVENTORY = Identifier.withDefaultNamespace("textures/gui/container/inventory.png")

    // The inventory's rounded corner, outline and bevel, past which its edges and face are the same all along.
    private const val PANEL_CORNER = 4

    // The last slot of the inventory's main grid, which holds nothing but its frame.
    private const val EMPTY_SLOT_U = 151f
    private const val EMPTY_SLOT_V = 119f
    private const val FLAME_SIZE = 14
    private val FLAME = intArrayOf(6, 8, 6, 9, 5, 9, 5, 10, 4, 10, 4, 11, 3, 11, 3, 12, 2, 12, 2, 12, 2, 12, 2, 12, 3, 11, 4, 10)
    const val BUBBLES_HEIGHT = 29
    private const val BUBBLE = 3
    private val BUBBLES = intArrayOf(1, 25, 6, 21, 2, 16, 7, 11, 3, 6, 6, 1)
    private val GOOD = Role.GOOD.argb()
    private val BODY = Role.BODY.argb()
}
