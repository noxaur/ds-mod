package io.github.noxaur.dragen.core.text

import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun `each span takes the color of its role`() {
        val text = text {
            body("Sold ")
            value(1204)
            body(" scales.")
        }

        assertEquals("Sold 1,204 scales.", text.plain)
        assertEquals(listOf(0xDBE6FB, 0xAADCF8, 0xDBE6FB), text.spans.map { it.rgb })
    }

    @Test
    fun `a number gets thousands separators`() {
        assertEquals("1,234,567", text { value(1_234_567) }.plain)
        assertEquals("-1,000", text { value(-1000) }.plain)
        assertEquals("999", text { value(999) }.plain)
    }

    @Test
    fun `brand runs through the logo gradient one character at a time`() {
        val brand = text { brand("[Dragen]") }

        assertEquals("[Dragen]", brand.plain)
        assertEquals(8, brand.spans.size)
        assertEquals(0xFFBB00, brand.spans.first().rgb)
        assertEquals(0x6C68FF, brand.spans.last().rgb)
    }

    @Test
    fun `brand passes through the middle stop`() {
        assertEquals(listOf(0xFFBB00, 0xFFFFFF, 0x6C68FF), text { brand("abc") }.spans.map { it.rgb })
    }

    @Test
    fun `brand of one character is the first stop`() {
        assertEquals(listOf(0xFFBB00), text { brand("x") }.spans.map { it.rgb })
    }

    @Test
    fun `server text keeps the server's colors spread across the new text`() {
        val white = 0xFFFFFF
        val yellow = 0xFFFF55
        val gold = 0xFFAA00
        val red = 0xFF5555
        val crit = text(
            ServerSpan("✧4,", white), ServerSpan("8", yellow), ServerSpan("7", gold), ServerSpan("6", red), ServerSpan(",", red),
            ServerSpan("4", gold), ServerSpan("4", yellow), ServerSpan("5✧", white),
        )
        val short = text { server("✧4.9M✧", crit) }
        assertEquals("✧4.9M✧", short.plain)
        assertEquals(listOf(white, white, gold, red, yellow, white), short.spans.flatMap { span -> span.text.map { span.rgb } })
    }

    @Test
    fun `server text in one color stays one span, and uncolored text is white`() {
        val gray = 0xAAAAAA
        assertEquals(listOf(Span("98.4k", gray)), text { server("98.4k", text("98,438", gray)) }.spans)
        assertEquals(listOf(Span("1M", 0xFFFFFF)), text { server("1M", text("1,000,000")) }.spans)
        assertEquals(listOf(Span("7", gray)), text { server("7", text(ServerSpan("1", 0xFFFFFF), ServerSpan("2", gray), ServerSpan("3", gray))) }.spans)
    }
}
