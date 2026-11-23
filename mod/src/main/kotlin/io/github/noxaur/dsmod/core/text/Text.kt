package io.github.noxaur.dsmod.core.text

import io.github.noxaur.dsmod.core.game.ServerText

enum class Role(val rgb: Int) {
    BODY(0xDBE6FB),
    VALUE(0xAADCF8),
    MUTED(0x8791A8),
    ACTION(0x63A0F5),
    GOOD(0x55FF55),
    BAD(0xFF5555),
    WARNING(0xFFFF55),

    MATCH(0xFFFF55),

    COMMON(0xFFFFFF),
    UNCOMMON(0x55FF55),
    RARE(0x5555FF),
    EPIC(0xAA00AA),
    LEGENDARY(0xFFAA00),
    MYTHIC(0xFF55FF),
    DIVINE(0x55FFFF),

    // @keep The server names these in red and in green alike, so neither is the Special color. Red is Dungeonbreaker's
    SPECIAL(0xFF5555),
    ;

    fun argb(alpha: Int = 255) = alpha shl 24 or rgb

    companion object {
        val RARITIES = listOf(COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, DIVINE, SPECIAL)

        fun rarity(rgb: Int?): Role? = RARITIES.firstOrNull { it.rgb == rgb }
    }
}

private val brandStops = intArrayOf(0xFFBB00, 0xFFFFFF, 0x6C68FF)

data class Span(val text: String, val rgb: Int)

data class Text(val spans: List<Span>) {
    val plain get() = spans.joinToString("") { it.text }
}

class TextBuilder {
    private val spans = mutableListOf<Span>()

    fun body(text: String) = add(text, Role.BODY)
    fun value(text: String) = add(text, Role.VALUE)
    fun value(number: Long) = add(grouped(number), Role.VALUE)
    fun muted(text: String) = add(text, Role.MUTED)
    fun action(text: String) = add(text, Role.ACTION)
    fun good(text: String) = add(text, Role.GOOD)
    fun bad(text: String) = add(text, Role.BAD)
    fun warning(text: String) = add(text, Role.WARNING)
    fun match(text: String) = add(text, Role.MATCH)

    fun rarity(text: String, rarity: Role) {
        require(rarity in Role.RARITIES) { "$rarity is not a rarity" }
        add(text, rarity)
    }

    fun brand(text: String) {
        val last = (text.length - 1).coerceAtLeast(1)
        text.forEachIndexed { i, char ->
            val at = i.toFloat() / last * (brandStops.size - 1)
            val from = at.toInt().coerceAtMost(brandStops.size - 2)
            spans += Span(char.toString(), mix(brandStops[from], brandStops[from + 1], at - from))
        }
    }

    fun server(text: String, like: ServerText) {
        val colors = like.spans.flatMap { span -> List(span.text.codePointCount(0, span.text.length)) { span.color ?: 0xFFFFFF } }
        val points = text.codePoints().toArray()
        if (colors.isEmpty()) return add(text, Role.COMMON)
        val shade = IntArray(points.size) { i -> colors[if (points.size == 1) colors.size / 2 else Math.round(i * (colors.size - 1f) / (points.size - 1))] }
        var start = 0
        for (i in points.indices) {
            if (i + 1 < points.size && shade[i + 1] == shade[i]) continue
            spans += Span(String(points, start, i + 1 - start), shade[i])
            start = i + 1
        }
    }

    fun build() = Text(spans.toList())

    private fun add(text: String, role: Role) {
        spans += Span(text, role.rgb)
    }
}

fun text(build: TextBuilder.() -> Unit) = TextBuilder().apply(build).build()

fun clock(millis: Long) = (millis / 1000).let { "%d:%02d".format(it / 60, it % 60) }

private fun mix(from: Int, to: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val a = from shr shift and 0xFF
        val b = to shr shift and 0xFF
        return Math.round(a + (b - a) * amount) shl shift
    }
    return channel(16) or channel(8) or channel(0)
}
