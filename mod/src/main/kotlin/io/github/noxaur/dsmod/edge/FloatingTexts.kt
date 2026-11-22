package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.FloatingText
import net.minecraft.network.chat.Component

/**
 * The layer that edits the server's floating text on this client. `FloatingTextMixin` hands it a text display's text
 * the moment it arrives, and keeps what comes back on the entity until the server changes the text again, so a
 * feature decides once per text and a frame only reads a field.
 */
object FloatingTexts {
    /** What a hidden text shows, told apart by identity, which `FloatingTextMixin` and `HiddenTextMixin` both read. */
    @JvmField
    val HIDDEN: Component = Component.empty()

    @JvmStatic
    fun hidden(text: Component?) = text === HIDDEN

    /** What to show in place of [text]: [HIDDEN], a replacement, or null to show it as the server sent it. */
    @JvmStatic
    fun edit(text: Component): Component? {
        val game = DsModClient.game
        if (!game.wants(FloatingText::class.java)) return null
        val plain = text.toText()
        if (plain.plain.isEmpty()) return null
        if (!game.post(System.currentTimeMillis(), FloatingText(plain))) return HIDDEN
        return game.replaced?.toComponent()
    }
}
