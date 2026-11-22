package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.PetItem
import io.github.noxaur.dsmod.core.game.PetProgress
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentPetTest {
    private val feature = Feature(DsModConfig(), "pets", Area.ANYWHERE, Store.GENERAL, "Follows the pet in a test.")
    private val pets = CurrentPet(feature, {})
    private val game = Game(listOf(feature))

    private fun card(vararg lore: String, name: ServerText = text(ServerSpan("[Lvl 37] ", 0xAAAAAA), ServerSpan("Wolf", Role.EPIC.rgb))): PetCard? {
        game.post(0, ContainerOpened(1, text(PetCard.MENU), 54))
        game.post(1, SlotChanged(0, Item("minecraft:player_head", 1, name, lore.map { text(it) })))
        return pets.card
    }

    @Test
    fun `a pet's stand names the pet whose head it wears`() {
        val head = Item("minecraft:player_head", 1)
        game.post(0, EntitySeen(3, "minecraft:armor_stand", text("[Lvl 100] Ender Dragon"), head))
        assertEquals(head, pets.head("Ender Dragon"))
    }

    @Test
    fun `only a pet's stand gives its head`() {
        val head = Item("minecraft:player_head", 1)
        game.post(0, EntitySeen(3, "minecraft:zombie", text("[Lvl 100] Ender Dragon"), head))
        game.post(1, EntitySeen(4, "minecraft:armor_stand", text("Click to talk"), head))
        assertNull(pets.head("Ender Dragon"))
        assertNull(pets.head("Click to talk"))
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
