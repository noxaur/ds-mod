package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.text.Role
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
    private fun card(name: String = "Black Cat", level: Int = 100, held: String? = "Gold Claws", vararg progress: String = arrayOf("", "MAX LEVEL")) = Item(
        "minecraft:player_head",
        1,
        ServerText(ServerSpan("[Lvl $level] ", 0xAAAAAA), ServerSpan(name, Role.LEGENDARY.rgb)),
        lines(" Speed: +25", "") +
            (if (held != null) listOf(ServerText(ServerSpan("Held Item")), ServerText(ServerSpan(held, Role.LEGENDARY.rgb)), ServerText(ServerSpan(""))) else emptyList()) +
            lines("LEVEL $level", *progress, "", "ACTIVE", "Click to deactivate!", "", "LEGENDARYCOMBAT PET"),
        skin = name.lowercase().replace(" ", ""),
    )

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
        val bar = hud()!!.rows.last() as BarRow
        assertEquals("XP: 1,234 / 5,000 (25%)", bar.text.plain)
        assertEquals(1234f / 5000f, bar.fraction)
    }

    @Test
    fun `a percent alone is read when the menu writes no XP`() {
        post(pet(level = 37), *menu(13 to card(level = 37, progress = arrayOf("", "Progress to Level 38: 45%"))))
        assertEquals("XP: 45%", rows()?.last())
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
    fun `the pet stays across a world change, through the blank sidebar between worlds`() {
        post(pet(), WorldChanged, Sidebar())
        assertEquals("[Lvl 100] Black Cat", rows()?.first())
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
}
