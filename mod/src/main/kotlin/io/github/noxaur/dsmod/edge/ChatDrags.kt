package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.ui.hud.chathudeditor.ChatHudEditor
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.mixin.ChatScreenAccessor
import io.github.noxaur.dsmod.edge.mixin.CommandSuggestionsAccessor
import io.github.noxaur.dsmod.edge.mixin.SuggestionsListAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

object ChatDrags {
    private lateinit var mover: HudMover
    private lateinit var readouts: Readouts
    private lateinit var editor: ChatHudEditor
    private lateinit var settings: DsModConfig.Hud

    private var changed = false

    private var tipId: String? = null
    private var tipPlaced = false
    private var tip = emptyList<Component>()

    fun bind(readouts: Readouts, editor: ChatHudEditor) {
        this.readouts = readouts
        this.editor = editor
    }

    @InputApproved(reaches = ["Refusals.allowKeyPress", "Refusals.allowMouseClick", "Refusals.allowMouseDrag", "Refusals.allowMouseRelease", "Refusals.allowMouseScroll"])
    fun register(settings: DsModConfig.Hud, save: () -> Unit) {
        this.settings = settings
        ScreenHooks.onEveryLayout<ChatScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            mover = HudMover(readouts)
            readouts.placeholders = on(client)
            ScreenEvents.remove(screen).register {
                mover.release()
                readouts.placeholders = false
                if (changed) save()
                changed = false
            }
            Refusals.allowMouseClick(screen).register { _, event ->
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
            Refusals.allowMouseDrag(screen).register { _, event, _, _ ->
                if (event.button() != 0 || !mover.drag(event.x(), event.y())) return@register true
                changed = true
                false
            }
            Refusals.allowMouseRelease(screen).register { _, event -> event.button() != 0 || !mover.release() }
            Refusals.allowMouseScroll(screen).register { _, x, y, _, vertical ->
                val block = under(client, screen, x, y) ?: return@register true
                mover.scroll(block, vertical)
                changed = true
                false
            }
            Refusals.allowKeyPress(screen).register { _, event ->
                val held = mover.held?.let(Hud::find)
                when {
                    event.isEscape -> !mover.cancel()
                    held != null && mover.nudge(held, event) -> {
                        changed = true
                        false
                    }
                    else -> true
                }
            }
            // @keep Minecraft draws the frame's tooltip at the end of the screen's own pass, so it is set before that pass.
            ScreenEvents.beforeExtract(screen).register { _, graphics, x, y, _ ->
                if (mover.held != null) return@register
                val block = under(client, screen, x.toDouble(), y.toDouble()) ?: return@register
                if (Hud.itemAt(x.toDouble(), y.toDouble()) != null) return@register
                graphics.setComponentTooltipForNextFrame(client.font, tooltip(block.id), x, y)
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, x, y, _ ->
                if (!on(client)) return@register
                val held = mover.held?.let(Hud::find)
                val hovered = held ?: under(client, screen, x.toDouble(), y.toDouble())
                val standIns = readouts.standIns
                if (standIns.isNotEmpty()) {
                    val placed = Hud.placed()
                    for (i in placed.indices) {
                        val block = placed[i]
                        if (block !== hovered && block.id in standIns) mover.ring(graphics, block, Kit.theme.inkMuted, grabbable = false)
                    }
                }
                if (held != null) mover.ring(graphics, held, Kit.theme.accent, grabbable = true)
                else if (hovered != null) mover.ring(graphics, hovered, Kit.theme.hoverOutline, grabbable = true)
            }
        }
    }

    private fun on(client: Minecraft) = editor.feature.runs && !client.gui.hud.isHidden

    private fun under(client: Minecraft, screen: ChatScreen, x: Double, y: Double): Hud.Placed? {
        if (!on(client)) return null
        val block = Hud.at(x, y) ?: return null
        if (suggests(screen, x.toInt(), y.toInt())) return null
        if (screen.children().any { it.isMouseOver(x, y) }) return null
        if (CopyChats.covers(client, x, y)) return null
        return block
    }

    private fun suggests(screen: ChatScreen, x: Int, y: Int): Boolean {
        val suggestions = (screen as ChatScreenAccessor).commandSuggestions as CommandSuggestionsAccessor
        val list = suggestions.suggestions
        // @keep The list draws a pixel past its rect, above and below, when it has more to scroll to.
        if (list != null) return (list as SuggestionsListAccessor).rect.let { x >= it.x && x < it.x + it.width && y >= it.y - 1 && y <= it.y + it.height }
        val lines = suggestions.commandUsage.size
        if (lines == 0) return false
        // @keep Minecraft's own numbers: each line is 12 tall, the newest one's top 27 above the screen's bottom.
        val left = suggestions.commandUsagePosition - 1
        return x >= left && x < left + suggestions.commandUsageWidth + 2 && y >= screen.height - 27 - 12 * (lines - 1) && y < screen.height - 15
    }

    private fun tooltip(id: String): List<Component> {
        val placed = readouts.isPlaced(id)
        if (id != tipId || placed != tipPlaced) {
            tipId = id
            tipPlaced = placed
            tip = listOfNotNull(
                text { brand(readouts.title(id)) }.toComponent(),
                text { action("Drag to move, scroll to resize.") }.toComponent(),
                text { action("Hold it and press the arrows to nudge it.") }.toComponent(),
                if (placed) text { action("Right-click to reset.") }.toComponent() else null,
            )
        }
        return tip
    }
}
