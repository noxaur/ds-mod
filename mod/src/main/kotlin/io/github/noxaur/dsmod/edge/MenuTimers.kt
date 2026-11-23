package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.util.CommonColors

object MenuTimers {
    private const val GAP = 4

    fun seconds(millis: Long): String {
        val tenths = (millis + 99) / 100
        return "${tenths / 10}.${tenths % 10}s"
    }

    fun draw(g: GuiGraphicsExtractor, screen: ContainerScreen, text: String, role: Role? = null) {
        val glass = MenuSkin.glass(screen)
        val skinned = MenuSkin.skins(screen)
        val color = when {
            role == null -> if (skinned) MenuSkin.ink(glass) else CommonColors.DARK_GRAY
            glass >= 0 -> role.argb()
            else -> ink(if (skinned) Kit.theme else Theme.VANILLA, role)
        }
        val x = screen.containerLeft + screen.titleLabelX + Kit.font.width(screen.title) + GAP
        val y = screen.containerTop + screen.titleLabelY
        g.text(Kit.font, text, x, y, color, skinned && MenuSkin.inkShadow(glass))
    }

    private fun ink(theme: Theme, role: Role) = when (role) {
        Role.BAD -> theme.inkBad
        Role.GOOD -> theme.inkGood
        else -> theme.ink
    }
}
