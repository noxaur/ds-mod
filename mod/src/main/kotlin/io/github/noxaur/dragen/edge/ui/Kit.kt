package io.github.noxaur.dragen.edge.ui

import com.mojang.blaze3d.platform.NativeImage
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.container.SlotArt
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.ui.ColorPick
import io.github.noxaur.dragen.core.ui.GameScreenColors
import io.github.noxaur.dragen.core.ui.Piece
import io.github.noxaur.dragen.core.ui.SettingsSearch
import io.github.noxaur.dragen.core.ui.TabEdge
import io.github.noxaur.dragen.core.ui.TabLayout
import io.github.noxaur.dragen.core.ui.TabStrip
import io.github.noxaur.dragen.core.ui.Theme
import io.github.noxaur.dragen.edge.Ticks
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

    val sprite: Identifier = Identifier.fromNamespaceAndPath("dragen", name.lowercase())
}

class Atlas(val id: Identifier, val width: Int, val height: Int)

object Kit {
    enum class State { IDLE, HOVER, DOWN, OFF }

    enum class Ink { NORMAL, MUTED, OFF, MATCH }

    class Marked(pieces: List<Piece>) {
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
            fun of(text: String, words: List<String>): Marked? =
                if (words.isEmpty()) null else SettingsSearch.pieces(text, words).takeIf { p -> p.any { it.match } }?.let(::Marked)
        }
    }

    const val BUTTON_HEIGHT = 16
    const val SLOT_SIZE = 18
    const val BAR_HEIGHT = 6
    const val SCROLLBAR_WIDTH = 6
    data class Frame(val x: Int, val y: Int) {
        val left get() = x + INSET
        val top get() = y + INSET
    }

    fun underTabs(screenWidth: Int, screenHeight: Int, width: Int, height: Int) = Frame((screenWidth - width) / 2, (screenHeight - height + TAB_HEIGHT) / 2)

    const val TAB_WIDTH = TabLayout.TAB_WIDTH
    const val TAB_HEIGHT = 21
    const val CHECKBOX_SIZE = 10

    const val LABEL_HEIGHT = 8
    const val GAP = 4
    const val INSET = 7
    const val INVENTORY_WIDTH = 176
    const val TAB_BAR_HEIGHT = 18
    const val NEW_TAB_WIDTH = TabStrip.NEW
    const val SWITCH_WIDTH = 22
    const val GRIP_WIDTH = 3
    const val GRIP_HEIGHT = 7
    const val SWITCH_HEIGHT = 12
    const val SWATCH_WIDTH = 22
    const val SWATCH_HEIGHT = 13
    const val COLOR_SWATCH = 10
    private const val DROPDOWN_PAD = 4
    private const val DIM_WASH = 150
    private const val PAGE_TAB_PAD = 5

    const val PAGE_TAB_LEAD = 9
    private const val PAGE_TAB_LEAD_UP = 12
    private const val PAGE_TAB_TEXT_UP = 11

    private lateinit var hud: DragenConfig.Hud

    val theme: Theme get() = hud.theme.value
    val font: Font get() = Minecraft.getInstance().font

    fun state(active: Boolean, hovered: Boolean, pressed: Boolean) = when {
        !active -> State.OFF
        pressed -> State.DOWN
        hovered -> State.HOVER
        else -> State.IDLE
    }

    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        val t = theme
        frame(g, x, y, width, height, fade(t.outline, alpha), fade(t.panel, alpha), fade(t.panelLight, alpha), fade(t.panelShade, alpha))
    }

    fun stemPanel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, stemX: Int, stemWidth: Int, stemHeight: Int) {
        val t = theme
        if (stemX < x + 3 || stemX + stemWidth > x + width - 3) return panel(g, x, y, width, height + stemHeight)
        val bottom = y + height
        val right = stemX + stemWidth
        litFace(g, x, y, width, height - 2, t.panel, t.panelLight, t.panelShade)
        rect(g, stemX, bottom - 1, stemWidth, 1, t.panel)
        rect(g, stemX + 1, bottom, stemWidth - 2, stemHeight - 1, t.panel)
        rect(g, stemX + 1, bottom, 1, stemHeight - 2, t.panelLight)
        rect(g, x + 2, bottom - 2, stemX - x - 2, 1, t.panelShade)
        rect(g, right, bottom - 2, x + width - 2 - right, 1, t.panelShade)
        rect(g, right - 1, bottom - 1, 1, 1, t.panelShade)
        rect(g, right - 2, bottom, 1, stemHeight - 1, t.panelShade)
        rect(g, stemX + 2, bottom + stemHeight - 2, stemWidth - 3, 1, t.panelShade)
        val edge = t.outline
        rect(g, x + 1, y, width - 2, 1, edge)
        rect(g, x, y + 1, 1, height - 2, edge)
        rect(g, x + width - 1, y + 1, 1, height - 2, edge)
        rect(g, x + 1, bottom - 1, stemX - x - 1, 1, edge)
        rect(g, right, bottom - 1, x + width - 1 - right, 1, edge)
        rect(g, stemX, bottom, 1, stemHeight - 1, edge)
        rect(g, right - 1, bottom, 1, stemHeight - 1, edge)
        rect(g, stemX + 1, bottom + stemHeight - 1, stemWidth - 2, 1, edge)
    }

    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, label: String, labelWidth: Int) {
        face(g, x, y, width, height, state)
        val dy = if (state == State.DOWN) 1 else 0
        val lx = x + (width - labelWidth) / 2
        val ly = y + (height - 8) / 2 + dy
        label(g, label, lx, ly, if (state == State.OFF) Ink.OFF else Ink.NORMAL)
    }

    fun button(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State, icon: Icon) {
        face(g, x, y, width, height, state)
        val t = theme
        val off = state == State.OFF
        val dy = if (state == State.DOWN) 1 else 0
        val ink = if (off) t.inkOff else t.ink
        icon(g, icon, x + (width - icon.width) / 2, y + (height - icon.height) / 2 + dy, ink, !off && t.inkShadow)
    }

    fun well(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        bevel(g, x, y, width, height, t.well, t.wellShade, t.wellLight)
    }

    fun field(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        well(g, x, y, width, height)
        focusRing(g, x, y, width, height, state)
    }

    fun slot(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int = SLOT_SIZE, alpha: Int = OPAQUE) {
        if (alpha <= 0) return
        val t = theme
        if (size != SLOT_SIZE || t !== slotTheme) return bevel(g, x, y, size, size, fade(t.slot, alpha), fade(t.slotShade, alpha), fade(t.slotLight, alpha))
        slotBlit(g, SLOT_TEXTURE, x, y, alpha)
    }

    // @keep A texture made or uploaded while a frame is being built breaks every frame after it, so paint at the end of a tick.
    fun register(hud: DragenConfig.Hud) {
        this.hud = hud
        Ticks.end {
            val t = theme
            if (t === slotTheme) return@end
            slotTexture = paintSlot(slotTexture, SLOT_TEXTURE, t.slot, t.slotShade, t.slotLight)
            slotTheme = t
        }
        Ticks.end { if (!colorsPainted) paintColors() }
    }

    private fun paintColors() {
        val image = NativeImage(RAMP, COLOR_ATLAS_HEIGHT, true)
        for (i in 0 until RAMP) {
            val f = i / (RAMP - 1f)
            image.setPixel(i, HUE_ROW, ColorPick.hsv(f * 360f, 1f, 1f) or OPAQUE_BLACK)
            image.setPixel(i, SATURATION_ROW, ARGB.white(((1 - f) * 255).roundToInt()))
            image.setPixel(i, OPACITY_ROW, ARGB.white((f * 255).roundToInt()))
            for (j in 0 until CHECKER_HEIGHT) image.setPixel(i, CHECKER_ROW + j, if ((i / CHECKER + j / CHECKER) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK)
        }
        val texture = DynamicTexture({ "Dragen colors" }, image)
        Minecraft.getInstance().textureManager.register(COLOR_ATLAS, texture)
        texture.upload()
        colorsPainted = true
    }

    private fun colorBlit(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, u: Int, v: Int, regionWidth: Int, regionHeight: Int, color: Int = -1) {
        if (colorsPainted) g.blit(RenderPipelines.GUI_TEXTURED, COLOR_ATLAS, x, y, u.toFloat(), v.toFloat(), width, height, regionWidth, regionHeight, RAMP, COLOR_ATLAS_HEIGHT, color)
    }

    private fun checker(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) =
        colorBlit(g, x, y, width, height, 0, CHECKER_ROW, width.coerceAtMost(RAMP), height.coerceAtMost(CHECKER_HEIGHT))

    fun colorSwatch(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, argb: Int) {
        rect(g, x, y, size, size, theme.outline)
        if (ARGB.alpha(argb) < OPAQUE) checker(g, x + 1, y + 1, size - 2, size - 2)
        rect(g, x + 1, y + 1, size - 2, size - 2, argb)
    }

    fun colorField(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, hue: Int) {
        rect(g, x, y, width, height, hue)
        colorBlit(g, x, y, width, height, 0, SATURATION_ROW, RAMP, 1)
        g.fillGradient(x, y, x + width, y + height, 0, OPAQUE_BLACK)
    }

    fun hueStrip(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = colorBlit(g, x, y, width, height, 0, HUE_ROW, RAMP, 1)

    fun opacityStrip(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, argb: Int) {
        checker(g, x, y, width, height)
        colorBlit(g, x, y, width, height, 0, OPACITY_ROW, RAMP, 1, ARGB.opaque(argb))
    }

    fun fieldMarker(g: GuiGraphicsExtractor, x: Int, y: Int) {
        ring(g, x - 3, y - 3, 7, 7, OPAQUE_BLACK)
        ring(g, x - 2, y - 2, 5, 5, -1)
    }

    fun stripKnob(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int) {
        ring(g, x - 3, y - 2, 7, height + 4, OPAQUE_BLACK)
        ring(g, x - 2, y - 1, 5, height + 2, -1)
    }

    private fun paintSlot(made: DynamicTexture?, id: Identifier, face: Int, topLeft: Int, bottomRight: Int): DynamicTexture {
        val texture = made ?: DynamicTexture({ "dragen $id" }, SLOT_GRID_WIDTH, SLOT_GRID_HEIGHT, false).also {
            Minecraft.getInstance().textureManager.register(id, it)
        }
        val pixels = checkNotNull(texture.pixels)
        for (row in 0 until SLOT_GRID_ROWS) for (column in 0 until SLOT_GRID_COLUMNS) {
            bevelRects(column * SLOT_SIZE, row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, face, topLeft, bottomRight, pixels::fillRect)
        }
        texture.upload()
        return texture
    }

    fun slotGrid(g: GuiGraphicsExtractor, x: Int, y: Int, columns: Int, rows: Int) {
        if (theme === slotTheme && columns <= SLOT_GRID_COLUMNS && rows <= SLOT_GRID_ROWS) {
            return slotBlit(g, SLOT_TEXTURE, x, y, OPAQUE, columns * SLOT_SIZE, rows * SLOT_SIZE)
        }
        for (row in 0 until rows) for (column in 0 until columns) slot(g, x + column * SLOT_SIZE, y + row * SLOT_SIZE)
    }

    private fun tabOutline(g: GuiGraphicsExtractor, x: Int, top: Int, width: Int, side: Int, color: Int) {
        rect(g, x + 1, top, width - 2, 1, color)
        rect(g, x, top + 1, 1, side, color)
        rect(g, x + width - 1, top + 1, 1, side, color)
    }

    fun hotbar(g: GuiGraphicsExtractor, x: Int, y: Int, cells: Int) {
        val body = 1 + cells * HOTBAR_CELL
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_WIDTH, HOTBAR_HEIGHT, 0, 0, x, y, body, HOTBAR_HEIGHT)
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_WIDTH - 1, 0, x + body, y, 1, HOTBAR_HEIGHT)
    }

    fun hotbarWidth(cells: Int) = cells * HOTBAR_CELL + 2

    fun gameScreen(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, guiPixel: Float) {
        val horizon = y + height / 2
        g.fillGradient(x, y, x + width, horizon, GameScreenColors.SKY_TOP, GameScreenColors.SKY_HORIZON)
        g.fillGradient(x, horizon, x + width, y + height, GameScreenColors.GROUND_TOP, GameScreenColors.GROUND_BOTTOM)
        val middle = x + width / 2
        val reach = maxOf(1, (GAME_CROSSHAIR_REACH * guiPixel).roundToInt())
        rect(g, middle - reach, horizon, reach * 2 + 1, 1, GameScreenColors.CROSSHAIR)
        rect(g, middle, horizon - reach, 1, reach * 2 + 1, GameScreenColors.CROSSHAIR)
        val bar = hotbarWidth(GAME_HOTBAR_CELLS)
        g.pose().pushMatrix()
        g.pose().translate(middle - bar * guiPixel / 2, y + height - HOTBAR_HEIGHT * guiPixel)
        g.pose().scale(guiPixel, guiPixel)
        hotbar(g, 0, 0, GAME_HOTBAR_CELLS)
        g.pose().popMatrix()
    }

    fun plate(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x + 1, y + 1, width - 2, height - 2, t.plate)
        ring(g, x, y, width, height, t.plateEdge)
    }

    fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, fraction: Float, color: Int) {
        well(g, x, y, width, BAR_HEIGHT)
        val filled = ((width - 2) * fraction).roundToInt()
        rect(g, x + 1, y + 1, filled, 4, color)
        stripe(g, x + 1, y + 1, filled, color, ARGB.white(1f), 0.45f)
        stripe(g, x + 1, y + 4, filled, color, ARGB.black(1f), 0.35f)
    }

    fun scrollbar(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int, thumbY: Int, thumbHeight: Int) {
        well(g, x, y, SCROLLBAR_WIDTH, height)
        thumb(g, x + 1, y + 1 + thumbY, SCROLLBAR_WIDTH - 2, thumbHeight, State.IDLE)
    }

    fun thumb(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
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

    fun tabBar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
        val t = theme
        rect(g, x, y, width, TAB_BAR_HEIGHT - 1, t.side)
        rect(g, x, y + TAB_BAR_HEIGHT - 1, width, 1, t.outline)
    }

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

    fun pageTabLabel(width: Int, lead: Boolean, close: Boolean) =
        width - PAGE_TAB_PAD - (if (lead) PAGE_TAB_LEAD + GAP else 0) - (if (close) GAP + PAGE_TAB_LEAD else 0) - PAGE_TAB_PAD

    fun pageTabCloseX(x: Int, width: Int) = x + width - PAGE_TAB_PAD - PAGE_TAB_LEAD

    fun pageTabCloseY(lineY: Int) = lineY - PAGE_TAB_LEAD_UP

    fun tag(g: GuiGraphicsExtractor, x: Int, lineY: Int, text: Component) {
        g.text(font, text, x, lineY - PAGE_TAB_TEXT_UP, -1, true)
    }

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
            litFace(g, x, y, width, 16, t.panel, t.panelLight, t.panelShade, shadeHeight = 14)
            return
        }
        val y = lineY - 15
        tabOutline(g, x, y, width, 14, t.outline)
        litFace(g, x, y, width, 14, if (state == State.HOVER) t.hover else t.tabOff, t.panelShade, t.wellShade)
    }

    private fun litFace(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, face: Int, light: Int, shade: Int, shadeHeight: Int = height - 1) {
        rect(g, x + 1, y + 1, width - 2, height, face)
        rect(g, x + 1, y + 1, width - 3, 1, light)
        rect(g, x + 1, y + 1, 1, height - 1, light)
        rect(g, x + width - 2, y + 2, 1, shadeHeight, shade)
    }

    fun checkbox(g: GuiGraphicsExtractor, x: Int, y: Int, checked: Boolean, state: State) {
        val t = theme
        well(g, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE)
        focusRing(g, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, state)
        if (checked) icon(g, Icon.CHECK, x + 2, y + 2, if (state == State.OFF) t.inkOff else GOOD, state != State.OFF)
    }

    fun switch(g: GuiGraphicsExtractor, x: Int, y: Int, on: Boolean, state: State) {
        val t = theme
        val lit = state == State.HOVER || state == State.DOWN
        if (state == State.OFF) {
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

    fun marker(g: GuiGraphicsExtractor, x: Int, y: Int, state: State) {
        val t = theme
        rect(g, x, y, 3, 3, t.accent)
        focusRing(g, x, y, 3, 3, state)
    }

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

    fun dropdownLabel(width: Int, lead: Int = 0) = width - DROPDOWN_PAD * 2 - Icon.DOWN.width - GAP - leadRoom(lead)

    fun dropdownWidth(label: Int, lead: Int = 0) = label + DROPDOWN_PAD * 2 + Icon.DOWN.width + GAP + leadRoom(lead)

    fun wellRow(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, mix(theme.well, BODY, 0.05f))

    fun dim(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(DIM_WASH, theme.well))

    fun grip(g: GuiGraphicsExtractor, x: Int, y: Int, lit: Boolean) {
        val color = mix(theme.well, BODY, if (lit) 0.8f else 0.4f)
        for (row in 0 until 4) for (column in 0 until 2) rect(g, x + column * 2, y + row * 2, 1, 1, color)
    }

    fun guide(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int) = rect(g, x, y, 1, height, mix(theme.well, BODY, 0.18f))

    fun themeSwatch(g: GuiGraphicsExtractor, x: Int, y: Int, look: Theme) {
        frame(g, x, y, SWATCH_WIDTH, SWATCH_HEIGHT, look.outline, look.panel, look.panelLight, look.panelShade)
        bevel(g, x + 3, y + 3, 9, SWATCH_HEIGHT - 6, look.well, look.wellShade, look.wellLight)
        frame(g, x + 13, y + 3, 6, SWATCH_HEIGHT - 6, look.outline, look.raised, look.raisedLight, look.raisedShade)
    }

    fun tile(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, lit: Boolean, selected: Boolean) {
        val t = theme
        if (lit) rect(g, x + 1, y + 1, width - 2, height - 2, t.hover)
        if (selected || lit) ring(g, x, y, width, height, if (selected) t.accent else t.hoverOutline)
    }

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

    fun flash(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, strength: Float) =
        rect(g, x, y, width, height, ARGB.white(strength.coerceIn(0f, 1f)))

    fun tick(g: GuiGraphicsExtractor, x: Int, y: Int, height: Int) = rect(g, x, y, 1, height, theme.wellLight)

    fun divider(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
        val t = theme
        rect(g, x, y, width, 1, t.panelShade)
        rect(g, x, y + 1, width, 1, t.panelLight)
    }

    fun rule(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, onWell: Boolean = false) {
        val t = theme
        rect(g, x, y, width, 1, if (onWell) mix(t.well, BODY, 0.07f) else mix(t.panel, t.ink, 0.12f))
    }

    fun backdrop(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, theme.panel)

    fun side(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        val t = theme
        rect(g, x, y, width - 1, height, t.side)
        rect(g, x + width - 1, y, 1, height, t.outline)
    }

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

    fun swatch(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) {
        rect(g, x, y, size, size, theme.outline)
        rect(g, x + 1, y + 1, size - 2, size - 2, color)
    }

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

    fun label(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, ink: Ink = Ink.NORMAL) {
        g.text(font, text, x, y, color(ink), ink != Ink.OFF && theme.inkShadow)
    }

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

    fun slotFill(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) = rect(g, x, y, 16, 16, color)

    fun cooldown(g: GuiGraphicsExtractor, x: Int, y: Int, left: Float) {
        if (left <= 0f) return
        val top = y + floor(16 * (1 - left.coerceAtMost(1f))).toInt()
        rect(g, x, top, 16, y + 16 - top, ARGB.white(COOLDOWN_ALPHA))
    }

    fun slotBorder(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        rect(g, x - 1, y - 1, 18, 1, color)
        rect(g, x - 1, y + 16, 18, 1, color)
        rect(g, x - 1, y, 1, 16, color)
        rect(g, x + 16, y, 1, 16, color)
    }

    fun slotOutline(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        rect(g, x - 2, y - 2, 20, 1, color)
        rect(g, x - 2, y + 17, 20, 1, color)
        rect(g, x - 2, y - 1, 1, 18, color)
        rect(g, x + 17, y - 1, 1, 18, color)
    }

    fun slotArt(g: GuiGraphicsExtractor, x: Int, y: Int, atlas: Atlas, cell: Int, row: Int = 0, opacity: Int = 100) = g.blit(
        RenderPipelines.GUI_TEXTURED, atlas.id, x - 1, y - 1, (cell * SlotArt.SIZE).toFloat(), (row * SlotArt.SIZE).toFloat(), SlotArt.SIZE, SlotArt.SIZE,
        atlas.width, atlas.height, ARGB.white((opacity.coerceIn(0, 100) * 255 + 50) / 100),
    )

    fun slotDim(g: GuiGraphicsExtractor, x: Int, y: Int) = rect(g, x, y, 16, 16, ARGB.color(DIM_ALPHA, theme.well))

    fun veil(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(VEIL_ALPHA, theme.panel))

    fun bone(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) = rect(g, x, y, width, height, ARGB.color(BONE_ALPHA, theme.inkMuted))

    fun slotHover(g: GuiGraphicsExtractor, x: Int, y: Int) = rect(g, x, y, 16, 16, ARGB.white(HOVER_STRENGTH))

    fun slotHighlightBack(g: GuiGraphicsExtractor, x: Int, y: Int) = g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - 4, y - 4, 24, 24)

    fun slotHighlightFront(g: GuiGraphicsExtractor, x: Int, y: Int) = g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - 4, y - 4, 24, 24)

    private fun face(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        val t = theme
        when (state) {
            State.IDLE -> frame(g, x, y, width, height, t.outline, t.raised, t.raisedLight, t.raisedShade) // rule:clone a table of each state's colors
            State.HOVER -> frame(g, x, y, width, height, t.hoverOutline, t.hover, t.hoverLight, t.raisedShade) // rule:clone a table of each state's colors
            State.DOWN -> frame(g, x, y, width, height, t.outline, t.down, t.wellShade, t.down) // rule:clone a table of each state's colors
            State.OFF -> frame(g, x, y, width, height, t.outline, t.panel, t.panel, t.panel) // rule:clone a table of each state's colors
        }
    }

    fun focusRing(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, state: State) {
        if (state == State.HOVER || state == State.DOWN) ring(g, x - 1, y - 1, width + 2, height + 2, theme.hoverOutline)
    }

    private fun slotBlit(g: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, alpha: Int, width: Int = SLOT_SIZE, height: Int = SLOT_SIZE) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, width, height, SLOT_GRID_WIDTH, SLOT_GRID_HEIGHT, ARGB.white(alpha))
    }

    private fun stripe(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, color: Int, tint: Int, amount: Float) = rect(g, x, y, width, 1, mix(color, tint, amount))

    private fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, outline: Int, face: Int, light: Int, shade: Int) {
        ring(g, x, y, width, height, outline)
        bevel(g, x + 1, y + 1, width - 2, height - 2, face, light, shade)
    }

    private fun bevel(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, face: Int, topLeft: Int, bottomRight: Int) =
        bevelRects(x, y, width, height, face, topLeft, bottomRight) { rx, ry, rw, rh, color -> rect(g, rx, ry, rw, rh, color) }

    private inline fun bevelRects(
        x: Int, y: Int, width: Int, height: Int, face: Int, topLeft: Int, bottomRight: Int, fill: (Int, Int, Int, Int, Int) -> Unit,
    ) {
        fill(x, y, width, height, face)
        fill(x, y, width - 1, 1, topLeft)
        fill(x, y, 1, height - 1, topLeft)
        fill(x + 1, y + height - 1, width - 1, 1, bottomRight)
        fill(x + width - 1, y + 1, 1, height - 1, bottomRight)
    }

    private fun fade(color: Int, alpha: Int) = if (alpha >= OPAQUE) color else ARGB.color(ARGB.alpha(color) * alpha / OPAQUE, color)

    private fun rect(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (width > 0 && height > 0) g.fill(x, y, x + width, y + height, color)
    }

    fun dark(argb: Int) = (ARGB.red(argb) * 299 + ARGB.green(argb) * 587 + ARGB.blue(argb) * 114) / 1000 < DARK_BELOW

    private fun mix(from: Int, to: Int, amount: Float) = ARGB.color(
        ARGB.alpha(from),
        channel(ARGB.red(from), ARGB.red(to), amount),
        channel(ARGB.green(from), ARGB.green(to), amount),
        channel(ARGB.blue(from), ARGB.blue(to), amount),
    )

    private fun channel(from: Int, to: Int, amount: Float) = (from + (to - from) * amount).roundToInt()

    private const val DIM_ALPHA = 184
    private val COLOR_ATLAS: Identifier = Identifier.fromNamespaceAndPath("dragen", "kit/colors")
    private const val RAMP = 256
    private const val HUE_ROW = 0
    private const val SATURATION_ROW = 1
    private const val OPACITY_ROW = 2
    private const val CHECKER_ROW = 4
    private const val CHECKER = 4
    private const val CHECKER_HEIGHT = 12
    private const val COLOR_ATLAS_HEIGHT = CHECKER_ROW + CHECKER_HEIGHT
    private val CHECKER_LIGHT = ARGB.white(1f)
    private val CHECKER_DARK = ARGB.color(OPAQUE, 200, 200, 200)
    private val OPAQUE_BLACK = ARGB.black(1f)
    private var colorsPainted = false
    private const val COOLDOWN_ALPHA = 0.5f
    const val OPAQUE = 255
    private const val HOVER_STRENGTH = 0.5f
    private const val VEIL_ALPHA = 190
    private const val BONE_ALPHA = 90
    private val SLOT_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("dragen", "kit/slot")
    private val HIGHLIGHT_BACK: Identifier = Identifier.withDefaultNamespace("container/slot_highlight_back")
    private val HIGHLIGHT_FRONT: Identifier = Identifier.withDefaultNamespace("container/slot_highlight_front")

    private val HOTBAR: Identifier = Identifier.withDefaultNamespace("hud/hotbar")
    private const val GAME_HOTBAR_CELLS = 9
    private const val GAME_CROSSHAIR_REACH = 4
    private const val HOTBAR_WIDTH = 182
    const val HOTBAR_HEIGHT = 22
    const val HOTBAR_CELL = 20
    private const val SLOT_GRID_COLUMNS = 9
    private const val SLOT_GRID_ROWS = 6
    private const val SLOT_GRID_WIDTH = SLOT_GRID_COLUMNS * SLOT_SIZE
    private const val SLOT_GRID_HEIGHT = SLOT_GRID_ROWS * SLOT_SIZE
    private var slotTexture: DynamicTexture? = null
    private var slotTheme: Theme? = null


    private const val DARK_BELOW = 90
    private val GOOD = Role.GOOD.argb()
    private val BODY = Role.BODY.argb()
}
