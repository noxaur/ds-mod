package io.github.noxaur.dragen.core.text

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

    @Test
    fun `a short number takes a suffix and the decimals asked, without zeros at the end`() {
        assertEquals("1.2M", short(1_234_567.0, 1))
        assertEquals("1.23M", short(1_234_567.0, 2))
        assertEquals("1M", short(1_234_567.0, 0))
        assertEquals("4.9M", short(4_876_445.0, 1))
        assertEquals("98.4k", short(98_438.0, 1))
        assertEquals("1M", short(1_000_000.0, 1))
        assertEquals("19.9k", short(19_914.0, 1))
        assertEquals("1.5B", short(1.5e9, 1))
        assertEquals("2T", short(2e12, 1))
        assertEquals("3000T", short(3e15, 1))
    }

    @Test
    fun `a short number under a thousand keeps its digits`() {
        assertEquals("338", short(338.0, 1))
        assertEquals("0", short(0.0, 2))
        assertEquals("12.5", short(12.5, 1))
    }

    @Test
    fun `a short number rounds before it picks the suffix`() {
        assertEquals("1M", short(999_960.0, 1))
        assertEquals("999.9k", short(999_940.0, 1))
        assertEquals("1k", short(999.96, 1))
        assertEquals("-1.2M", short(-1_234_567.0, 1))
    }

    @Test
    fun `a fixed number keeps its places, a whole one too`() {
        assertEquals("1.0", fixed(1.0, 1))
        assertEquals("19.9", fixed(19.94, 1))
        assertEquals("1234.50", fixed(1234.5, 2))
        assertEquals("3", fixed(2.6, 0))
    }

    @Test
    fun `a number the server wrote reads with or without its commas, and other text reads as none`() {
        assertEquals(1_234_567, count("1,234,567"))
        assertEquals(8, count("8"))
        assertEquals(null, count(""))
        assertEquals(null, count("1.5"))
        assertEquals(1234.5, number("1,234.5"))
        assertEquals(40.0, number("40"))
        assertEquals(null, number("x"))
    }

    @Test
    fun `a rate is per hour`() {
        assertEquals(2000.0, perHour(1000.0, 30 * 60_000L))
        assertEquals(6.0, perHour(1.0, 10 * 60_000L))
    }

    @Test
    fun `the seconds left round up and stop at zero`() {
        assertEquals(30L, secondsLeft(30_000))
        assertEquals(30L, secondsLeft(29_001))
        assertEquals(1L, secondsLeft(1))
        assertEquals(0L, secondsLeft(0))
        assertEquals(0L, secondsLeft(-5_000))
    }
}
