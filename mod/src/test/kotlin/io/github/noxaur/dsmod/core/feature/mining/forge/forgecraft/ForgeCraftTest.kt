package io.github.noxaur.dsmod.core.feature.mining.forge.forgecraft

import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForgeCraftTest : Harness(alone = ForgeCraft::class) {
    private val feature = tree.feature<ForgeCraft>()
    private var id = 1

    private fun item(id: String, name: String, count: Int = 1, vararg lore: String) = Item(id, count, text(name), lines(*lore))

    private val obsidian = item("minecraft:obsidian", "Refined Obsidian", 1, "RARE", "", "Items Required", "Enchanted Obsidian x16", "", "Duration: 49m 12s")

    private fun open(title: String?, rows: Int, items: Map<Int, Item>, carried: Int = 34) {
        val size = rows * 9 + 36
        post(ContainerOpened(id++, title?.let { text(it) }, size))
        for ((slot, item) in items) post(SlotChanged(slot, item))
        if (carried > 0) post(SlotChanged(size - 1, item("minecraft:obsidian", "Enchanted Obsidian", carried)))
    }

    private fun forge(queued: Int = 0, busy: Int = 7, carried: Int = 34) {
        val plate = item("minecraft:netherite_scrap", "Void Plate", 1, "Right-click to view recipes.", "", "EPIC", "", "Time Remaining: 26m 1s")
        val forging = (10..16).associateWith { if (it < 10 + busy) plate else item("minecraft:black_stained_glass_pane", "") }
        open(ForgeCraft.FORGE, 6, forging + (22 to item("minecraft:hopper", "Forge Queue ($queued/10)")), carried)
    }

    private fun select() = open(ForgeCraft.SELECT, 6, mapOf(11 to item("minecraft:end_stone_bricks", "Refining", 1, "Click to browse!")))

    private fun refining() = open("Refining", 6, mapOf(11 to obsidian))

    private fun confirm(button: String = "minecraft:green_terracotta", carried: Int = 34) =
        open(ForgeCraft.CONFIRM, 4, mapOf(4 to obsidian, 20 to item(button, "Confirm")), carried)

    private fun craftByHand() {
        forge()
        select()
        refining()
        confirm()
        post(chat("Queued Refined Obsidian! It starts automatically when a Forge slot finishes."))
        forge(1)
    }

    private fun next() = feature.next()?.let { it.slot to it.blocked }

    @Test
    fun `a craft by hand is remembered, and each menu of it is one click`() {
        assertNull(feature.recipe)
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
    fun `a craft that starts in a free forge slot is remembered too`() {
        forge()
        select()
        refining()
        confirm()
        post(chat("Forging Refined Obsidian! Ready in 49m 12s."))
        assertEquals("Refined Obsidian", feature.recipe?.name)
    }

    @Test
    fun `missing materials make Confirm a red spot`() {
        craftByHand()
        confirm(button = "minecraft:gray_terracotta", carried = 4)
        assertEquals(20 to true, next())
        assertEquals(0, feature.canCraft())
    }

    @Test
    fun `a full forge makes Forge Queue a red spot`() {
        craftByHand()
        forge(10)
        assertTrue(feature.full)
        assertEquals(22 to true, next())
        assertEquals(0, feature.canCraft())
    }

    @Test
    fun `an idle forge takes its seven slots and the queue's ten`() {
        craftByHand()
        forge(busy = 0, carried = 20 * 16)
        assertEquals(17, feature.free)
        assertEquals(17, feature.canCraft())
        forge(queued = 0, busy = 3)
        assertEquals(14, feature.free)
    }

    @Test
    fun `the key keeps going until the forge is full`() {
        craftByHand()
        select()
        refining()
        confirm()
        post(chat("Queued Refined Obsidian! It starts automatically when a Forge slot finishes."))
        forge(2)
        assertEquals("Refined Obsidian", feature.recipe?.name)
        assertEquals(22 to false, next())
    }

    @Test
    fun `another item's Confirm gets no click`() {
        craftByHand()
        open(ForgeCraft.CONFIRM, 4, mapOf(4 to item("minecraft:end_stone_bricks", "Refined End Stone"), 20 to item("minecraft:green_terracotta", "Confirm")))
        assertNull(next())
    }

    @Test
    fun `materials read their counts, and a line with none takes one`() {
        val plate = item("minecraft:netherite_scrap", "Void Plate", 1, "Items Required", "Refined End Stone x2", "Refined Obsidian", "Flawed Amethyst x5", "", "Duration: 49m 12s")
        assertEquals(listOf("Refined End Stone" to 2, "Refined Obsidian" to 1, "Flawed Amethyst" to 5), ForgeCraft.materials(plate))
    }
}
