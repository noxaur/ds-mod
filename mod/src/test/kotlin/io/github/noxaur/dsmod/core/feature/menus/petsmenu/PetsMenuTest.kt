package io.github.noxaur.dsmod.core.feature.menus.petsmenu

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals

class PetsMenuTest : Harness(alone = PetsMenu::class) {
    private var id = 1

    private fun pet(name: String, active: Boolean = false) = Item(
        "minecraft:player_head",
        1,
        text(ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, Role.LEGENDARY.rgb)),
        lines("LEVEL 100", "", "MAX LEVEL", "", *(if (active) arrayOf("ACTIVE", "Click to deactivate!") else arrayOf("Click to summon!"))),
    )

    private fun open(title: String = "Pets", vararg slots: Pair<Int, Item>) {
        post(ContainerOpened(id++, text(title), 90))
        post(SlotChanged(4, Item("minecraft:bone", 1, text("Pets"), lines("View and manage your pets."))))
        for ((slot, item) in slots) post(SlotChanged(slot, item))
    }

    @Test
    fun `only the active pet is outlined`() {
        open(slots = arrayOf(10 to pet("Wolf"), 13 to pet("Black Cat", active = true), 14 to pet("Tiger")))
        assertEquals(mapOf(13 to SlotMark(Role.GOOD, outline = true)), readout().slots)
    }

    @Test
    fun `nothing is marked outside the Pets menu`() {
        open("Storage", 13 to pet("Black Cat", active = true))
        assertEquals(emptyMap(), readout().slots)
    }
}
