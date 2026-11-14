package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import org.joml.Matrix3x2f
import org.joml.Vector2f
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Copy Chat. A right click on a line of the open chat copies the whole message it belongs to, and the message
 * flashes with `Copied` beside the mouse. Minecraft is asked where it draws each line, through the pass it
 * uses to find a clicked link, so the rows follow the player's chat scale, width, spacing and scroll.
 */
object CopyChats {
    private const val FLASH_MS = 700L
    private const val FLASH_ALPHA = 0.45f

    /** Where a copied message goes. A game test swaps it, so a run leaves the user's clipboard alone. */
    var clipboard: (String) -> Unit = { Minecraft.getInstance().keyboardHandler.clipboard = it }

    private val copiedLabel: Component by lazy { text { good("Copied") }.toComponent() }
    private var copied: GuiMessage? = null
    private var copiedAt = 0L
    private var copiedX = 0
    private var copiedY = 0

    /** One line of chat as Minecraft lays it out: [top] and [bottom] are in the space [pose] maps to the screen. */
    private class Row(val line: GuiMessage.Line, val pose: Matrix3x2f, val top: Int, val bottom: Int)

    fun register(settings: DsModConfig.Chat) {
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            ScreenMouseEvents.afterMouseClick(screen).register { _, event, consumed ->
                consumed || (event.button() == 1 && settings.copyChat && copy(client, event.x(), event.y()))
            }
            // Minecraft draws the frame's tooltip at the end of the screen's own pass, so the label is set before it.
            ScreenEvents.beforeExtract(screen).register { _, graphics, _, _, _ ->
                if (Util.getMillis() - copiedAt >= FLASH_MS) copied = null
                if (copied != null) graphics.setTooltipForNextFrame(client.font, copiedLabel, copiedX, copiedY)
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, _, _, _ ->
                val message = copied ?: return@register
                val left = 1f - (Util.getMillis() - copiedAt).toFloat() / FLASH_MS
                val width = width(client)
                for (row in rows(client)) {
                    if (row.line.parent() !== message) continue
                    graphics.pose().pushMatrix()
                    graphics.pose().mul(row.pose)
                    Kit.flash(graphics, LEFT, row.top, width, row.bottom - row.top, left * FLASH_ALPHA)
                    graphics.pose().popMatrix()
                }
            }
        }
    }

    private fun copy(client: Minecraft, x: Double, y: Double): Boolean {
        val width = width(client)
        val local = Vector2f()
        val hit = rows(client).firstOrNull { row ->
            row.pose.invert(Matrix3x2f()).transformPosition(x.toFloat(), y.toFloat(), local)
            ActiveTextCollector.isPointInRectangle(local.x, local.y, LEFT.toFloat(), row.top.toFloat(), (LEFT + width).toFloat(), row.bottom.toFloat())
        } ?: return false
        clipboard(hit.line.parent().content().string)
        copied = hit.line.parent()
        copiedAt = Util.getMillis()
        copiedX = x.toInt()
        copiedY = y.toInt()
        client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f))
        return true
    }

    // The numbers below are `ChatComponent`'s own: its backing runs from 4 left of the text to 8 past the chat's width.
    private const val LEFT = -4

    private fun width(client: Minecraft) =
        ceil(ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get()).toInt() + 12

    private fun rows(client: Minecraft): List<Row> {
        val chat = client.gui.hud.chat
        val lines = (chat as ChatComponentAccessor).trimmedMessages
        val spacing = client.options.chatLineSpacing().get()
        val height = (9 * (spacing + 1)).toInt()
        val textToBottom = (8 * (spacing + 1) - 4 * spacing).roundToInt()
        val rows = ArrayList<Row>()
        chat.captureClickableText(object : ActiveTextCollector {
            private var parameters = ActiveTextCollector.Parameters(Matrix3x2f())

            override fun defaultParameters() = parameters

            override fun defaultParameters(newParameters: ActiveTextCollector.Parameters) {
                parameters = newParameters
            }

            override fun accept(alignment: TextAlignment, x: Int, y: Int, parameters: ActiveTextCollector.Parameters, text: FormattedCharSequence) {
                // The queue's and the restriction's lines come through here too, and belong to no message.
                val line = lines.firstOrNull { it.content() === text } ?: return
                rows += Row(line, Matrix3x2f(parameters.pose()), y + textToBottom - height, y + textToBottom)
            }

            override fun acceptScrolling(text: Component, x: Int, left: Int, right: Int, top: Int, bottom: Int, parameters: ActiveTextCollector.Parameters) {}
        }, client.window.guiScaledHeight, client.gui.hud.guiTicks, ChatComponent.DisplayMode.FOREGROUND)
        return rows
    }
}
