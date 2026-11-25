package io.github.noxaur.dragen.core.player

import io.github.noxaur.dragen.harness.encode
import io.github.noxaur.dragen.core.feature.inventory.items.itemoverview.ItemOverview
import io.github.noxaur.dragen.core.feature.savedJson
import io.github.noxaur.dragen.core.feature.combat.slayers.slayertracker.SlayerTracker
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.GameEvent
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.game.SlotClicked
import io.github.noxaur.dragen.core.game.SystemMessage
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.profile.Profile
import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.chat
import io.github.noxaur.dragen.harness.lines
import io.github.noxaur.dragen.harness.readCapture
import io.github.noxaur.dragen.harness.text
import java.nio.file.Files
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

    private val superior = Profile.parse(Files.readString(Path.of("src/test/resources/profiles/profile.json")))
    private val arcane = superior.copy(profileId = ARCANE, profileName = text("Arcane"), profiles = superior.profiles.map { it.copy(selected = it.id == ARCANE) })

    private class Asking : Harness(alone = ItemOverview::class) {
        val asked = arrayListOf<String?>().also { asked -> game.player.ask = { asked += it } }

        val here: ProfileData
            get() {
                game.player.unwritten()
                val players = savedJson.decodeFromString(Players.serializer(), game.player.takeChanges()!!())
                return players.profiles[players.profile] ?: ProfileData()
            }
    }

    @Test
    fun `a profile with no answer asks the backend once, and the answer names the profile and stays on it`() {
        val session = Asking()
        val asked = session.asked
        session.post(diamonds, diamonds)
        assertEquals(listOf<String?>(null), asked, "the selected profile, as the mod knows no name yet")

        session.game.player.heard(superior)
        session.post(diamonds)
        assertEquals("Superior", session.game.player.profile)
        assertEquals(superior.hotd, session.here.answer?.hotd)
        assertEquals(listOf<String?>(null), asked)
    }

    @Test
    fun `the menu naming the profile while the first ask is out asks nothing more`() {
        val session = Asking()
        session.post(diamonds, list, SlotChanged(10, profile("Old", "Selected")), closed)
        assertEquals("Old", session.game.player.profile)
        assertEquals(listOf<String?>(null), session.asked)
    }

    @Test
    fun `an answer that arrives after the player left still lets the next join ask`() {
        val session = Asking()
        session.post(diamonds, LeftServer)
        session.game.player.heard(superior.copy(stale = true))
        session.post(diamonds)
        assertEquals(listOf<String?>(null, SUPERIOR), session.asked)
    }

    @Test
    fun `a switch to a profile with no answer asks for it by the id an earlier answer listed`() {
        val session = Asking()
        val asked = session.asked
        session.game.player.heard(superior)
        session.switchTo("Arcane", from = "Superior")
        assertEquals(listOf<String?>(ARCANE), asked)
        assertNull(session.here.answer)

        session.switchTo("Superior", from = "Arcane")
        session.switchTo("Arcane", from = "Superior")
        assertEquals(listOf<String?>(ARCANE, ARCANE), asked, "again, as the backend never answered")

        session.game.player.heard(arcane)
        session.switchTo("Superior", from = "Arcane")
        assertEquals(superior, session.here.answer)
        assertEquals(listOf<String?>(ARCANE, ARCANE), asked)
    }

    @Test
    fun `the backend naming the profile the player left keeps its answer there`() {
        val session = Asking()
        session.post(list, SlotChanged(10, profile("Arcane", "Selected")), closed)
        session.game.player.heard(superior)
        assertEquals("Arcane", session.game.player.profile)
        assertNull(session.here.answer)
        session.switchTo("Superior", from = "Arcane")
        assertEquals(superior, session.here.answer)
    }

    @Test
    fun `a stale answer never replaces a fresh one, and is asked for again when the player joins`() {
        val session = Asking()
        val asked = session.asked
        session.game.player.heard(superior.copy(stale = true))
        session.post(diamonds, LeftServer, diamonds)
        assertEquals(listOf<String?>(SUPERIOR, SUPERIOR), asked, "once on each join, by the id it lists")

        session.game.player.heard(superior)
        session.game.player.heard(superior.copy(stale = true, savedAt = "later"))
        assertEquals(superior, session.here.answer)
    }

    private fun tab(vararg stats: String) = TabList(listOf(text(" Coins: 1"), text(""), text("Stats:")) + stats.map { text(it) } + listOf(text(""), text("Info")))

    @Test
    fun `each profile keeps the stats its tab list showed, and a stat taken off the tab list keeps its last reading`() {
        val session = Asking()
        session.post(list, SlotChanged(10, profile("Old", "Selected")), closed)
        session.post(tab(" ✯ Magic Find 58", " ☣ Crit Chance 98%", " ⸕ Mining Speed 1,250"))
        session.post(tab(" ✯ Magic Find 61"))
        val stats = session.here.stats.mapValues { it.value.value }
        assertEquals(mapOf("Magic Find" to 61.0, "Crit Chance" to 98.0, "Mining Speed" to 1250.0), stats)

        session.switchTo("Young", from = "Old")
        assertEquals(emptyMap(), session.here.stats)
        session.switchTo("Old", from = "Young")
        assertEquals(stats, session.here.stats.mapValues { it.value.value })
    }

    private companion object {
        const val ARCANE = "aaaaaaaa-0000-4000-8000-000000000001"
        const val SUPERIOR = "bbbbbbbb-0000-4000-8000-000000000002"
    }

    private fun heart() = Asking().apply {
        for (captured in readCapture(Path.of("src/test/resources/features/hotd-button/heart-profiles.jsonl"))) post(captured.event)
    }

    private fun Heart.lore(name: String, layout: Int? = null) = (layout?.let { layouts.getValue(it) } ?: items).getValue(name).item.cleanLore

    @Test
    fun `the Heart keeps every tier the player scrolled to, and each Heart profile's tree as it was applied`() {
        val heart = heart().here.heart
        assertEquals((1..10).map { "Tier $it" }.toSet(), heart.items.keys.filter { it.startsWith("Tier ") }.toSet())
        assertEquals(7, heart.items.getValue("Speedy Mineman").tier)
        assertEquals("Level 31/50", heart.lore("Speedy Mineman").first())
        assertEquals("Token of the Dragon: 3 (17 spent)", heart.lore("Heart of the Dragon")[3])
        assertTrue("Click to unlock!" in heart.lore("Speedy Mineman", layout = 2), "Heart profile 2 is empty")
        assertEquals(1, heart.layout)
        assertEquals(heart.items, heart.layouts[1])
        assertFalse("Scroll Up" in heart.items || "Scroll Down" in heart.items)
    }

    @Test
    fun `applying a Heart profile the mod saw brings its tree back`() {
        val session = heart()
        val two = session.here.heart.layouts.getValue(2)
        session.post(
            ContainerOpened(9, text("Apply Dragon Profile #2?"), 63),
            SlotChanged(11, Item("minecraft:green_terracotta", 1, text("Confirm"))),
            SlotClicked(11),
            chat("Applied Heart of the Dragon Profile #2."),
        )
        assertEquals(two, session.here.heart.items)
        assertEquals(2, session.here.heart.layout)
    }

    @Test
    fun `a click on a perk stops keeping the tree as the Heart profile's`() {
        val session = heart()
        val one = session.here.heart.layouts.getValue(1)
        val perk = one.getValue("Speedy Mineman").item
        val upgraded = perk.copy(lore = lines("Level 32/50", "Click to upgrade!"))
        val pane = SlotChanged(27, one.getValue("Tier 7").item)
        session.post(ContainerOpened(9, text("Heart of the Dragon"), 90), pane, SlotChanged(29, perk), SlotClicked(29), SlotChanged(29, upgraded))
        assertNull(session.here.heart.layout)
        assertEquals(one, session.here.heart.layouts[1])
        assertEquals("Level 32/50", session.here.heart.lore("Speedy Mineman").first())
    }

    @Test
    fun `a click on the player's own inventory keeps the tree as the Heart profile's`() {
        val session = heart()
        session.post(ContainerOpened(9, text("Heart of the Dragon"), 90), SlotChanged(54, Item("minecraft:diamond_pickaxe", 1, text("Pickaxe"), lines("Mining Speed: +60"))), SlotClicked(54))
        assertEquals(1, session.here.heart.layout)
    }

    @Test
    fun `the Dragon Sim Menu gives every stat, with or without an icon`() {
        val stats = heart().here.stats.mapValues { it.value.value }
        assertEquals(3373.0, stats["Mining Speed"])
        assertEquals(139.0, stats["Mining Spread"])
        assertEquals(862.0, stats["Health"])
        assertEquals(2.0, stats["Crit Damage"])
        assertEquals(21, stats.size)
    }
}
