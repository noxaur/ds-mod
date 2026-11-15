package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ChatScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

/**
 * Drag In Chat. With the chat open, a block on the HUD moves, resizes and resets by [HudMover]'s rules, as it does in
 * the HUD editor. Minecraft draws the chat over the HUD, so where a row of chat or the input lies over a block, and
 * anywhere while command suggestions are open, the press, the wheel and the hover are the chat's and it works as it
 * always has.
 * The keyboard stays the input's, except Escape during a drag, which puts the block back and keeps the chat open.
 */
object ChatDrags {
    private lateinit var mover: HudMover
    private lateinit var readouts: Readouts
    private lateinit var settings: DsModConfig.Hud

    /** Whether a block moved since the chat opened, so closing it saves. */
    private var changed = false

    // The tooltip of the block last hovered, kept so a frame over the same block builds nothing.
    private var tipId: String? = null
    private var tipPlaced = false
    private var tip = emptyList<Component>()

    fun register(readouts: Readouts, settings: DsModConfig.Hud, save: () -> Unit) {
        this.readouts = readouts
        this.settings = settings
        mover = HudMover(readouts)
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            ScreenEvents.remove(screen).register {
                mover.release()
                if (changed) save()
                changed = false
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                val block = under(client, screen, event.x(), event.y()) ?: return@register true
                when (event.button()) {
                    0 -> mover.grab(block, event.x(), event.y())
                    1 -> {
                        mover.reset(block)
                        changed = true
                    }
                    else -> return@register true
                }
                false
            }
            ScreenMouseEvents.allowMouseDrag(screen).register { _, event, _, _ ->
                if (event.button() != 0 || !mover.drag(event.x(), event.y())) return@register true
                changed = true
                false
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event -> event.button() != 0 || !mover.release() }
            ScreenMouseEvents.allowMouseScroll(screen).register { _, x, y, _, vertical ->
                val block = under(client, screen, x, y) ?: return@register true
                mover.scroll(block, vertical)
                changed = true
                false
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event -> !(event.isEscape && mover.cancel()) }
            // Minecraft draws the frame's tooltip at the end of the screen's own pass, so it is set before that pass.
            ScreenEvents.beforeExtract(screen).register { _, graphics, x, y, _ ->
                if (mover.held != null) return@register
                val block = under(client, screen, x.toDouble(), y.toDouble()) ?: return@register
                // An item on the block shows its own tooltip, from Hud.
                if (Hud.itemAt(x.toDouble(), y.toDouble()) != null) return@register
                graphics.setComponentTooltipForNextFrame(client.font, tooltip(block.id), x, y)
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, x, y, _ ->
                val held = mover.held?.let(Hud::find)
                if (held != null) return@register mover.ring(graphics, held, Kit.theme.accent, grabbable = true)
                under(client, screen, x.toDouble(), y.toDouble())?.let { mover.ring(graphics, it, Kit.theme.hoverOutline, grabbable = true) }
            }
        }
    }

    /** The block at a point in GUI pixels that the chat leaves to Drag In Chat, or null. */
    private fun under(client: Minecraft, screen: ChatScreen, x: Double, y: Double): Hud.Placed? {
        if (!DsModClient.active || !settings.dragInChat || client.gui.hud.isHidden) return null
        val block = Hud.at(x, y) ?: return null
        if ((screen as ChatScreenAccessor).commandSuggestions.isVisible) return null
        if (screen.children().any { it.isMouseOver(x, y) }) return null
        if (CopyChats.covers(client, x, y)) return null
        return block
    }

    private fun tooltip(id: String): List<Component> {
        val placed = readouts.isPlaced(id)
        if (id != tipId || placed != tipPlaced) {
            tipId = id
            tipPlaced = placed
            tip = listOfNotNull(
                text { brand(readouts.title(id)) }.toComponent(),
                text { action("Drag to move, scroll to resize.") }.toComponent(),
                if (placed) text { action("Right-click to reset.") }.toComponent() else null,
            )
        }
        return tip
    }
}
