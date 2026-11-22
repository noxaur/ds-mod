package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

private const val WIDTH = 240
private const val LINE = 10
private const val MARGIN = 4

private const val CHROME = Kit.INSET * 2 + 11 + 4 + 4 + Kit.BUTTON_HEIGHT

class ContributeScreen(private val signingIn: Boolean, private val parent: Screen? = null) : Screen(text { body("Contribute") }.toComponent()) {
    private class Line(val text: String, val ink: Kit.Ink)

    private var queue: List<Request>? = null
    private var failure: String? = null
    private var lines = emptyList<Line>()
    private var x = 0
    private var y = 0
    private var panelHeight = 0

    override fun isPauseScreen() = false

    override fun onClose() = minecraft.gui.setScreen(parent)

    fun offer(requests: List<Request>) {
        queue = requests
        redraw()
    }

    fun fail(message: String) {
        failure = message
        redraw()
    }

    private fun redraw() {
        if (width > 0) rebuildWidgets()
    }

    override fun init() {
        val request = queue?.firstOrNull()
        val inner = WIDTH - Kit.INSET * 2
        val room = ((height - MARGIN * 2 - CHROME) / LINE).coerceAtLeast(0)
        lines = when {
            failure != null -> wrap("Could not load requests. $failure Close and open Contribute to try again.", inner, Kit.Ink.NORMAL)
            queue == null && signingIn -> wrap("Signing in with your Minecraft account...", inner, Kit.Ink.MUTED)
            queue == null -> listOf(Line("Looking for requests...", Kit.Ink.MUTED))
            request == null -> wrap("No request needs you right now. Check again later.", inner, Kit.Ink.NORMAL)
            else -> describe(request, inner, room)
        }.take(room)
        panelHeight = CHROME + lines.size * LINE
        x = (width - WIDTH) / 2
        y = (height - panelHeight) / 2
        val buttons = y + panelHeight - Kit.INSET - Kit.BUTTON_HEIGHT
        if (request != null) {
            val accept = addRenderableWidget(KitButton(x + Kit.INSET, buttons, "Accept", { if (Contribute.accept(request)) minecraft.gui.setScreen(null) }))
            addRenderableWidget(
                KitButton(accept.right + Kit.GAP, buttons, "Skip", {
                    Contribute.skip(request)
                    queue = queue?.drop(1)
                    rebuildWidgets()
                }),
            )
        }
        val close = KitButton(0, buttons, "Close", ::onClose)
        close.x = x + WIDTH - Kit.INSET - close.width
        addRenderableWidget(close)
    }

    private fun describe(request: Request, inner: Int, room: Int): List<Line> {
        val head = wrap(request.brief.name, inner, Kit.Ink.NORMAL)
        val tail = wrap("Up to ${clock(request.timeoutSeconds * 1000L)} to finish.", inner, Kit.Ink.MUTED) +
            wrap("Only your play is recorded, with player names removed.", inner, Kit.Ink.MUTED)
        val steps = request.brief.steps.mapIndexed { i, step ->
            val number = "${i + 1}. "
            val textWidth = inner - font.width(number)
            wrap(step.action, textWidth, Kit.Ink.NORMAL).mapIndexed { l, line ->
                Line((if (l == 0) number else " ".repeat(number.length)) + line.text, line.ink)
            } + step.note?.let { note -> wrap(note, textWidth, Kit.Ink.MUTED).map { Line("   " + it.text, it.ink) } }.orEmpty()
        }
        val forSteps = room - head.size - tail.size
        var fits = steps.size
        if (steps.sumOf { it.size } > forSteps) {
            fits = 0
            var used = 1
            while (fits < steps.size && used + steps[fits].size <= forSteps) used += steps[fits++].size
        }
        val hidden = steps.size - fits
        val more = if (hidden == 0) emptyList() else listOf(Line("and $hidden more ${if (hidden == 1) "step" else "steps"}", Kit.Ink.MUTED))
        return head + steps.take(fits).flatten() + more + tail
    }

    private fun wrap(string: String, maxWidth: Int, ink: Kit.Ink): List<Line> {
        val out = ArrayList<Line>()
        var line = ""
        for (word in string.split(' ')) {
            if (line.isNotEmpty() && font.width("$line $word") > maxWidth) {
                out += Line(line, ink)
                line = ""
            }
            line = if (line.isEmpty()) word else "$line $word"
            while (font.width(line) > maxWidth) {
                val fits = font.plainSubstrByWidth(line, maxWidth)
                out += Line(fits, ink)
                line = line.substring(fits.length)
            }
        }
        return out + Line(line, ink)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, x, y, WIDTH, panelHeight)
        Kit.label(graphics, "Contribute", x + Kit.INSET, y + Kit.INSET, Kit.Ink.MUTED)
        lines.forEachIndexed { i, line -> Kit.label(graphics, line.text, x + Kit.INSET, y + Kit.INSET + 15 + i * LINE, line.ink) }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
