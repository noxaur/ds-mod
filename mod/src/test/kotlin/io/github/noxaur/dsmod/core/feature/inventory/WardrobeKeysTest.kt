package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WardrobeKeysTest : Harness(alone = WardrobeKeys::class) {
    private val feature = tree.feature<WardrobeKeys>()
    private var id = 1

    private fun open(title: String, buttons: Int = 9): Int {
        val menu = id++
        post(ContainerOpened(menu, text(title), 90))
        for (n in 1..buttons) post(SlotChanged(35 + n, Item("minecraft:pink_dye", 1, text("Slot $n"), lines("Click to equip!"))))
        return menu
    }

    private fun key(n: Int) = feature.index(48 + n)!!

    @Test
    fun `each number key clicks its slot button once the second is up`() {
        val menu = open(WardrobeKeys.TITLE)
        assertTrue(feature.open(menu))
        pass(WardrobeKeys.WAIT_MILLIS)
        assertEquals(36, feature.press(key(1), time))
        assertEquals(44, feature.press(key(9), time))
        assertNull(feature.due(time))
    }

    @Test
    fun `a press inside the second waits for it, and the last press wins`() {
        open(WardrobeKeys.TITLE)
        assertEquals(WardrobeKeys.WAIT_MILLIS, feature.wait(time))
        assertNull(feature.press(key(1), time))
        pass(400)
        assertNull(feature.press(key(3), time))
        assertEquals(setOf(38), feature.feature.slots!!(time).keys)
        assertEquals(600, feature.wait(time))
        pass(599)
        assertNull(feature.due(time))
        pass(1)
        assertEquals(38, feature.due(time))
        assertNull(feature.due(time))
        assertTrue(feature.feature.slots!!(time).isEmpty())
    }

    @Test
    fun `a new menu drops a held press and starts the second again`() {
        open(WardrobeKeys.TITLE)
        feature.press(key(2), time)
        pass(900)
        open(WardrobeKeys.TITLE)
        pass(200)
        assertNull(feature.due(time))
        assertEquals(800, feature.wait(time))
    }

    @Test
    fun `keys do nothing outside the wardrobe, or on a slot with no button`() {
        val guide = open("Wardrobe & Armor Sets", buttons = 0)
        assertNull(feature.index(49))
        assertEquals(0, feature.wait(time))
        assertTrue(!feature.open(guide))
        open(WardrobeKeys.TITLE, buttons = 3)
        pass(WardrobeKeys.WAIT_MILLIS)
        assertNull(feature.press(key(4), time))
    }

    @Test
    fun `a rebound key clicks in place of the old one`() {
        feature.keys[0].value = 82
        open(WardrobeKeys.TITLE)
        assertNull(feature.index(49))
        pass(WardrobeKeys.WAIT_MILLIS)
        assertEquals(36, feature.press(feature.index(82)!!, time))
    }
}
