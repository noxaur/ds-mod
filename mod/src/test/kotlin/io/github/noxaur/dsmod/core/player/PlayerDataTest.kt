package io.github.noxaur.dsmod.core.player

import io.github.noxaur.dsmod.harness.encode
import io.github.noxaur.dsmod.core.feature.general.ItemOverview
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SlotClicked
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.readCapture
import io.github.noxaur.dsmod.harness.text
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerDataTest {
    private class Session : Harness(alone = ItemOverview::class) {
        val player get() = game.player
        val profiles = arrayListOf<String?>(null)
        val left = HashMap<String?, Map<String, Seen>>()

        init {
            var before = player.containers
            for (captured in readCapture(Path.of("src/test/resources/features/item-overview/profile-switch.jsonl"))) {
                post(captured.event)
                if (profiles.last() != player.profile) {
                    left[profiles.last()] = before
                    profiles += player.profile
                }
                before = player.containers
            }
        }
    }

    @Test
    fun `the profile is named by the menu, follows each switch and stays when the server refuses one`() {
        assertEquals(listOf(null, "Old", "Young", "Old"), Session().profiles)
    }

    @Test
    fun `each profile keeps the containers the player saw on it`() {
        val session = Session()
        val old = session.left.getValue("Old")
        assertEquals(setOf("inventory", "ender_chest", "storage:0"), old.keys)
        assertEquals(setOf("inventory"), session.left.getValue("Young").keys, "Young shows none of Old's menus")
        assertEquals(old.getValue("ender_chest").items, session.player.containers.getValue("ender_chest").items)
        assertEquals(old.getValue("storage:0").items, session.player.containers.getValue("storage:0").items)
        assertNotEquals(old.getValue("inventory").items, session.left.getValue("Young").getValue("inventory").items)
        assertEquals(41, session.player.containers.getValue("inventory").items.size, "worn, carried and offhand")
    }

    @Test
    fun `the item overview lists only the profile the player is on`() {
        val session = Session()
        assertEquals(session.player.containers.keys.toList(), session.tree.feature<ItemOverview>().copies.map { it.id })
    }

    private fun profile(name: String, state: String) = Item("minecraft:grass_block", 1, text(name), lines(state))

    private val list = ContainerOpened(1, text("Profile Management"), 72)
    private val manage = ContainerOpened(2, text("Profile Management"), 72)
    private val closed = ContainerOpened(0, null, 46, kept = true)
    private val enderChest = ContainerOpened(3, text("Ender Chest"), 72)
    private val diamonds = SlotChanged(9, Item("minecraft:diamond", 3, text("Diamond")))

    private fun Harness.begin(other: String, from: String) {
        post(list, SlotChanged(10, profile(from, "Selected")), SlotChanged(11, profile(other, "In use")), SlotClicked(11))
        post(manage, SlotChanged(11, Item("minecraft:grass_block", 1, text("Switch to Profile"))), SlotClicked(11))
    }

    private fun Harness.switchTo(other: String, from: String, vararg before: GameEvent) {
        begin(other, from)
        post(*before, closed)
    }

    @Test
    fun `a switch the player begun asks for the chat line that refuses it, whichever features are on`() {
        val session = object : Harness(alone = ItemOverview::class) {}
        assertFalse(session.game.wants(SystemMessage::class.java))
        session.begin("Young", from = "Old")
        assertTrue(session.game.wants(SystemMessage::class.java))
        session.post(chat("You can switch profiles in 12s."), closed)
        assertEquals("Old", session.game.player.profile)
        assertFalse(session.game.wants(SystemMessage::class.java))
    }

    @Test
    fun `a feature's profiled state follows the switch and comes back`() {
        val session = object : Harness(alone = SlayerTracker::class) {}
        val tracker = session.tree.feature<SlayerTracker>().feature
        session.post(list, SlotChanged(10, profile("Old", "Selected")), closed)
        session.post(chat("☠ SLAYER BOSS SLAIN!"))
        val onOld = tracker.state!!.encode()

        session.switchTo("Young", from = "Old")
        assertEquals("Young", session.game.player.profile)
        assertEquals("""{"slain":0}""", tracker.state!!.encode())

        session.switchTo("Old", from = "Young")
        assertEquals(onOld, tracker.state!!.encode())
    }

    @Test
    fun `what the mod kept before it knew the profile goes to the first name it learns`() {
        val session = object : Harness(alone = ItemOverview::class) {}
        session.post(enderChest, diamonds)
        assertNull(session.game.player.profile)
        session.post(list, SlotChanged(10, profile("Old", "Selected")), closed)
        assertEquals("Old", session.game.player.profile)
        assertEquals(3, session.game.player.containers.getValue("ender_chest").items[0]?.count)
    }

    @Test
    fun `a menu closed before its slots arrive keeps the copy the player saw last`() {
        val session = object : Harness(alone = ItemOverview::class) {}
        session.post(enderChest, diamonds, closed, enderChest.copy(id = 4), closed)
        assertEquals(3, session.game.player.containers.getValue("ender_chest").items[0]?.count)
    }

    @Test
    fun `leaving the server forgets a switch the player had begun`() {
        val session = object : Harness(alone = ItemOverview::class) {}
        session.switchTo("Young", from = "Old", LeftServer)
        assertEquals("Old", session.game.player.profile)
    }
}
