package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.ui.Theme
import io.github.noxaur.dragen.edge.ui.Kit
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
        val color = if (role == null) CommonColors.DARK_GRAY else ink(Theme.VANILLA, role)
        val x = screen.containerLeft + screen.titleLabelX + Kit.font.width(screen.title) + GAP
        val y = screen.containerTop + screen.titleLabelY
        g.text(Kit.font, text, x, y, color, false)
    }

    private fun ink(theme: Theme, role: Role) = when (role) {
        Role.BAD -> theme.inkBad
        Role.GOOD -> theme.inkGood
        else -> theme.ink
    }
}
