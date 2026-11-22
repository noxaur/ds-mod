package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PetTest {
    @Test
    fun `a pet's rarity is the color of its name, and an unknown color is Common`() {
        assertEquals(Pet("Black Cat", 100, Role.LEGENDARY), Pet.of(text(ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan("Black Cat", Role.LEGENDARY.rgb), ServerSpan(" "))))
        assertEquals(Pet("Goop", 1, Role.COMMON), Pet.of(text("[Lvl 1] Goop", 0x123456)))
        assertNull(Pet.of(text("None")))
        assertEquals("Ender Dragon", Pet.nameOf(" [Lvl 100] Ender Dragon "))
    }
}
