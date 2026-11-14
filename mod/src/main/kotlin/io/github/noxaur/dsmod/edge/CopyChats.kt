package io.github.noxaur.dsmod.edge

import com.google.gson.JsonArray
import com.mojang.serialization.JsonOps
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.FormatCodes
import io.github.noxaur.dsmod.core.text.StyledRun
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.Style
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import org.joml.Matrix3x2f
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Copy Chat. A right press on a message of the open chat selects it, and dragging over others selects every
 * message between. On release a small menu under the mouse asks how to copy them: as text, with format codes,
 * or as JSON. The first choice lies under the mouse, so a right click and a left click copy plain text.
 *
 * Minecraft is asked where it draws each line, through the pass it uses to find a clicked link, so the rows
 * follow the player's chat scale, width, spacing and scroll.
 */
object CopyChats {
    enum class Format(val label: String) { TEXT("Text"), FORMATTED("Formatted"), JSON("JSON") }

    /** The menu's panel at [x], [y]: a header line, then one button for each [Format] in a row. */
    class Menu(val x: Int, val y: Int, val header: String, private val widths: List<Int>) {
        val width = 2 * Kit.INSET + widths.sum() + (widths.size - 1) * Kit.GAP
        val height = HEADER_HEIGHT + Kit.BUTTON_HEIGHT + Kit.INSET
        val buttonY = y + HEADER_HEIGHT

        fun buttonX(format: Format) = x + Kit.INSET + widths.take(format.ordinal).sum() + format.ordinal * Kit.GAP

        fun buttonWidth(format: Format) = widths[format.ordinal]

        fun holds(mouseX: Double, mouseY: Double) = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

        fun at(mouseX: Double, mouseY: Double) = Format.entries.firstOrNull {
            mouseY >= buttonY && mouseY < buttonY + Kit.BUTTON_HEIGHT && mouseX >= buttonX(it) && mouseX < buttonX(it) + buttonWidth(it)
        }
    }

    private const val HEADER_HEIGHT = 18
    private const val FLASH_MS = 700L
    private const val SELECTED = 0.28f
    private const val FLASH = 0.55f

    /** Where a copy goes. A game test swaps it, so a run leaves the user's clipboard alone. */
    var clipboard: (String) -> Unit = { Minecraft.getInstance().keyboardHandler.clipboard = it }

    /** The open menu, for a game test to find its buttons. */
    var menu: Menu? = null
        private set

    private val copiedLabel: Component by lazy { text { good("Copied") }.toComponent() }

    /** The message the right button went down on, while it is held. */
    private var anchor: GuiMessage? = null

    /** The selected messages, oldest first. */
    private var selection = emptyList<GuiMessage>()
    private var copiedAt = -FLASH_MS
    private var copiedX = 0
    private var copiedY = 0

    /** One line of chat as Minecraft lays it out: [top] and [bottom] are in the space [pose] maps to the screen. */
    private class Row(val line: GuiMessage.Line, val pose: Matrix3x2f, val top: Int, val bottom: Int)

    fun register(settings: DsModConfig.Chat) {
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            clear()
            ScreenEvents.remove(screen).register { clear() }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event -> !settings.copyChat || !pressed(client, event.button(), event.x(), event.y()) }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event ->
                if (event.button() != 1 || anchor == null) return@register true
                anchor = null
                open(client, event.x().toInt(), event.y().toInt())
                false
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                if (menu == null || event.key() != GLFW.GLFW_KEY_ESCAPE) return@register true
                clear()
                false
            }
            // Minecraft draws the frame's tooltip at the end of the screen's own pass, so the label is set before it.
            ScreenEvents.beforeExtract(screen).register { _, graphics, _, _, _ ->
                if (flashing()) graphics.setTooltipForNextFrame(client.font, copiedLabel, copiedX, copiedY)
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, _ -> draw(client, graphics, mouseX, mouseY) }
        }
    }

    private fun flashing() = Util.getMillis() - copiedAt < FLASH_MS

    private fun clear() {
        anchor = null
        selection = emptyList()
        menu = null
    }

    /** A mouse button went down. True when the press was Copy Chat's, so the screen must not see it. */
    private fun pressed(client: Minecraft, button: Int, x: Double, y: Double): Boolean {
        menu?.let { open ->
            if (open.holds(x, y)) {
                if (button == 0) open.at(x, y)?.let { copy(client, it, x.toInt(), y.toInt()) }
                return true
            }
            clear()
        }
        if (button != 1) return false
        val hit = under(client, rows(client), x, y) ?: return false
        copiedAt = -FLASH_MS
        anchor = hit
        selection = listOf(hit)
        return true
    }

    private fun open(client: Minecraft, x: Int, y: Int) {
        val widths = Format.entries.map { Kit.font.width(it.label) + 12 }
        val header = if (selection.size == 1) "Copy Message" else "Copy ${selection.size} Messages"
        val size = Menu(0, 0, header, widths)
        // The first button sits under the mouse, unless the screen's edge is in the way.
        menu = Menu(
            (x - Kit.INSET - widths[0] / 2).coerceIn(0, maxOf(0, client.window.guiScaledWidth - size.width)),
            (y - HEADER_HEIGHT - Kit.BUTTON_HEIGHT / 2).coerceIn(0, maxOf(0, client.window.guiScaledHeight - size.height)),
            header,
            widths,
        )
    }

    private fun copy(client: Minecraft, format: Format, x: Int, y: Int) {
        val shown = (client.gui.hud.chat as ChatComponentAccessor).allMessages
        val messages = selection.filter { message -> shown.any { it === message } }.map { it.content() }
        menu = null
        if (messages.isEmpty()) return clear()
        clipboard(
            when (format) {
                Format.TEXT -> messages.joinToString("\n") { it.string }
                Format.FORMATTED -> messages.joinToString("\n") { FormatCodes.write(runs(it)) }
                Format.JSON -> {
                    val ops = (client.level?.registryAccess() ?: RegistryAccess.EMPTY).createSerializationContext(JsonOps.INSTANCE)
                    val json = messages.map { ComponentSerialization.CODEC.encodeStart(ops, it).getOrThrow() }
                    // One message is its own object, and several are a list, so the paste is always valid JSON.
                    (json.singleOrNull() ?: JsonArray().also { list -> json.forEach(list::add) }).toString()
                }
            },
        )
        copiedAt = Util.getMillis()
        copiedX = x
        copiedY = y
        client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f))
    }

    private fun runs(message: Component): List<StyledRun> {
        val runs = ArrayList<StyledRun>()
        message.visit({ style: Style, part: String ->
            runs += StyledRun(part, style.color?.serialize(), style.isBold, style.isItalic, style.isUnderlined, style.isStrikethrough, style.isObfuscated)
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return runs
    }

    private fun draw(client: Minecraft, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (selection.isEmpty()) return
        val rows = rows(client)
        anchor?.let { from ->
            // Read each frame and not on a drag event, so scrolling the chat with the button held extends the selection too.
            val to = under(client, rows, mouseX.toDouble(), mouseY.toDouble())
            val all = (client.gui.hud.chat as ChatComponentAccessor).allMessages
            val a = all.indexOfFirst { it === from }
            val b = all.indexOfFirst { it === to }
            if (a >= 0 && b >= 0) selection = all.subList(minOf(a, b), maxOf(a, b) + 1).reversed()
        }
        val left = 1f - (Util.getMillis() - copiedAt).toFloat() / FLASH_MS
        val strength = if (left > 0f) left * FLASH else SELECTED
        if (left <= 0f && anchor == null && menu == null) return clear()
        val width = width(client)
        for (row in rows) {
            if (selection.none { it === row.line.parent() }) continue
            graphics.pose().pushMatrix()
            graphics.pose().mul(row.pose)
            Kit.flash(graphics, LEFT, row.top, width, row.bottom - row.top, strength)
            graphics.pose().popMatrix()
        }
        val open = menu ?: return
        graphics.nextStratum()
        Kit.panel(graphics, open.x, open.y, open.width, open.height)
        Kit.label(graphics, open.header, open.x + Kit.INSET, open.y + 6, Kit.Ink.MUTED)
        val hovered = open.at(mouseX.toDouble(), mouseY.toDouble())
        for (format in Format.entries) {
            val state = Kit.state(active = true, hovered = format == hovered, pressed = false)
            val buttonWidth = open.buttonWidth(format)
            Kit.button(graphics, open.buttonX(format), open.buttonY, buttonWidth, Kit.BUTTON_HEIGHT, state, format.label, buttonWidth - 12)
        }
    }

    // The numbers below are `ChatComponent`'s own: its backing runs from 4 left of the text to 8 past the chat's width.
    private const val LEFT = -4

    private fun width(client: Minecraft) =
        ceil(ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get()).toInt() + 12

    private fun under(client: Minecraft, rows: List<Row>, x: Double, y: Double): GuiMessage? {
        val width = width(client)
        val local = Vector2f()
        return rows.firstOrNull { row ->
            row.pose.invert(Matrix3x2f()).transformPosition(x.toFloat(), y.toFloat(), local)
            ActiveTextCollector.isPointInRectangle(local.x, local.y, LEFT.toFloat(), row.top.toFloat(), (LEFT + width).toFloat(), row.bottom.toFloat())
        }?.line?.parent()
    }

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
