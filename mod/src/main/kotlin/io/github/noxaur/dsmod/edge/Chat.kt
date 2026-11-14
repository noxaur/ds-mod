package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent

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
