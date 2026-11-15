package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional

object Chat {
    private val prefix: Component by lazy {
        text { brand("[ds-mod]") }.toComponent().withStyle {
            // Fabric runs a clicked client command locally, so nothing reaches the server.
            it.withClickEvent(ClickEvent.RunCommand("/dsmod"))
                .withHoverEvent(HoverEvent.ShowText(text { action("Click to open settings.") }.toComponent()))
        }.append(" ")
    }

    /** Prints one line to the player's chat, behind the mod's prefix. Call it on the client thread. */
    fun send(text: Text) {
        Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.empty().append(prefix).append(text.toComponent()))
    }

    /** A low-urgency notice from one feature: its name, a separator, then [detail]. */
    fun notice(feature: String, detail: Text) = send(noticeText(feature, detail))

    /** The line [notice] prints, for an alert to print with its title. */
    fun noticeText(feature: String, detail: Text) = Text(text { body(feature); muted(" | ") }.spans + detail.spans)
}

fun Text.toComponent(): MutableComponent =
    spans.fold(Component.empty()) { line, span -> line.append(Component.literal(span.text).withColor(span.rgb)) }

/**
 * The server's own text, in the styles it came with. An item's name and lore are drawn italic by vanilla unless a
 * span says otherwise, so every span states its italic, and the game's text matches the server's.
 */
fun ServerText.toComponent(): MutableComponent = spans.fold(Component.empty()) { line, span ->
    line.append(
        Component.literal(span.text).withStyle {
            it.withColor(span.color?.let(TextColor::fromRgb)).withBold(span.bold).withItalic(span.italic)
                .withUnderlined(span.underlined).withStrikethrough(span.strikethrough).withObfuscated(span.obfuscated)
        },
    )
}

/**
 * This line with each of [ranges] of its plain text restyled by [restyle], which gets the text in the range. The rest
 * keeps its styles, and a piece the server made clickable keeps its own click.
 */
fun Component.restyle(ranges: List<IntRange>, restyle: (String, Style) -> Style): Component {
    if (ranges.isEmpty()) return this
    val plain = string
    val line = Component.empty()
    var at = 0
    visit({ style, piece ->
        var i = 0
        while (i < piece.length) {
            val range = ranges.firstOrNull { at + i in it }
            val end = range?.let { it.last + 1 - at } ?: (ranges.filter { it.first > at + i }.minOfOrNull { it.first - at } ?: piece.length)
            val text = piece.substring(i, end.coerceAtMost(piece.length))
            line.append(Component.literal(text).setStyle(if (range != null && style.clickEvent == null) restyle(plain.substring(range), style) else style))
            i += text.length
        }
        at += piece.length
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return line
}
