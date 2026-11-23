package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer

private fun Style.toSpan(text: String = "") =
    ServerSpan(text, color?.value, isBold, isItalic, isUnderlined, isStrikethrough, isObfuscated, (font as? FontDescription.Resource)?.takeIf { it != FontDescription.DEFAULT }?.id?.toString())

fun Component.toText(): ServerText {
    val spans = ArrayList<ServerSpan>()
    val run = StringBuilder()
    var current = ServerSpan("")
    var last: Style? = null
    fun flush() {
        if (run.isNotEmpty()) spans += current.copy(text = run.toString())
        run.setLength(0)
    }
    StringDecomposer.iterateFormatted(this, Style.EMPTY) { _, style, codePoint ->
        if (style !== last) {
            last = style
            val next = style.toSpan()
            if (next != current) {
                flush()
                current = next
            }
        }
        run.appendCodePoint(codePoint)
        true
    }
    flush()
    return ServerText(spans)
}
