package io.github.noxaur.dragen.core.feature.inventory.itemrarity

import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.lines
import io.github.noxaur.dragen.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemRarityTest : Harness(alone = ItemRarity::class) {
    private fun named(name: String, vararg lore: String) =
        Item("minecraft:player_head", 1, text(ServerSpan("[Lvl 55] ", 0xAAAAAA), ServerSpan(name, Role.LEGENDARY.rgb)), lines(*lore))

    @Test
    fun `a pet card is marked in the rarity its footer names, and an item only named like a pet is not`() {
        post(ContainerOpened(1, text("Bestiary"), 90))
        post(SlotChanged(10, named("Black Cat", "LEVEL 55", "", "Click to summon!", "", "EPICCOMBAT PET")))
        post(SlotChanged(11, named("Zealot", "Kills: 1,204")))
        assertEquals(mapOf(10 to SlotMark(Role.EPIC, rarity = true)), readout().slots.filterKeys { it < 54 })
    }
}
