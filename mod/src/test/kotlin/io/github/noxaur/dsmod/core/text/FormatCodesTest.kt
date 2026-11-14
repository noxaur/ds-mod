package io.github.noxaur.dsmod.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatCodesTest {
    @Test
    fun `plain text has no codes`() = assertEquals("Hello there", FormatCodes.write(listOf(StyledRun("Hello "), StyledRun("there"))))

    @Test
    fun `a named color is one character and any other is hex`() =
        assertEquals("&6RARE DROP! &#12ab34Eye", FormatCodes.write(listOf(StyledRun("RARE DROP! ", "gold"), StyledRun("Eye", "#12AB34"))))

    @Test
    fun `formats follow the color and are written again after the next one`() = assertEquals(
        "&a&lGo &c&lStop",
        FormatCodes.write(listOf(StyledRun("Go ", "green", bold = true), StyledRun("Stop", "red", bold = true))),
    )

    @Test
    fun `a format added in the same color is only that format`() = assertEquals(
        "&eOne &ltwo",
        FormatCodes.write(listOf(StyledRun("One ", "yellow"), StyledRun("two", "yellow", bold = true))),
    )

    @Test
    fun `a format or a color that ends takes a reset`() = assertEquals(
        "&e&lLoud &r&equiet&r plain",
        FormatCodes.write(listOf(StyledRun("Loud ", "yellow", bold = true), StyledRun("quiet", "yellow"), StyledRun(" plain"))),
    )

    @Test
    fun `an empty stretch writes nothing`() =
        assertEquals("&bA", FormatCodes.write(listOf(StyledRun("", "red", bold = true), StyledRun("A", "aqua"))))
}
