package io.github.noxaur.dsmod.edge

import com.google.gson.JsonArray
import com.mojang.serialization.JsonOps
import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.text.FormatCodes
import io.github.noxaur.dsmod.core.text.StyledRun
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Selection
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import java.util.IdentityHashMap
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

object CopyChats {
    private lateinit var copyChat: CopyChat

    fun bind(copyChat: CopyChat) {
        this.copyChat = copyChat
    }

    enum class Format(val label: String) { TEXT("Text"), FORMATTED("Formatted"), JSON("JSON") }

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

    private const val BESIDE = 8
    private const val FLASH_MS = 700L
    private const val SCROLL_MS = 50L
    private const val SELECTED = 0.28f
    private const val FLASH = 0.55f

    var clipboard: (String) -> Unit = { Minecraft.getInstance().keyboardHandler.clipboard = it }

    var menu: Menu? = null
        private set

    private val copiedLabel: Component by lazy { text { good("Copied") }.toComponent() }

    private class Part(val message: GuiMessage, val index: Int, val pieces: List<Pair<Style, String>>, val alone: Boolean) {
        val blank get() = pieces.all { it.second.isBlank() }

        override fun equals(other: Any?) = other is Part && other.message === message && other.index == index

        override fun hashCode() = System.identityHashCode(message) * 31 + index
    }

    private class Layout(val width: Int, val parts: List<Part>, val partOfRow: List<Int>)

    private val layouts = IdentityHashMap<GuiMessage, Layout>()
    private var selection = Selection<Part>()

    private var drag: Selection.Drag<Part>? = null

    private var chosen = Format.TEXT
    private var menuX = 0
    private var menuY = 0
    private var mouseX = 0
    private var mouseY = 0
    private var scrolledAt = 0L
    private var flashed = emptyList<Part>()
    private var copiedAt = -FLASH_MS
    private var copiedX = 0
    private var copiedY = 0

    private class Row(val part: Part, val pose: Matrix3x2f, val top: Int, val bottom: Int)

    @InputApproved(reaches = ["Refusals.allowKeyPress", "Refusals.allowMouseClick", "Refusals.allowMouseRelease"])
    fun register() {
        ScreenHooks.onEveryLayout<ChatScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            dismiss()
            ScreenEvents.remove(screen).register {
                dismiss()
                selection = Selection()
                layouts.clear()
            }
            Refusals.allowMouseClick(screen).register { _, event -> !copyChat.feature.runs || !pressed(client, event) }
            Refusals.allowMouseRelease(screen).register { _, event ->
                if (event.button() != 1 || drag == null) return@register true
                drag = null
                chosen = Format.TEXT
                open(client, event.x().toInt(), event.y().toInt())
                false
            }
            Refusals.allowKeyPress(screen).register { _, event -> !key(client, event) }
            // @keep Minecraft draws the frame's tooltip at the end of the screen's own pass, so the label is set before it.
            ScreenEvents.beforeExtract(screen).register { _, graphics, _, _, _ ->
                if (Util.getMillis() - copiedAt < FLASH_MS) graphics.setTooltipForNextFrame(client.font, copiedLabel, copiedX, copiedY)
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, x, y, _ -> draw(client, graphics, x, y) }
        }
    }

    private fun dismiss() {
        drag = null
        menu = null
        selection = selection.cleared()
    }

    private fun all(client: Minecraft) = (client.gui.hud.chat as ChatComponentAccessor).allMessages.asReversed().flatMap { layout(client, it).parts }

    private fun pressed(client: Minecraft, event: MouseButtonEvent): Boolean {
        menu?.let { open ->
            if (open.holds(event.x(), event.y())) {
                if (event.button() == 0) open.at(event.x(), event.y())?.let { copy(client, it) }
                return true
            }
        }
        val extend = client.hasShiftDown()
        val toggle = Keys.command(client) || client.hasControlDown()
        val hit = if (event.button() == 1) under(client, rows(client), event.x(), event.y()) else null
        if (hit == null || (hit.blank && !extend && !toggle)) {
            dismiss()
            return false
        }
        menu = null
        flashed = emptyList()
        val all = all(client)
        drag = selection.press(all, hit, extend, toggle).also { selection = it.over(all, hit) }
        return true
    }

    private fun key(client: Minecraft, event: KeyEvent): Boolean {
        if (menu == null) return false
        when {
            event.isEscape -> dismiss()
            Keys.command(client) && event.key() == GLFW.GLFW_KEY_C -> copy(client, Format.TEXT)
            event.isConfirmation -> copy(client, chosen)
            event.isLeft -> chosen = Format.entries[maxOf(chosen.ordinal - 1, 0)]
            event.isRight -> chosen = Format.entries[minOf(chosen.ordinal + 1, Format.entries.lastIndex)]
            Keys.command(client) && event.key() == GLFW.GLFW_KEY_A -> {
                selection = selection.all(all(client))
                open(client, menuX, menuY)
            }
            client.hasShiftDown() && (event.isUp || event.isDown) -> {
                selection = selection.step(all(client), if (event.isUp) -1 else 1)
                reveal(client)
                open(client, menuX, menuY)
            }
            else -> return false
        }
        return true
    }

    private fun reveal(client: Minecraft) {
        val focus = selection.focus ?: return
        val all = all(client)
        val chat = client.gui.hud.chat
        repeat(chat.linesPerPage + (chat as ChatComponentAccessor).trimmedMessages.size) {
            val rows = rows(client)
            if (rows.isEmpty() || rows.any { it.part == focus }) return
            // @keep The first row drawn is the top one, which is the oldest on screen.
            chat.scrollChat(if (all.indexOf(focus) < all.indexOf(rows[0].part)) 1 else -1)
        }
    }

    private fun open(client: Minecraft, x: Int, y: Int) {
        val parts = selection.ordered(all(client)).filter { !it.blank }
        if (parts.isEmpty()) return dismiss()
        val noun = if (parts.all { it.alone }) "Message" else "Line"
        val header = if (parts.size == 1) "Copy $noun" else "Copy ${parts.size} ${noun}s"
        val widths = Format.entries.map { Kit.font.width(it.label) + 12 }
        val size = Menu(0, 0, header, widths)
        menuX = x
        menuY = y
        menu = Menu(
            if (x + BESIDE + size.width <= client.window.guiScaledWidth) x + BESIDE else maxOf(0, x - BESIDE - size.width),
            (y - HEADER_HEIGHT - Kit.BUTTON_HEIGHT / 2).coerceIn(0, maxOf(0, client.window.guiScaledHeight - size.height)),
            header,
            widths,
        )
    }

    private fun copy(client: Minecraft, format: Format) {
        val parts = selection.ordered(all(client)).dropWhile { it.blank }.dropLastWhile { it.blank }
        dismiss()
        if (parts.isEmpty()) return
        clipboard(
            when (format) {
                Format.TEXT -> parts.joinToString("\n") { part -> part.pieces.joinToString("") { it.second }.trim() }
                Format.FORMATTED -> parts.joinToString("\n") { FormatCodes.write(runs(trimmed(it.pieces))) }
                Format.JSON -> {
                    val ops = (client.level?.registryAccess() ?: RegistryAccess.EMPTY).createSerializationContext(JsonOps.INSTANCE)
                    val byMessage = ArrayList<MutableList<Part>>()
                    for (part in parts) if (byMessage.lastOrNull()?.last()?.message === part.message) byMessage.last() += part else byMessage += mutableListOf(part)
                    val components = byMessage.flatMap { of ->
                        if (of.size == layout(client, of[0].message).parts.size) listOf(of[0].message.content())
                        else of.filter { !it.blank }.map { part ->
                            part.pieces.fold(Component.empty()) { line, (style, piece) -> line.append(MutableComponent.create(PlainTextContents.create(piece)).withStyle(style)) }
                        }
                    }
                    val json = components.map { ComponentSerialization.CODEC.encodeStart(ops, it).getOrThrow() }
                    (json.singleOrNull() ?: JsonArray().also { list -> json.forEach(list::add) }).toString()
                }
            },
        )
        flashed = parts
        copiedAt = Util.getMillis()
        copiedX = mouseX
        copiedY = mouseY
        client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f))
    }

    private fun trimmed(pieces: List<Pair<Style, String>>): List<Pair<Style, String>> {
        val kept = pieces.dropWhile { it.second.isBlank() }.dropLastWhile { it.second.isBlank() }.toMutableList()
        if (kept.isEmpty()) return kept
        kept[0] = kept[0].first to kept[0].second.trimStart()
        kept[kept.lastIndex] = kept.last().first to kept.last().second.trimEnd()
        return kept
    }

    private fun runs(pieces: List<Pair<Style, String>>) = pieces.map { (style, piece) ->
        StyledRun(piece, style.color?.serialize(), style.isBold, style.isItalic, style.isUnderlined, style.isStrikethrough, style.isObfuscated)
    }

    private fun layout(client: Minecraft, message: GuiMessage): Layout {
        var width = floor(ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get()).toInt()
        message.tag()?.icon()?.let { width -= it.width + 6 }
        layouts[message]?.takeIf { it.width == width }?.let { return it }

        val lines = arrayListOf(ArrayList<Pair<Style, String>>())
        message.content().visit({ style: Style, text: String ->
            text.split('\n').forEachIndexed { i, piece ->
                if (i > 0) lines += ArrayList<Pair<Style, String>>()
                if (piece.isNotEmpty()) lines.last() += style to piece
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        val partOfRow = ArrayList<Int>()
        client.font.splitter.splitLines(message.content(), width, Style.EMPTY) { _, wrapped ->
            partOfRow += if (partOfRow.isEmpty()) 0 else partOfRow.last() + if (wrapped) 0 else 1
        }
        val layout = if (partOfRow.isNotEmpty() && partOfRow.last() == lines.lastIndex) {
            Layout(width, lines.mapIndexed { i, pieces -> Part(message, i, pieces, lines.size == 1) }, partOfRow)
        } else {
            whole(width, message, lines.flatten())
        }
        layouts[message] = layout
        return layout
    }

    private fun whole(width: Int, message: GuiMessage, pieces: List<Pair<Style, String>>) = Layout(width, listOf(Part(message, 0, pieces, true)), emptyList())

    private fun draw(client: Minecraft, graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        mouseX = x
        mouseY = y
        val flash = 1f - (Util.getMillis() - copiedAt).toFloat() / FLASH_MS
        if (flash <= 0f) flashed = emptyList()
        if (drag == null && menu == null && flashed.isEmpty()) return
        var rows = rows(client)
        drag?.let { held ->
            if (rows.isEmpty()) return@let
            val local = rows[0].pose.invert(Matrix3x2f()).transformPosition(x.toFloat(), y.toFloat(), Vector2f())
            val above = local.y < rows.minOf { it.top }
            val below = local.y >= rows.maxOf { it.bottom }
            if ((above || below) && Util.getMillis() - scrolledAt >= SCROLL_MS) {
                scrolledAt = Util.getMillis()
                client.gui.hud.chat.scrollChat(if (above) 1 else -1)
                rows = rows(client)
            }
            val to = rows.minBy { maxOf(it.top - local.y, local.y - it.bottom + 1, 0f) }.part
            selection = held.over(all(client), to)
        }
        val width = width(client)
        for (row in rows) {
            val strength = if (row.part in selection.items) SELECTED else if (row.part in flashed) flash * FLASH else continue
            graphics.pose().pushMatrix()
            graphics.pose().mul(row.pose)
            Kit.flash(graphics, LEFT, row.top, width, row.bottom - row.top, strength)
            graphics.pose().popMatrix()
        }
        val open = menu ?: return
        graphics.nextStratum()
        Kit.panel(graphics, open.x, open.y, open.width, open.height)
        Kit.label(graphics, open.header, open.x + Kit.INSET, open.y + 6, Kit.Ink.MUTED)
        val marked = open.at(x.toDouble(), y.toDouble()) ?: chosen
        for (format in Format.entries) {
            val buttonWidth = open.buttonWidth(format)
            val state = Kit.state(active = true, hovered = format == marked, pressed = false)
            Kit.button(graphics, open.buttonX(format), open.buttonY, buttonWidth, Kit.BUTTON_HEIGHT, state, format.label, buttonWidth - 12)
        }
    }

    private const val BACKING_BEFORE = 4
    private const val BACKING_AFTER = 8
    private const val LEFT = -BACKING_BEFORE

    private fun width(client: Minecraft) =
        ceil(ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get()).toInt() + BACKING_BEFORE + BACKING_AFTER

    private fun under(client: Minecraft, rows: List<Row>, x: Double, y: Double): Part? {
        val width = width(client)
        val inverse = Matrix3x2f()
        val local = Vector2f()
        return rows.firstOrNull { row -> hits(row.pose, x, y, row.top, row.bottom, width, inverse, local) }?.part
    }

    private fun hits(pose: Matrix3x2fc, x: Double, y: Double, top: Int, bottom: Int, width: Int, inverse: Matrix3x2f, local: Vector2f): Boolean {
        pose.invert(inverse).transformPosition(x.toFloat(), y.toFloat(), local)
        return ActiveTextCollector.isPointInRectangle(local.x, local.y, LEFT.toFloat(), top.toFloat(), (LEFT + width).toFloat(), bottom.toFloat())
    }

    private fun rows(client: Minecraft): List<Row> {
        val chat = client.gui.hud.chat
        val lines = (chat as ChatComponentAccessor).trimmedMessages
        // @keep The chat keeps its rows newest first, so a message's last row comes before its first.
        val rowsOf = IdentityHashMap<GuiMessage, Int>()
        for (line in lines) rowsOf.merge(line.parent(), 1, Int::plus)
        val seen = IdentityHashMap<GuiMessage, Int>()
        val partOf = lines.map { line ->
            val message = line.parent()
            val earlier = seen.getOrDefault(message, 0)
            seen[message] = earlier + 1
            val row = rowsOf.getValue(message) - 1 - earlier
            var layout = layout(client, message)
            val shown = rowsOf.getValue(message)
            val full = layout.partOfRow.size
            /* @keep The chat drops the oldest rows once a message no longer fits in its 100. Those missing rows are a prefix of the replay. A count the replay never produced is another mod's layout, and stays one part. */
            if (layout.parts.size > 1 && full != shown) {
                if (full > shown) {
                    layout.parts[layout.partOfRow[full - shown + row]]
                } else {
                    layout = whole(layout.width, message, layout.parts.flatMap { it.pieces })
                    layouts[message] = layout
                    layout.parts[0]
                }
            } else {
                layout.parts[if (layout.parts.size == 1) 0 else layout.partOfRow[row]]
            }
        }
        val rows = ArrayList<Row>()
        visible(client) { line, pose, top, bottom -> rows += Row(partOf[line], Matrix3x2f(pose), top, bottom) }
        return rows
    }

    fun covers(client: Minecraft, x: Double, y: Double): Boolean {
        val width = width(client)
        val inverse = Matrix3x2f()
        val local = Vector2f()
        var hit = false
        visible(client) { _, pose, top, bottom ->
            if (!hit) hit = hits(pose, x, y, top, bottom, width, inverse, local)
        }
        return hit
    }

    private inline fun visible(client: Minecraft, crossinline row: (line: Int, pose: Matrix3x2fc, top: Int, bottom: Int) -> Unit) {
        val chat = client.gui.hud.chat
        val lines = (chat as ChatComponentAccessor).trimmedMessages
        /* @keep Minecraft draws the rows on screen from the top one down to the newest, and then lines that belong to no message. A row is told by that order and not by its text, because every empty line is the same object. */
        val scrolled = chat.chatScrollbarPos
        var next = minOf(lines.size - scrolled, chat.linesPerPage) - 1
        val spacing = client.options.chatLineSpacing().get()
        val height = (9 * (spacing + 1)).toInt()
        val textToBottom = (8 * (spacing + 1) - 4 * spacing).roundToInt()
        chat.captureClickableText(object : ActiveTextCollector {
            private var parameters = ActiveTextCollector.Parameters(Matrix3x2f())

            override fun defaultParameters() = parameters

            override fun defaultParameters(newParameters: ActiveTextCollector.Parameters) {
                parameters = newParameters
            }

            override fun accept(alignment: TextAlignment, x: Int, y: Int, parameters: ActiveTextCollector.Parameters, text: FormattedCharSequence) {
                if (next < 0) return
                row(scrolled + next--, parameters.pose(), y + textToBottom - height, y + textToBottom)
            }

            override fun acceptScrolling(text: Component, x: Int, left: Int, right: Int, top: Int, bottom: Int, parameters: ActiveTextCollector.Parameters) {}
        }, client.window.guiScaledHeight, client.gui.hud.guiTicks, ChatComponent.DisplayMode.FOREGROUND)
    }
}
