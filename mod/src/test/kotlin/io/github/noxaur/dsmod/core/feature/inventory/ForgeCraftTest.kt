package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForgeCraftTest {
    private val feature = SettingsTree().feature<ForgeCraft>()
    private val game = Game(listOf(feature.feature))
    private var id = 1

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(0, event)
        game.tick(0)
    }

    private fun text(plain: String) = ServerText(ServerSpan(plain))

    private fun item(id: String, name: String, count: Int = 1, vararg lore: String) = Item(id, count, text(name), lore.map(::text))

    private val obsidian = item("minecraft:obsidian", "Refined Obsidian", 1, "RARE", "", "Items Required", "Enchanted Obsidian x16", "", "Duration: 49m 12s")

    private fun open(title: String?, rows: Int, items: Map<Int, Item>, carried: Int = 34) {
        val size = rows * 9 + 36
        post(ContainerOpened(id++, title?.let(::text), size))
        for ((slot, item) in items) post(SlotChanged(slot, item))
        if (carried > 0) post(SlotChanged(size - 1, item("minecraft:obsidian", "Enchanted Obsidian", carried)))
    }

    private fun forge(queued: Int = 0) = open(ForgeCraft.FORGE, 6, mapOf(22 to item("minecraft:hopper", "Forge Queue ($queued/10)")))

    private fun select() = open(ForgeCraft.SELECT, 6, mapOf(11 to item("minecraft:end_stone_bricks", "Refining", 1, "Click to browse!")))

    private fun refining() = open("Refining", 6, mapOf(11 to obsidian))

    private fun confirm(button: String = "minecraft:green_terracotta", carried: Int = 34) =
        open(ForgeCraft.CONFIRM, 4, mapOf(4 to obsidian, 20 to item(button, "Confirm")), carried)

    private fun craftByHand() {
        forge()
        select()
        refining()
        confirm()
        post(SystemMessage(text("Queued Refined Obsidian! It starts automatically when a Forge slot finishes.")))
        forge(1)
    }

    private fun next() = feature.next()?.let { it.slot to it.blocked }

    @Test
    fun `a craft by hand is remembered, and each menu of it is one click`() {
        assertNull(feature.recipe)
        feature.amount = 3
        craftByHand()
        assertEquals("Refined Obsidian", feature.recipe?.name)
        assertEquals(listOf(ForgeCraft.Material("Enchanted Obsidian", 16)), feature.recipe?.materials?.map { it.copy(icon = null) })
        assertEquals(22 to false, next())
        select()
        assertEquals(11 to false, next())
        refining()
        assertEquals(11 to false, next())
        confirm()
        assertEquals(20 to false, next())
        assertEquals(2, feature.canCraft())
    }

    @Test
    fun `missing materials make Confirm a red spot`() {
        feature.amount = 3
        craftByHand()
        confirm(button = "minecraft:gray_terracotta", carried = 4)
        assertEquals(20 to true, next())
        assertEquals(0, feature.canCraft())
    }

    @Test
    fun `a full forge makes Forge Queue a red spot`() {
        feature.amount = 3
        craftByHand()
        forge(10)
        assertEquals(22 to true, next())
        assertEquals(0, feature.canCraft())
    }

    @Test
    fun `the amount reached stops the key, and raising it resumes`() {
        feature.amount = 1
        craftByHand()
        select()
        refining()
        confirm()
        post(SystemMessage(text("Queued Refined Obsidian! It starts automatically when a Forge slot finishes.")))
        forge(2)
        assertTrue(feature.done)
        assertNull(next())
        feature.amount = 2
        assertEquals(22 to false, next())
    }

    @Test
    fun `a new visit to the forge counts from none`() {
        feature.amount = 1
        craftByHand()
        select()
        refining()
        confirm()
        post(SystemMessage(text("Queued Refined Obsidian! It starts automatically when a Forge slot finishes.")))
        assertTrue(feature.done)
        open(null, 0, emptyMap(), carried = 0)
        forge(2)
        assertEquals(0, feature.crafted)
        assertEquals(22 to false, next())
    }

    @Test
    fun `another item's Confirm gets no click`() {
        craftByHand()
        open(ForgeCraft.CONFIRM, 4, mapOf(4 to item("minecraft:end_stone_bricks", "Refined End Stone"), 20 to item("minecraft:green_terracotta", "Confirm")))
        assertNull(next())
    }

    @Test
    fun `the amount stays between 1 and 99`() {
        feature.amount = 0
        assertEquals(1, feature.amount)
        feature.amount = 500
        assertEquals(99, feature.amount)
    }

    @Test
    fun `materials read their counts, and a line with none takes one`() {
        val plate = item("minecraft:netherite_scrap", "Void Plate", 1, "Items Required", "Refined End Stone x2", "Refined Obsidian", "Flawed Amethyst x5", "", "Duration: 49m 12s")
        assertEquals(listOf("Refined End Stone" to 2, "Refined Obsidian" to 1, "Flawed Amethyst" to 5), ForgeCraft.materials(plate))
    }
}
