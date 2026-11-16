package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.text.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivePetTest {
    private val config = DsModConfig().apply { general.activePet = true }
    private val game = Game(features(config))
    private var time = 0L

    private fun feature() = readout().feature

    @BeforeTest
    fun quiet() {
        // Equipment Display reads the same lines, so it is off to keep the readouts to this feature's.
        config.general.equipmentDisplay = false
    }

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(++time, event)
        time += 250
        game.tick(time)
    }

    private fun readout() = game.readouts.single { it.feature.id == "active-pet" }
    private fun hud() = readout().hud
    private fun rows() = hud()?.rows?.map { it.text.plain }

    private fun sidebar(vararg lines: ServerText) = Sidebar(ServerText(ServerSpan("DragSim")), listOf(ServerText(ServerSpan("Purse: 1,000")), *lines))

    private fun petLine(name: String = "Black Cat", level: Int = 100, rgb: Int = Role.LEGENDARY.rgb) =
        ServerText(ServerSpan("Pet: ", 0xFFFFFF), ServerSpan("[Lvl $level] ", 0xAAAAAA), ServerSpan(name, rgb))

    private fun pet(name: String = "Black Cat", level: Int = 100, rgb: Int = Role.LEGENDARY.rgb) = sidebar(petLine(name, level, rgb))

    private fun none() = sidebar(ServerText(ServerSpan("Pet: ", 0xFFFFFF), ServerSpan("None", 0xAAAAAA)))

    private fun dungeon() = sidebar(ServerText(ServerSpan("⏣ The Catacombs (F1)")))

    private fun chat(line: String) = SystemMessage(ServerText(ServerSpan(line)))

    private fun lines(vararg lines: String) = lines.map { ServerText(ServerSpan(it)) }

    /** The active pet's item as the Pets menu shows it, with [progress] as the lines under its level. */
    private fun card(
        name: String = "Black Cat",
        level: Int = 100,
        held: String? = "Gold Claws",
        vararg progress: String = arrayOf("", "MAX LEVEL"),
        category: String = "COMBAT",
        uuid: String? = null,
        active: Boolean = true,
    ) = Item(
        "minecraft:player_head",
        1,
        ServerText(ServerSpan("[Lvl $level] ", 0xAAAAAA), ServerSpan(name, Role.LEGENDARY.rgb)),
        lines(" Speed: +25", "") +
            (if (held != null) listOf(ServerText(ServerSpan("Held Item")), ServerText(ServerSpan(held, Role.LEGENDARY.rgb)), ServerText(ServerSpan(""))) else emptyList()) +
            lines("LEVEL $level", *progress, "") +
            lines(*(if (active) arrayOf("ACTIVE", "Click to deactivate!") else arrayOf("Click to summon!"))) +
            lines("", "LEGENDARY${category} PET"),
        data = uuid?.let { JsonObject(mapOf("extra" to JsonObject(mapOf("item_uuid" to JsonObject(mapOf("uuid" to JsonPrimitive(it))))))) } ?: JsonObject(emptyMap()),
        skin = name.lowercase().replace(" ", ""),
    )

    /** Lets [millis] pass, then refreshes the readouts. */
    private fun pass(millis: Long) {
        time += millis
        game.tick(time)
    }

    /** A mob the player killed, and the action bar's Combat XP right behind its death. */
    private fun kill(combat: Int) {
        game.post(++time, EntityDied("minecraft:zombie", ServerText(ServerSpan("Zombie")), 0.0, 0.0, 0.0))
        game.post(++time, ActionBar(ServerText(ServerSpan("100/100❤     +$combat Combat (5%)     ✎ Mana 50/50"))))
        time += 250
        game.tick(time)
    }

    private fun xp(have: Int) = arrayOf("", "Progress to Level 38", "$have / 10,000 XP")

    private fun other(name: String = "Wolf") = Item(
        "minecraft:player_head",
        1,
        ServerText(ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, Role.EPIC.rgb)),
        lines("LEVEL 100", "", "MAX LEVEL", "", "Click to summon!"),
    )

    private fun menu(vararg slots: Pair<Int, Item>) =
        arrayOf<GameEvent>(ContainerOpened(30, ServerText(ServerSpan("Pets")), 90)) + slots.map { (slot, item) -> SlotChanged(slot, item) }

    @Test
    fun `shows nothing until the server names a pet`() {
        post(sidebar())
        assertNull(hud())
    }

    @Test
    fun `the sidebar's pet shows by name and level in its rarity, with no header, and asks for the menu for its XP`() {
        post(pet())
        assertEquals("", hud()?.header?.plain)
        assertEquals(listOf("[Lvl 100] Black Cat", "Open your Pets menu to read its XP."), rows())
        assertEquals(Role.LEGENDARY.rgb, hud()!!.rows[0].text.spans.last().rgb)
        assertNull(hud()?.icon)
    }

    @Test
    fun `the Pets menu gives the icon, the held item in its rarity, and max level`() {
        post(pet(), *menu(12 to other(), 13 to card()))
        assertEquals(listOf("[Lvl 100] Black Cat", "Item: Gold Claws", "XP: MAX LEVEL"), rows())
        assertEquals("blackcat", hud()?.icon?.skin)
        assertEquals(Role.LEGENDARY.rgb, hud()!!.rows[1].text.spans.last().rgb)
    }

    @Test
    fun `a pet below its max level shows its XP as a bar`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = arrayOf("", "Progress to Level 38: 45%", "1,234 / 5,000 XP"))))
        val bar = hud()!!.rows.filterIsInstance<BarRow>().single()
        assertEquals("XP: 1,234 / 5,000 (25%)", bar.text.plain)
        assertEquals(1234f / 5000f, bar.fraction)
    }

    @Test
    fun `a percent alone is read when the menu writes no XP`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = arrayOf("", "Progress to Level 38: 45%"))))
        assertEquals("XP: 45%", rows()?.get(2))
    }

    @Test
    fun `a level up drops the XP read at the old level`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = arrayOf("", "45%"))))
        post(ContainerOpened(0, null, 46), pet(level = 38))
        assertEquals(listOf("[Lvl 38] Black Cat", "Item: Gold Claws", "Open your Pets menu to read its XP."), rows())
    }

    @Test
    fun `Pet None hides the block, and a dungeon's sidebar with no pet line keeps it`() {
        post(pet())
        post(dungeon())
        assertEquals("[Lvl 100] Black Cat", rows()?.first())
        post(none())
        assertNull(hud())
    }

    @Test
    fun `in a dungeon, despawning the pet in the menu hides it, and turning a page does not`() {
        post(pet(), dungeon(), *menu(13 to card()))
        post(SlotChanged(13, other("Wolf")))
        assertEquals("[Lvl 100] Black Cat", rows()?.first())
        post(SlotChanged(13, card()), SlotChanged(13, other("Black Cat")))
        assertNull(hud())
    }

    @Test
    fun `in a dungeon, the menu brings the level the pet reached, with its XP`() {
        post(pet(level = 37), dungeon(), *menu(13 to card(level = 38, progress = arrayOf("", "10%"))))
        assertEquals(listOf("[Lvl 38] Black Cat", "Item: Gold Claws", "XP: 10%", "Open your Pets menu again to see a rate."), rows())
    }

    @Test
    fun `the pet stays across a world change, through the blank sidebar between worlds`() {
        post(pet(), WorldChanged, Sidebar())
        assertEquals("[Lvl 100] Black Cat", rows()?.first())
    }

    @Test
    fun `leaving the server forgets the pet`() {
        post(pet(), LeftServer)
        assertNull(hud())
    }

    @Test
    fun `leaving the server while the feature is off forgets the pet once it is back on`() {
        post(pet())
        config.general.activePet = false
        post(LeftServer)
        config.general.activePet = true
        post()
        assertNull(hud())
    }

    @Test
    fun `a summon in chat switches the pet, in the rarity it was last seen in`() {
        post(pet("Wolf", rgb = Role.EPIC.rgb), pet())
        post(dungeon(), chat("Activated [Lvl 100] Wolf!"))
        assertEquals("[Lvl 100] Wolf", rows()?.first())
        assertEquals(Role.EPIC.rgb, hud()!!.rows[0].text.spans.last().rgb)
    }

    @Test
    fun `a new held item in chat replaces the old one`() {
        post(pet(), *menu(13 to card()))
        post(chat("Equipped Lucky Clover on [Lvl 100] Black Cat!"))
        assertEquals("Item: Lucky Clover", rows()?.get(1))
    }

    @Test
    fun `the head of a pet of that name near the player is the icon until the menu shows one`() {
        val head = Item("minecraft:player_head", 1, skin = "floating")
        post(pet(), PetNear(ServerText(ServerSpan("[Lvl 100] Black Cat")), head))
        assertEquals(head, hud()?.icon)
        post(*menu(13 to card()))
        assertEquals("blackcat", hud()?.icon?.skin)
    }

    @Test
    fun `each part can be turned off`() {
        post(pet(), *menu(13 to card()))
        config.general.activePetIcon = false
        config.general.activePetItem = false
        config.general.activePetXp = false
        post()
        assertNull(hud()?.icon)
        assertEquals(listOf("[Lvl 100] Black Cat"), rows())
    }

    @Test
    fun `the active pet is marked in the Pets menu, and the mark goes when it is despawned`() {
        post(pet(), *menu(12 to other(), 13 to card()))
        assertEquals(mapOf(13 to SlotMark(Role.GOOD)), readout().slots)
        post(SlotChanged(13, other("Black Cat")))
        assertEquals(emptyMap(), readout().slots)
        post(SlotChanged(13, card()))
        config.general.markActivePet = false
        post()
        assertEquals(emptyMap(), readout().slots)
    }

    @Test
    fun `what the menu showed is kept by pet name for the next session`() {
        post(pet(), *menu(13 to card()))
        val saved = feature().state!!.encode()
        val next = Game(features(DsModConfig().apply { general.activePet = true; general.equipmentDisplay = false }))
        next.readouts.single { it.feature.id == "active-pet" }.feature.state!!.load(saved)
        next.post(1, pet())
        next.tick(300)
        val hud = next.readouts.single { it.feature.id == "active-pet" }.hud
        assertEquals("blackcat", hud?.icon?.skin)
        assertEquals(listOf("[Lvl 100] Black Cat", "Item: Gold Claws", "XP: MAX LEVEL"), hud?.rows?.map { it.text.plain })
    }

    @Test
    fun `two reads of the menu give the XP per hour and the time to the next level, which counts down`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000))))
        assertEquals("Open your Pets menu again to see a rate.", rows()?.last())
        pass(30 * 60_000L)
        post(*menu(13 to card(level = 37, progress = xp(2000))))
        // 1,000 XP in half an hour is 2,000 an hour, so the 8,000 left take four hours.
        assertEquals(listOf("XP per Hour: 2,000", "Next Level: 4h 0m"), rows()?.takeLast(2))
        pass(60 * 60_000L)
        assertEquals("Next Level: 3h 0m", rows()?.last())
    }

    @Test
    fun `with a percent alone the rate is in percent`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = arrayOf("", "10%"))))
        pass(60 * 60_000L)
        post(*menu(13 to card(level = 37, progress = arrayOf("", "35%"))))
        assertEquals(listOf("XP per Hour: 25%", "Next Level: 2h 36m"), rows()?.takeLast(2))
    }

    @Test
    fun `Combat XP moves a combat pet's XP live, at the rate the reads showed, and each read sets it right`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000))))
        kill(100)
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(1050))))
        // 100 Combat XP gave 50 pet XP, so each Combat XP is half a pet XP.
        kill(300)
        assertEquals("XP: 1,200 / 10,000 (12%)", rows()?.get(2))
        post(*menu(13 to card(level = 37, progress = xp(1120))))
        assertEquals("XP: 1,120 / 10,000 (11%)", rows()?.get(2))
    }

    @Test
    fun `Combat XP moves no other pet, and an action bar with no death behind it counts for none`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000), category = "MINING")))
        kill(100)
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(1020), category = "MINING")))
        kill(200)
        assertEquals("XP: 1,020 / 10,000 (10%)", rows()?.get(2))
        post(pet(), *menu(13 to card(level = 37, progress = xp(1000))))
        post(ActionBar(ServerText(ServerSpan("+500 Combat (5%)"))))
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(1020))))
        kill(200)
        assertEquals("XP: 1,020 / 10,000 (10%)", rows()?.get(2))
    }

    @Test
    fun `a level up and a switch of pet start the rate over`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000))))
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(2000))))
        assertEquals(true, rows()?.get(3)?.startsWith("XP per Hour: "))
        post(ContainerOpened(0, null, 46), pet(level = 38), *menu(13 to card(level = 38, progress = xp(100))))
        assertEquals("Open your Pets menu again to see a rate.", rows()?.last())
        pass(60_000L)
        post(*menu(13 to card(level = 38, progress = xp(200))))
        post(ContainerOpened(0, null, 46), pet("Wolf", level = 38), *menu(13 to card("Wolf", level = 38, progress = xp(200))))
        assertEquals("Open your Pets menu again to see a rate.", rows()?.last())
    }

    @Test
    fun `a summon of another pet of the same name and level starts the rate over`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000))))
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(1100))))
        post(ContainerOpened(0, null, 46), chat("Autopet equipped your [Lvl 37] Black Cat! VIEW RULE"))
        assertEquals("Open your Pets menu to read its XP.", rows()?.last())
        post(*menu(13 to card(level = 37, progress = xp(8000))))
        assertEquals("Open your Pets menu again to see a rate.", rows()?.last())
        pass(60_000L)
        post(*menu(13 to card(level = 37, progress = xp(500))))
        assertEquals("Open your Pets menu again to see a rate.", rows()?.last())
    }

    @Test
    fun `the rate rows can be turned off`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = xp(1000))))
        config.general.activePetRate = false
        config.general.activePetNext = false
        post()
        assertEquals(listOf("[Lvl 37] Black Cat", "Item: Gold Claws", "XP: 1,000 / 10,000 (10%)"), rows())
    }

    @Test
    fun `Autopet's swap switches the pet, and its line is hidden when the player asks`() {
        post(pet(), pet("Wolf", rgb = Role.EPIC.rgb), dungeon())
        assertEquals(true, game.post(++time, chat("Autopet equipped your [Lvl 100] Black Cat! VIEW RULE")))
        game.tick(time)
        assertEquals("[Lvl 100] Black Cat", rows()?.first())
        config.general.hideAutopet = true
        assertEquals(false, game.post(++time, chat("Autopet equipped your [Lvl 100] Wolf! VIEW RULE")))
        game.tick(time)
        assertEquals("[Lvl 100] Wolf", rows()?.first())
        assertEquals(false, game.post(++time, chat("Autopet swapped something new")))
    }

    @Test
    fun `the Dragon Sim Menu's Pets button names the selected pet`() {
        post(pet(), dungeon())
        val button = Item(
            "minecraft:bone",
            1,
            ServerText(ServerSpan("Pets")),
            listOf(ServerText(ServerSpan("View and manage your pets.")), ServerText(ServerSpan("Selected Pet: ", 0xAAAAAA), ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan("Wolf", Role.EPIC.rgb))),
        )
        post(ContainerOpened(31, ServerText(ServerSpan("Dragon Sim Menu")), 90), SlotChanged(20, button))
        assertEquals("[Lvl 100] Wolf", rows()?.first())
        assertEquals(Role.EPIC.rgb, hud()!!.rows[0].text.spans.last().rgb)
    }

    @Test
    fun `two pets of one name are told apart by their uuid, and share what is known without one`() {
        post(pet("Enderman"), *menu(10 to card("Enderman", held = "Gold Claws", uuid = "a"), 14 to card("Enderman", held = null, uuid = "b", active = false)))
        assertEquals("Item: Gold Claws", rows()?.get(1))
        post(SlotChanged(10, card("Enderman", held = "Gold Claws", uuid = "a", active = false)), SlotChanged(14, card("Enderman", held = "Lucky Clover", uuid = "b")))
        assertEquals("Item: Lucky Clover", rows()?.get(1))
        post(chat("Equipped Spooky Cupcake on [Lvl 100] Enderman!"))
        post(SlotChanged(14, card("Enderman", held = "Spooky Cupcake", uuid = "b", active = false)), SlotChanged(10, card("Enderman", held = "Gold Claws", uuid = "a")))
        assertEquals("Item: Gold Claws", rows()?.get(1))
        // With no uuid, the menu's last read of either is what shows.
        post(*menu(10 to card("Enderman", held = "Iron Claws")))
        assertEquals("Item: Iron Claws", rows()?.get(1))
    }
}
