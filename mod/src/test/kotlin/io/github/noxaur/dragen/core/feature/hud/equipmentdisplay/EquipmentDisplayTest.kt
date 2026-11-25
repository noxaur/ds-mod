package io.github.noxaur.dragen.core.feature.hud.equipmentdisplay

import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.dragSimSidebar
import io.github.noxaur.dragen.harness.ownInventory
import io.github.noxaur.dragen.harness.text
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.GameEvent
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.EntitySeen
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.Sidebar
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.hud.HudDock
import io.github.noxaur.dragen.core.hud.HudLook
import io.github.noxaur.dragen.core.hud.ItemsRow
import io.github.noxaur.dragen.core.text.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquipmentDisplayTest : Harness() {
    private val feature = tree.feature<EquipmentDisplay>()

    private val helmet = Item("minecraft:player_head", 1)
    private val boots = Item("minecraft:leather_boots", 1)
    private val head = Item("minecraft:player_head", 1, data = JsonObject(mapOf("pet" to JsonPrimitive(true))))

    private fun inventory(helmet: Item?, boots: Item?) = ownInventory(5 to helmet, 8 to boots)

    private fun pet(name: String = "Ender Dragon", rgb: Int? = Role.EPIC.rgb) =
        dragSimSidebar(text(ServerSpan("Pet: ", 0xFFFFFF), ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, rgb)))

    private fun near(name: String, id: Int = 7, head: Item = this.head) = EntitySeen(id, "minecraft:armor_stand", text(name), head)

    private fun petsMenu(active: String, held: String) = arrayOf<GameEvent>(
        ContainerOpened(25, text("Pets"), 90),
        SlotChanged(12, menuItem("Tiger", held, active = false)),
        SlotChanged(13, menuItem(active, held, active = true)),
    )

    private fun menuItem(name: String, held: String, active: Boolean) = Item(
        "minecraft:player_head",
        1,
        text(ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(name, Role.EPIC.rgb)),
        listOf("Held Item", held, "", "LEVEL 100").map { text(it) } +
            (if (active) listOf("ACTIVE", "Click to deactivate!") else listOf("Click to summon!")).map { text(it) },
        skin = name.lowercase().replace(" ", ""),
    )

    private fun shown() = hud(feature)?.rows?.map { if (it is ItemsRow) it.items else it.text.plain }

    private fun heads() = shown().orEmpty().map { (it as List<*>).filterNotNull() }

    @Test
    fun `the armor shows in a row with no header, helmet first, with a gap for a piece not worn`() {
        post(*inventory(helmet, boots))
        assertEquals("", hud(feature)?.header?.plain)
        assertEquals(listOf(listOf(helmet, null, null, boots)), shown())
    }

    @Test
    fun `the pet's head sits beside the armor once one is seen, whatever its level`() {
        post(*inventory(helmet, boots), pet())
        assertEquals(listOf(listOf(helmet, null, null, boots), "[Lvl 100] Ender Dragon"), shown(), "No head seen yet, so the name shows")
        post(near("[Lvl 99] Ender Dragon"))
        assertEquals(listOf(listOf(helmet, null, null, boots, head)), shown())
    }

    @Test
    fun `the Name look puts the pet on a line in its rarity's color`() {
        feature.petLook.value = PetLook.NAME
        post(*inventory(helmet, null), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(helmet, null, null, null), "[Lvl 100] Ender Dragon"), shown())
        assertEquals(Role.EPIC.rgb, hud(feature)!!.rows.last().text.spans.last().rgb)
    }

    @Test
    fun `the Pets menu gives the active pet's item as its icon, before any head is seen`() {
        post(pet(), *petsMenu("Ender Dragon", "Iron Claws"), ContainerOpened(0, null, 46))
        val icon = (hud(feature)!!.rows.single() as ItemsRow).items.last()
        assertEquals("enderdragon", icon?.skin)
        assertEquals("Iron Claws", icon?.lore?.get(1)?.plain, "The tooltip names the held item")
    }

    @Test
    fun `the Name look shows the held item the Pets menu named`() {
        feature.petLook.value = PetLook.NAME
        post(pet(), *petsMenu("Ender Dragon", "Iron Claws"))
        assertEquals(listOf("[Lvl 100] Ender Dragon · Iron Claws"), shown())

        post(pet(name = "Tiger"))
        assertEquals(listOf("[Lvl 100] Tiger"), shown(), "The held item was another pet's")
    }

    @Test
    fun `the pet can sit on a row of its own above or below the armor`() {
        feature.petPosition.value = PetPosition.ABOVE
        post(*inventory(helmet, boots), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(head), listOf(helmet, null, null, boots)), shown())

        feature.petPosition.value = PetPosition.BELOW
        pass()
        assertEquals(listOf(listOf(helmet, null, null, boots), listOf(head)), shown())

        feature.petLook.value = PetLook.NAME
        feature.petPosition.value = PetPosition.ABOVE
        pass()
        assertEquals(listOf("[Lvl 100] Ender Dragon", listOf(helmet, null, null, boots)), shown(), "The name goes above too")
    }

    @Test
    fun `the block carries the look and the place the player picked`() {
        feature.look.value = EquipmentLook.HOTBAR
        feature.placement.value = HudDock.RIGHT_OF_HOTBAR
        post(*inventory(helmet, boots))
        assertEquals(HudLook.HOTBAR, hud(feature)?.look)
        assertEquals(HudDock.RIGHT_OF_HOTBAR, hud(feature)?.dock)
    }

    @Test
    fun `another player's pet coming nearer keeps the head already seen`() {
        post(pet(), near("[Lvl 100] Ender Dragon"), near("[Lvl 100] Tiger"))
        assertEquals(listOf(listOf(head)), heads())
    }

    @Test
    fun `a new pet's head seen before the sidebar names it still joins the row`() {
        val tiger = Item("minecraft:player_head", 1, skin = "tiger")
        post(pet(), near("[Lvl 100] Ender Dragon"), near("[Lvl 100] Tiger", 8, tiger))
        post(pet(name = "Tiger"))
        assertEquals(listOf(listOf(tiger)), heads())
    }

    @Test
    fun `another pet nearby lends no head`() {
        post(pet(), near("[Lvl 100] Tiger"))
        assertEquals(listOf("[Lvl 100] Ender Dragon"), shown())
    }

    @Test
    fun `only the picked pieces show, in the order picked`() {
        feature.pieces.value = listOf(EquipmentPiece.PET, EquipmentPiece.BOOTS, EquipmentPiece.HELMET)
        post(*inventory(helmet, boots), pet(), near("[Lvl 100] Ender Dragon"))
        assertEquals(listOf(listOf(head, boots, helmet)), shown())

        feature.pieces.value = listOf(EquipmentPiece.CHESTPLATE)
        pass()
        assertNull(shown(), "A chestplate not worn is all there is to show")
    }

    @Test
    fun `the column layout gives each piece its own row`() {
        feature.layout.value = EquipmentLayout.COLUMN
        post(*inventory(helmet, boots))
        assertEquals(listOf(listOf(helmet), listOf(null), listOf(null), listOf(boots)), shown())
    }

    @Test
    fun `another menu keeps the armor, and taking it all off hides the block`() {
        post(*inventory(helmet, boots))
        post(ContainerOpened(7, null, 54), SlotChanged(5, null), SlotChanged(8, null))
        assertEquals(listOf(listOf(helmet, null, null, boots)), shown())

        post(ContainerOpened(0, null, 46))
        assertNull(shown())
    }

    @Test
    fun `armor changed while the feature was off shows when it comes back`() {
        post(*inventory(helmet, null))
        feature.feature.enabled = false
        post(SlotChanged(8, boots))
        feature.feature.enabled = true
        pass()
        assertEquals(listOf(listOf(helmet, null, null, boots)), shown())
    }

    @Test
    fun `an empty sidebar, as between worlds, keeps the pet`() {
        post(pet())
        post(Sidebar())
        assertEquals(listOf("[Lvl 100] Ender Dragon"), shown())
    }

    @Test
    fun `leaving the server forgets the pet, and keeps the heads seen`() {
        post(pet(), near("[Lvl 100] Ender Dragon"), LeftServer)
        assertNull(shown())
        post(pet())
        assertEquals(listOf(listOf(head)), heads())
    }

    @Test
    fun `leaving the server while the feature is off forgets the pet once it is back on`() {
        leaveWhileOff(feature, pet())
        assertNull(shown())
    }

    @Test
    fun `a sidebar with no pet line, as a dungeon's, keeps the pet, and Pet None shows none`() {
        post(pet())
        post(dragSimSidebar(text("Booster: Whale Booster")))
        assertEquals(listOf("[Lvl 100] Ender Dragon"), shown())
        post(dragSimSidebar(text("Pet: None")))
        assertNull(shown())
        assertEquals(null, game.metrics.snapshot().counters["equipment-display.unparsed"])
    }

    @Test
    fun `a pet line in another shape is counted as unread`() {
        post(dragSimSidebar(text("Pet: Ender Dragon")))
        assertNull(shown())
        assertEquals(1L, game.metrics.snapshot().counters["equipment-display.unparsed"])
    }
}
