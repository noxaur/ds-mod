package io.github.noxaur.dsmod.core.text

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
        val brand = text { brand("[ds-mod]") }

        assertEquals("[ds-mod]", brand.plain)
        assertEquals(8, brand.spans.size)
        assertEquals(0x6064F9, brand.spans.first().rgb)
        assertEquals(0xAADCF8, brand.spans.last().rgb)
    }

    @Test
    fun `brand passes through the middle stop`() {
        assertEquals(listOf(0x6064F9, 0x63A0F5, 0xAADCF8), text { brand("abc") }.spans.map { it.rgb })
    }

    @Test
    fun `brand of one character is the first stop`() {
        assertEquals(listOf(0x6064F9), text { brand("x") }.spans.map { it.rgb })
    }
}
