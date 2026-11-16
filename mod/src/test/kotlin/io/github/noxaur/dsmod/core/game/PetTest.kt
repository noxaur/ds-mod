package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.text.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PetTest {
    private fun text(vararg spans: Pair<String, Int?>) = ServerText(spans.map { (text, color) -> ServerSpan(text, color) })

    private fun card(vararg lore: String, name: ServerText = text("[Lvl 37] " to 0xAAAAAA, "Wolf" to Role.EPIC.rgb)) =
        PetCard.of(Item("minecraft:player_head", 1, name, lore.map { ServerText(ServerSpan(it)) }))

    @Test
    fun `a pet's rarity is the color of its name, and an unknown color is Common`() {
        assertEquals(Pet("Black Cat", 100, Role.LEGENDARY), Pet.of(text("[Lvl 100] " to 0xAAAAAA, "Black Cat" to Role.LEGENDARY.rgb, " " to null)))
        assertEquals(Pet("Goop", 1, Role.COMMON), Pet.of(text("[Lvl 1] Goop" to 0x123456)))
        assertNull(Pet.of(text("None" to null)))
        assertEquals("Ender Dragon", Pet.nameOf(" [Lvl 100] Ender Dragon "))
    }

    @Test
    fun `only the active pet is a card`() {
        assertNull(card("LEVEL 37", "Click to summon!"))
        assertEquals(Pet("Wolf", 37, Role.EPIC), card("ACTIVE", "Click to deactivate!")?.pet)
    }

    @Test
    fun `the held item is the line under Held Item, and none when the pet holds none`() {
        assertEquals(PetItem("Gold Claws"), card("Held Item", "Gold Claws", "", "LEVEL 37", "Click to deactivate!")?.held)
        assertNull(card("LEVEL 37", "Click to deactivate!")?.held)
    }

    @Test
    fun `progress is max level, an XP pair, or a percent, read between the level and the click hint`() {
        assertEquals(PetProgress.MAX, card("LEVEL 100", "", "MAX LEVEL", "", "ACTIVE", "Click to deactivate!")?.progress)
        assertEquals(PetProgress(0.25f, 250.0, 1000.0), card("LEVEL 37", "Progress: 25%", "250/1,000", "Click to deactivate!")?.progress)
        assertEquals(PetProgress(0.5f), card("LEVEL 37", "Progress to Level 38: 50%", "Click to deactivate!")?.progress)
    }

    @Test
    fun `the resource pack's glyphs around Held Item, the level and max level are read past`() {
        val read = card("Held Item", "Gold Claws", "", "LEVEL 100", "", "MAX LEVEL", "", "ACTIVE", "Click to deactivate!")
        assertEquals(PetItem("Gold Claws"), read?.held)
        assertEquals(PetProgress.MAX, read?.progress)
    }

    @Test
    fun `lines under the level that hold no progress are handed back unread`() {
        val read = card("LEVEL 37", "", "Something new", "ACTIVE", "Click to deactivate!")
        assertNull(read?.progress)
        assertEquals("Something new", read?.unread)
    }
}
