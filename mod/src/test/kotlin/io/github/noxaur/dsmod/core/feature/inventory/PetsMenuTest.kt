package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PetsMenuTest : Harness(alone = PetsMenu::class) {
    private val pets = tree.feature<PetsMenu>()
    private val feature = pets.keys
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

    private fun shelf() = (10..16).map { it to pet("Pet $it") } + (19..23).map { it to pet("Pet $it") } + (13 to pet("Black Cat", active = true))

    private fun key(n: Int) = feature.index(MenuKeys.KEY_0 + n % 10)!!

    private fun label(key: String) = text { value(key) }

    private fun labels() = readout().slots.mapValues { it.value.label?.plain }

    @Test
    fun `the first ten pets show their key, the eighth on the second row, and the active pet is outlined`() {
        open(slots = shelf().toTypedArray())
        val slots = readout().slots
        assertEquals(mapOf(10 to "1", 11 to "2", 12 to "3", 13 to "4", 14 to "5", 15 to "6", 16 to "7", 19 to "8", 20 to "9", 21 to "0"), labels())
        assertEquals(SlotMark(Role.GOOD, label("4"), outline = true), slots[13])
        assertEquals(SlotMark(Role.MUTED, label("1")), slots[10])
    }

    @Test
    fun `nothing is marked outside the Pets menu`() {
        open("Storage", 13 to pet("Black Cat", active = true))
        assertEquals(emptyMap(), readout().slots)
    }

    @Test
    fun `the active outline can be turned off, and a key bound to no character or none shows no number`() {
        open(slots = shelf().toTypedArray())
        pets.markActive.value = false
        feature.keys[0].value = 290
        feature.keys[1].value = -1
        feature.keys[2].value = 'R'.code
        pass()
        assertEquals(SlotMark(Role.MUTED, label("4")), readout().slots[13])
        assertEquals(null to "R", labels()[10] to labels()[12])
        assertEquals(setOf(12, 13), readout().slots.keys intersect setOf(10, 11, 12, 13))
    }

    @Test
    fun `a key bound to two pets shows only on the one it clicks`() {
        open(slots = shelf().toTypedArray())
        feature.keys[1].value = MenuKeys.KEY_0 + 1
        pass()
        assertEquals("1" to null, labels()[10] to labels()[11])
    }

    @Test
    fun `1 to 9 and 0 click the first ten pets from the top left, skipping the menu's own items`() {
        open(slots = shelf().toTypedArray())
        assertEquals(10, feature.press(key(1), time))
        assertEquals(16, feature.press(key(7), time))
        assertEquals(19, feature.press(key(8), time))
        assertEquals(21, feature.press(key(10), time))
    }

    @Test
    fun `a key past the last pet clicks nothing, and the keys do nothing in another menu`() {
        open(slots = arrayOf(10 to pet("Wolf"), 11 to pet("Black Cat", active = true)))
        assertNull(feature.press(key(3), time))
        open("Wardrobe", 10 to pet("Wolf"))
        assertNull(feature.index(MenuKeys.KEY_0 + 1))
    }

    @Test
    fun `a press the moment the menu opens clicks at once, with no wait`() {
        open(slots = shelf().toTypedArray())
        assertEquals(0, feature.wait(time))
        assertEquals(13, feature.press(key(4), time))
        pass()
        assertEquals(SlotMark(Role.GOOD, label("4"), outline = true), readout().slots[13])
    }
}
