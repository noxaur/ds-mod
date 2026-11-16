package io.github.noxaur.dsmod.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NumbersTest {
    @Test
    fun `a number is grouped by thousands whatever the locale`() {
        assertEquals("0", grouped(0))
        assertEquals("999", grouped(999))
        assertEquals("1,000", grouped(1000))
        assertEquals("-1,234,567", grouped(-1_234_567))
        assertEquals("9,223,372,036,854,775,807", grouped(Long.MAX_VALUE))
    }

    @Test
    fun `a whole double reads as a whole number`() {
        assertEquals("0", amount(0.0))
        assertEquals("12,000", amount(12000.0))
        assertEquals("-3", amount(-3.0))
        assertEquals("1,000,000,000,000", amount(1e12))
    }

    @Test
    fun `a double with a fraction reads to the places asked, one by default`() {
        assertEquals("2.5", amount(2.5))
        assertEquals("1,234.6", amount(1234.56))
        assertEquals("-0.3", amount(-0.25))
        assertEquals("2.0", amount(2.04))
        assertEquals("1,234.57", amount(1234.567, 2))
    }

    @Test
    fun `trimming drops the zeros at the end of the places`() {
        assertEquals("1.5", amount(1.5, 2, trim = true))
        assertEquals("1.25", amount(1.25, 2, trim = true))
        assertEquals("1", amount(1.0, 2, trim = true))
        assertEquals("1", amount(1.004, 2, trim = true))
        assertEquals("0.05", amount(0.05, 2, trim = true))
        assertEquals("0.5", amount(0.5f.toDouble(), 2, trim = true))
    }
}
