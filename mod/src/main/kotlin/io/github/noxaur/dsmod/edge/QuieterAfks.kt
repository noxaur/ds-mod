package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.menus.quieterafk.QuieterAfk
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.sounds.SoundSource

object QuieterAfks {
    private lateinit var afk: QuieterAfk

    @Volatile
    private var muted = false

    fun bind(afk: QuieterAfk) {
        this.afk = afk
    }

    @JvmStatic
    fun volume(volume: Float) = if (muted) 0f else volume

    @JvmStatic
    var dimmed = false
        private set

    fun register() {
        // @keep A playing sound's channel takes a new volume only when the game sets one, so the change is pushed to every sound at once.
        Ticks.end { client ->
            val quiet = afk.quiet
            if (quiet == muted) return@end
            muted = quiet
            client.soundManager.refreshCategoryVolume(SoundSource.MASTER)
        }
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, _ ->
            ScreenEvents.beforeExtract(screen).register { _, _, mouseX, mouseY, _ ->
                val quiet = afk.quiet
                dimmed = quiet && !afk.lit(System.currentTimeMillis(), mouseX, mouseY)
                if (!quiet) return@register
                // @keep A widget beside the menu, such as a Menu Tab, would show its tooltip above the blackout and still take clicks.
                val left = screen.containerLeft
                val top = screen.containerTop
                for (widget in Screens.getWidgets(screen)) {
                    val inside = widget.x >= left && widget.y >= top && widget.right <= left + screen.containerWidth && widget.bottom <= top + screen.containerHeight
                    if (!inside) widget.visible = false
                }
            }
        }
    }

    @JvmStatic
    fun draw(screen: AbstractContainerScreen<*>, g: GuiGraphicsExtractor) {
        if (!afk.quiet) return
        val left = screen.containerLeft
        val top = screen.containerTop
        val width = screen.containerWidth
        val height = screen.containerHeight
        g.nextStratum()
        Kit.blackout(g, 0, 0, screen.width, top)
        Kit.blackout(g, 0, top + height, screen.width, screen.height - top - height)
        Kit.blackout(g, 0, top, left, height)
        Kit.blackout(g, left + width, top, screen.width - left - width, height)
        if (dimmed) Kit.blackout(g, left, top, width, height, DIM)
    }

    // @keep 90% black leaves the menu at a tenth of its brightness.
    private const val DIM = 0.9f
}
