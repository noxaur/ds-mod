package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.FloatingText
import net.minecraft.network.chat.Component

/** What a text display shows in place of the server's [text], until its own tick count reaches [untilTick]. */
class FloatingEdit(@JvmField val text: Component, @JvmField val untilTick: Int)

/**
 * The layer that edits the server's floating text on this client. `FloatingTextMixin` hands it a text display's text
 * the moment it arrives, and keeps what comes back on the entity until the server changes the text again or the edit's
 * time runs out, so a feature decides once per text and a frame only reads a field.
 */
object FloatingTexts {
    /** What a hidden text shows, told apart by identity, which `FloatingTextMixin` and `HiddenTextMixin` both read. */
    @JvmField
    val HIDDEN: Component = Component.empty()

    private const val TICK_MILLIS = 50

    /** The edit of [text], which arrived on a display [ticks] ticks old, or null to show it as the server sent it. */
    @JvmStatic
    fun edit(text: Component, ticks: Int): FloatingEdit? {
        val game = DsModClient.game
        if (!game.wants(FloatingText::class.java)) return null
        val plain = text.toText()
        if (plain.plain.isEmpty()) return null
        val shown = game.post(System.currentTimeMillis(), FloatingText(plain, ticks.toLong() * TICK_MILLIS))
        val edited = if (shown) game.replaced?.toComponent() ?: return null else HIDDEN
        val until = game.heldUntil?.let { ((it + TICK_MILLIS - 1) / TICK_MILLIS).toInt() } ?: Int.MAX_VALUE
        return FloatingEdit(edited, until)
    }
}
