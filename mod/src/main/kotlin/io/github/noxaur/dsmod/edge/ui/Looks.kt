package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.NativeImage
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

object Looks {
    const val HEIGHT = Kit.SLOT_SIZE

    private val RARITIES = listOf(Role.LEGENDARY, Role.EPIC, Role.RARE)
    private val COLUMNS = MarkStyle.entries.size * 2
    private val ATLAS = Atlas(Identifier.fromNamespaceAndPath("dsmod", "kit/looks"), COLUMNS * SlotArt.SIZE, RARITIES.size * 2 * SlotArt.SIZE)
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
        val dark = Kit.dark(Kit.theme.slot)
        val column = style.mark.ordinal * 2 + if (style.sparkles) 1 else 0
        for (i in 0 until look.count) {
            val sx = x + i * Kit.SLOT_SIZE
            Kit.slot(g, sx, y)
            if (painted) Kit.slotArt(g, sx + 1, y + 1, ATLAS, column, (i % RARITIES.size) * 2 + if (dark) 1 else 0, style.opacity)
        }
    }

    // @keep A texture made while a frame is being built breaks the frames after it, so paint at the end of a tick.
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { if (!painted) paint() }
    }

    private fun paint() {
        val image = NativeImage(ATLAS.width, ATLAS.height, true)
        for ((r, role) in RARITIES.withIndex()) {
            for (dark in 0..1) {
                val top = (r * 2 + dark) * SlotArt.SIZE
                for (mark in MarkStyle.entries) {
                    for (sparkles in 0..1) {
                        val art = SlotArt.back(SlotStyle(mark, sparkles = sparkles == 1), role.rgb, dark == 1, variant = r)
                        blit(image, (mark.ordinal * 2 + sparkles) * SlotArt.SIZE, top, SlotArt.SIZE, art)
                    }
                }
            }
        }
        val texture = DynamicTexture({ "ds-mod looks" }, image)
        Minecraft.getInstance().textureManager.register(ATLAS.id, texture)
        texture.upload()
        painted = true
    }

    private fun blit(image: NativeImage, x: Int, y: Int, size: Int, pixels: IntArray) {
        for (j in 0 until size) for (i in 0 until size) image.setPixel(x + i, y + j, pixels[j * size + i])
    }
}
