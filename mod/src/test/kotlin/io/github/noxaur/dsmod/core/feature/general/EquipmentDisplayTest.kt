package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.core.text.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquipmentDisplayTest {
    private val tree = SettingsTree()
    private val feature = tree.feature<EquipmentDisplay>()
    private val game = Game(tree.features)
    private var time = 0L

    private val helmet = Item("minecraft:player_head", 1)
    private val boots = Item("minecraft:leather_boots", 1)
    private val head = Item("minecraft:player_head", 1, data = JsonObject(mapOf("pet" to JsonPrimitive(true))))

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(++time, event)
        // A readout refreshes at least every 250 ms, which is how a changed setting shows with no event.
        time += 250
        game.tick(time)
    }

    private fun inventory(helmet: Item?, boots: Item?) =
        arrayOf<GameEvent>(ContainerOpened(0, null, 46), SlotChanged(5, helmet), SlotChanged(8, boots))

    private fun sidebar(vararg lines: ServerText) = Sidebar(ServerText(ServerSpan("DragSim")), listOf(ServerText(ServerSpan("Purse: 1,000")), *lines))

    private fun pet(name: String = "Ender Dragon", rgb: Int? = Role.EPIC.rgb) =
        sidebar(ServerText(ServerSpan("Pet: ", 0xFFFFFF), ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, rgb)))

    private fun near(name: String) = PetNear(ServerText(ServerSpan(name)), head)

    /** The Pets menu with [active] marked as dragsim.co marks the pet that is out, holding [held]. */
    private fun petsMenu(active: String, held: String) = arrayOf<GameEvent>(
        ContainerOpened(25, ServerText(ServerSpan("Pets")), 90),
        SlotChanged(12, menuItem("Tiger", held, active = false)),
        SlotChanged(13, menuItem(active, held, active = true)),
    )

    private fun menuItem(name: String, held: String, active: Boolean) = Item(
        "minecraft:player_head",
        1,
        ServerText(ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, Role.EPIC.rgb)),
        listOf("Held Item", held, "", "LEVEL 100").map { ServerText(ServerSpan(it)) } +
            (if (active) listOf("ACTIVE", "Click to deactivate!") else listOf("Click to summon!")).map { ServerText(ServerSpan(it)) },
        skin = name.lowercase().replace(" ", ""),
    )

    private fun hud() = game.readouts.single { it.feature.id == "equipment-display" }.hud

    /** Each row as its icons, or as its text. */
    private fun rows() = hud()?.rows?.map { if (it is ItemsRow) it.items else it.text.plain }

    @Test
    fun `the armor shows in a row with no header, helmet first, with a gap for a piece not worn`() {
        post(*inventory(helmet, boots))
        assertEquals("", hud()?.header?.plain)
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())
    }

    @Test
    fun `the pet's head sits beside the armor once one is seen, whatever its level`() {
        post(*inventory(helmet, boots), pet())
        assertEquals(listOf(listOf(helmet, null, null, boots), "[Lvl 100] Ender Dragon"), rows(), "No head seen yet, so the name shows")
        post(near("[Lvl 99] Ender Dragon"))
        assertEquals(listOf(listOf(helmet, null, null, boots, head)), rows())
    }

    @Test
    fun `the Name look puts the pet on a line in its rarity's color`() {
        feature.petLook.value = PetLook.NAME
        post(*inventory(helmet, null), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(helmet, null, null, null), "[Lvl 100] Ender Dragon"), rows())
        assertEquals(Role.EPIC.rgb, hud()!!.rows.last().text.spans.last().rgb)
    }

    @Test
    fun `the Pets menu gives the active pet's item as its icon, before any head is seen`() {
        post(pet(), *petsMenu("Ender Dragon", "Iron Claws"), ContainerOpened(0, null, 46))
        val icon = (hud()!!.rows.single() as ItemsRow).items.last()
        assertEquals("enderdragon", icon?.skin)
        assertEquals("Iron Claws", icon?.lore?.get(1)?.plain, "The tooltip names the held item")
    }

    @Test
    fun `the Name look shows the held item the Pets menu named`() {
        feature.petLook.value = PetLook.NAME
        post(pet(), *petsMenu("Ender Dragon", "Iron Claws"))
        assertEquals(listOf("[Lvl 100] Ender Dragon · Iron Claws"), rows())

        post(pet(name = "Tiger"))
        assertEquals(listOf("[Lvl 100] Tiger"), rows(), "The held item was another pet's")
    }

    @Test
    fun `the pet can sit on a row of its own above or below the armor`() {
        feature.petPosition.value = PetPosition.ABOVE
        post(*inventory(helmet, boots), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(head), listOf(helmet, null, null, boots)), rows())

        feature.petPosition.value = PetPosition.BELOW
        post()
        assertEquals(listOf(listOf(helmet, null, null, boots), listOf(head)), rows())

        feature.petLook.value = PetLook.NAME
        feature.petPosition.value = PetPosition.ABOVE
        post()
        assertEquals(listOf("[Lvl 100] Ender Dragon", listOf(helmet, null, null, boots)), rows(), "The name goes above too")
    }

    @Test
    fun `the block carries the look and the place the player picked`() {
        feature.look.value = EquipmentLook.HOTBAR
        feature.placement.value = HudDock.RIGHT_OF_HOTBAR
        post(*inventory(helmet, boots))
        assertEquals(HudLook.HOTBAR, hud()?.look)
        assertEquals(HudDock.RIGHT_OF_HOTBAR, hud()?.dock)
    }

    @Test
    fun `another player's pet coming nearer keeps the head already seen`() {
        post(pet(), near("[Lvl 100] Ender Dragon"), near("[Lvl 100] Tiger"))
        assertEquals(listOf(listOf(head)), rows().orEmpty().map { (it as List<*>).filterNotNull() })
    }

    @Test
    fun `a new pet's head seen before the sidebar names it still joins the row`() {
        val tiger = Item("minecraft:player_head", 1, skin = "tiger")
        post(pet(), near("[Lvl 100] Ender Dragon"), PetNear(ServerText(ServerSpan("[Lvl 100] Tiger")), tiger))
        post(pet(name = "Tiger"))
        assertEquals(listOf(listOf(tiger)), rows().orEmpty().map { (it as List<*>).filterNotNull() })
    }

    @Test
    fun `another pet nearby lends no head`() {
        post(pet(), near("[Lvl 100] Tiger"))
        assertEquals(listOf("[Lvl 100] Ender Dragon"), rows())
    }

    @Test
    fun `only the picked pieces show, in the order picked`() {
        feature.pieces.value = listOf(EquipmentPiece.PET, EquipmentPiece.BOOTS, EquipmentPiece.HELMET)
        post(*inventory(helmet, boots), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(head, boots, helmet)), rows())

        feature.pieces.value = listOf(EquipmentPiece.CHESTPLATE)
        post()
        assertNull(rows(), "A chestplate not worn is all there is to show")
    }

    @Test
    fun `the column layout gives each piece its own row`() {
        feature.layout.value = EquipmentLayout.COLUMN
        post(*inventory(helmet, boots))
        assertEquals(listOf(listOf(helmet), listOf(null), listOf(null), listOf(boots)), rows())
    }

    @Test
    fun `another menu keeps the armor, and taking it all off hides the block`() {
        post(*inventory(helmet, boots))
        post(ContainerOpened(7, null, 54), SlotChanged(5, null), SlotChanged(8, null))
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())

        post(ContainerOpened(0, null, 46))
        assertNull(rows())
    }

    @Test
    fun `armor changed while the feature was off shows when it comes back`() {
        post(*inventory(helmet, null))
        feature.enabled = false
        post(SlotChanged(8, boots))
        feature.enabled = true
        post()
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())
    }

    @Test
    fun `an empty sidebar, as between worlds, keeps the pet`() {
        post(pet())
        post(Sidebar())
        assertEquals(listOf("[Lvl 100] Ender Dragon"), rows())
    }

    @Test
    fun `leaving the server forgets the pet, and keeps the heads seen`() {
        post(pet(), near("[Lvl 100] Ender Dragon"), LeftServer)
        assertNull(rows())
        post(pet())
        assertEquals(listOf(listOf(head)), rows().orEmpty().map { (it as List<*>).filterNotNull() })
    }

    @Test
    fun `leaving the server while the feature is off forgets the pet once it is back on`() {
        post(pet())
        feature.enabled = false
        post(LeftServer)
        feature.enabled = true
        post()
        assertNull(rows())
    }

    @Test
    fun `no pet line, or Pet None, shows no pet`() {
        post(pet())
        post(sidebar(ServerText(ServerSpan("Booster: Whale Booster"))))
        assertNull(rows())
        post(sidebar(ServerText(ServerSpan("Pet: None"))))
        assertNull(rows())
        assertEquals(null, game.metrics.snapshot().counters["equipment-display.unparsed"])
    }

    @Test
    fun `a pet line in another shape is counted as unread`() {
        post(sidebar(ServerText(ServerSpan("Pet: Ender Dragon"))))
        assertNull(rows())
        assertEquals(1L, game.metrics.snapshot().counters["equipment-display.unparsed"])
    }
}
