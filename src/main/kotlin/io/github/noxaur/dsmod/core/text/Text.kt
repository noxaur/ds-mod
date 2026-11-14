package io.github.noxaur.dsmod.core.text

import java.util.Locale

/** What a piece of text means to the player. A feature picks the role, and the role owns the color. */
enum class Role(val rgb: Int) {
    BODY(0xDBE6FB),
    VALUE(0xAADCF8),
    MUTED(0x8791A8),
    ACTION(0x63A0F5),
    GOOD(0x55FF55),
    BAD(0xFF5555),
    WARNING(0xFFFF55),
    ;

    /** The color with an alpha from 0 to 255, for shapes that sit over the game. */
    fun argb(alpha: Int = 255) = alpha shl 24 or rgb
}

/** The blue of the logo, left to right. */
private val brandStops = intArrayOf(0x6064F9, 0x63A0F5, 0xAADCF8)

class Span(val text: String, val rgb: Int)

class Text(val spans: List<Span>) {
    val plain get() = spans.joinToString("") { it.text }
}

class TextBuilder {
    private val spans = mutableListOf<Span>()

    fun body(text: String) = add(text, Role.BODY)
    fun value(text: String) = add(text, Role.VALUE)
    fun value(number: Long) = add(String.format(Locale.US, "%,d", number), Role.VALUE)
    fun muted(text: String) = add(text, Role.MUTED)
    fun action(text: String) = add(text, Role.ACTION)
    fun good(text: String) = add(text, Role.GOOD)
    fun bad(text: String) = add(text, Role.BAD)
    fun warning(text: String) = add(text, Role.WARNING)

    /** The mod's own name and feature headers, in the logo gradient. */
    fun brand(text: String) {
        val last = (text.length - 1).coerceAtLeast(1)
        text.forEachIndexed { i, char ->
            val at = i.toFloat() / last * (brandStops.size - 1)
            val from = at.toInt().coerceAtMost(brandStops.size - 2)
            spans += Span(char.toString(), mix(brandStops[from], brandStops[from + 1], at - from))
        }
    }

    fun build() = Text(spans.toList())

    private fun add(text: String, role: Role) {
        spans += Span(text, role.rgb)
    }
}

fun text(build: TextBuilder.() -> Unit) = TextBuilder().apply(build).build()

private fun mix(from: Int, to: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val a = from shr shift and 0xFF
        val b = to shr shift and 0xFF
        return Math.round(a + (b - a) * amount) shl shift
    }
    return channel(16) or channel(8) or channel(0)
}
