package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.NativeImage
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB

/**
 * Draws each [Look] a pick shows, [HEIGHT] tall. A slot look is drawn from one atlas that holds every border, fill
 * and floor alone, for three rarities on a dark and a light slot. `SlotArt` paints a style back to front with
 * straight alpha, so blitting its parts in that order gives the same pixels as painting the style whole, and any
 * style costs three blits a slot with one texture painted once.
 */
object Looks {
    const val HEIGHT = Kit.SLOT_SIZE

    /** The rarities a slot look shows, rarest first. */
    private val RARITIES = listOf(Role.LEGENDARY, Role.EPIC, Role.RARE)
    private val COLUMNS = Border.entries.size + Fill.entries.size + Floor.entries.size
    private val GEM_ROW = RARITIES.size * 2
    private val ATLAS = Atlas(Identifier.fromNamespaceAndPath("dsmod", "kit/looks"), COLUMNS * SlotArt.SIZE, GEM_ROW * SlotArt.SIZE + SlotArt.GEM)
    private var painted = false

    fun width(look: Look) = when (look) {
        is Look.Slots -> look.count * Kit.SLOT_SIZE
        is Look.Swatch -> Kit.SWATCH_WIDTH
    }

    fun draw(g: GuiGraphicsExtractor, look: Look, x: Int, y: Int) {
        when (look) {
            is Look.Slots -> slots(g, look, x, y)
            is Look.Swatch -> Kit.themeSwatch(g, x, y + (HEIGHT - Kit.SWATCH_HEIGHT) / 2, look.theme)
        }
    }

    private fun slots(g: GuiGraphicsExtractor, look: Look.Slots, x: Int, y: Int) {
        val style = look.style
        val t = Kit.theme
        // The art for a light slot keeps more of the color, as on a chest's gray slot.
        val dark = (ARGB.red(t.slot) * 299 + ARGB.green(t.slot) * 587 + ARGB.blue(t.slot) * 114) / 1000 < DARK_BELOW
        for (i in 0 until look.count) {
            val sx = x + i * Kit.SLOT_SIZE
            Kit.slot(g, sx, y)
            if (!painted) continue
            val row = (i % RARITIES.size) * 2 + if (dark) 1 else 0
            if (style.fill != Fill.NONE) Kit.slotArt(g, sx + 1, y + 1, ATLAS, Border.entries.size + style.fill.ordinal, row)
            if (style.floor != Floor.NONE) Kit.slotArt(g, sx + 1, y + 1, ATLAS, Border.entries.size + Fill.entries.size + style.floor.ordinal, row)
            if (style.border != Border.NONE) Kit.slotArt(g, sx + 1, y + 1, ATLAS, style.border.ordinal, row)
            if (style.gem) Kit.slotGem(g, sx + 1, y + 1, ATLAS, i % RARITIES.size, GEM_ROW)
        }
    }

    // As with the kit's slot, a texture made while a frame is being built breaks the frames after it, so it is
    // painted at the end of a tick.
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { if (!painted) paint() }
    }

    private fun paint() {
        val image = NativeImage(ATLAS.width, ATLAS.height, true)
        val parts = Border.entries.map { SlotStyle(it, Fill.NONE, Floor.NONE) } + Fill.entries.map { SlotStyle(Border.NONE, it, Floor.NONE) } +
            Floor.entries.map { SlotStyle(Border.NONE, Fill.NONE, it) }
        for ((r, role) in RARITIES.withIndex()) {
            for (dark in 0..1) {
                val top = (r * 2 + dark) * SlotArt.SIZE
                for ((c, part) in parts.withIndex()) {
                    val art = SlotArt.back(part, role.rgb, dark == 1)
                    blit(image, c * SlotArt.SIZE, top, SlotArt.SIZE, art)
                }
            }
            val gem = SlotArt.gem(role.rgb)
            blit(image, r * SlotArt.SIZE, GEM_ROW * SlotArt.SIZE, SlotArt.GEM, gem)
        }
        val texture = DynamicTexture({ "ds-mod looks" }, image)
        Minecraft.getInstance().textureManager.register(ATLAS.id, texture)
        texture.upload()
        painted = true
    }

    private fun blit(image: NativeImage, x: Int, y: Int, size: Int, pixels: IntArray) {
        for (j in 0 until size) for (i in 0 until size) image.setPixel(x + i, y + j, pixels[j * size + i])
    }

    private const val DARK_BELOW = 90
}
