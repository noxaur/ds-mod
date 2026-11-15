package io.github.noxaur.dsmod.core.container

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MenuButtonTest {
    @Test
    fun `the server's navigation items are buttons`() {
        assertEquals(MenuButton.BACK, MenuButton.of("minecraft:arrow", "Go Back"))
        assertEquals(MenuButton.BACK, MenuButton.of("minecraft:arrow", "Return"))
        assertEquals(MenuButton.CLOSE, MenuButton.of("minecraft:barrier", "Close"))
        assertEquals(MenuButton.NEXT, MenuButton.of("minecraft:arrow", "Next page"))
        assertEquals(MenuButton.NEXT, MenuButton.of("minecraft:arrow", "Next Page"))
        assertEquals(MenuButton.PREVIOUS, MenuButton.of("minecraft:arrow", "Previous page"))
        assertEquals(MenuButton.PREVIOUS, MenuButton.of("minecraft:arrow", "Previous Page"))
    }

    @Test
    fun `items that share an id or a name are not`() {
        assertNull(MenuButton.of("minecraft:arrow", "Flint Arrow"))
        assertNull(MenuButton.of("minecraft:arrow", "Levels 26 - 50"))
        assertNull(MenuButton.of("minecraft:barrier", "No matching recipe"))
        assertNull(MenuButton.of("minecraft:barrier", null))
        assertNull(MenuButton.of("minecraft:oak_sign", "Close"))
    }
}
