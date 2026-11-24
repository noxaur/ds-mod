package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.end.protector.protectorspawnbeam.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.visuals.camera.thirdperson.Camera
import io.github.noxaur.dsmod.core.feature.utilities.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.feature.utilities.waypoints.Waypoints
import io.github.noxaur.dsmod.core.feature.menus.betterui.backbuttons.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.protection.slotlock.SlotLock
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTreeTest {
    private val config = DsModConfig()
    private val settings = SettingsTree(config, mac = true)
    private val tree = settings.sections
    private val rows = tree.flatMap { it.walk() }
    private val rarity = settings.feature<ItemRarity>()
    private val stored = mapOf("General.recentColors" to "The color picker's recent swatches, which the player picks by picking a color, not on a row")
    private val spanned = settings.feature<CommandBinds>().run { listOf(groups, collapsedGroups, offGroups).map { it.bind.field } } +
        settings.feature<Waypoints>().run { listOf(groups, collapsedGroups, offGroups).map { it.bind.field } }

    private val saved = Store.entries.flatMap { store -> config.stored(store).all.map { it.bind.field } }

    @Test
    fun `a setting is saved in a slot of a store, so its key is written where it is declared`() {
        assertEquals(listOf("favorites"), DsModConfig.serializer().descriptor.elementNames.toList(), "Declare the setting with Stored.slot, not as a field of DsModConfig")
    }

    @Test
    fun `every saved field is reached by exactly one row, or is stored only, with a reason`() {
        assertEquals(saved, saved.distinct(), "a feature's key is also a field: ${saved.groupBy { it }.filterValues { it.size > 1 }.keys}")
        val reached = rows.filterIsInstance<Valued<*>>().map { it.field } + spanned
        assertEquals(reached, reached.distinct(), "two rows reach the same field: ${reached.groupBy { it }.filterValues { it.size > 1 }.keys}")
        for (field in saved) {
            val whole = field in reached
            val parts = reached.count { it.startsWith("$field.") }
            val only = field in stored
            assertTrue(listOf(whole, parts > 0, only).count { it } == 1, "$field: reached $whole, by $parts parts, stored only $only")
        }
        for (field in reached + stored.keys) assertTrue(saved.any { field == it || field.startsWith("$it.") }, "$field is not a saved field")
    }

    @Test
    fun `a feature in a group runs only while the group is on, and keeps its own switch`() {
        val tree = SettingsTree()
        val group = tree.features.single { it.id == "better-ui" }
        val back = tree.feature<BackButtons>().feature
        assertTrue(back.on)
        group.enabled = false
        assertFalse(back.enabled || back.on)
        assertTrue(back.switch.value)
        group.enabled = true
        assertTrue(back.on)
    }

    @Test
    fun `a reset of every row leaves the hotbar matching the inventory`() {
        for (row in rows) row.reset()
        assertTrue(rarity.hotbarMatches.value, "a reset of the hotbar's parts turned Match Inventory off")
    }

    @Test
    fun `a look's rows show its parts, and a change to one keeps the rest`() {
        rarity.inventoryStyle.value = SlotStyle(MarkStyle.AURA, opacity = 60, sparkles = true)
        @Suppress("UNCHECKED_CAST")
        val style = rows.single { it.id == "style" } as Pick<MarkStyle>
        val opacity = rows.single { it.id == "opacity" } as Number
        assertEquals(MarkStyle.AURA, style.value)
        assertEquals(60f, opacity.value)
        style.value = MarkStyle.UNDERGLOW
        opacity.value = 42f
        assertEquals(SlotStyle(MarkStyle.UNDERGLOW, opacity = 40, sparkles = true), rarity.containerStyle(), "the opacity steps by 5")
        opacity.reset()
        assertEquals(SlotStyle(MarkStyle.UNDERGLOW, sparkles = true), rarity.containerStyle())
    }

    @Test
    fun `resetting a hotbar row while it matches leaves the hotbar matching the inventory`() {
        rarity.inventoryStyle.value = SlotStyle(MarkStyle.AURA, opacity = 60)
        val style = rows.single { it.id == "hotbarStyle" } as Pick<*>
        assertEquals(MarkStyle.AURA, style.value)
        assertFalse(style.changed, "a matching hotbar style reads as changed from the Frame default")
        style.reset()
        assertTrue(rarity.hotbarMatches.value, "resetting the style turned Match Inventory off")
        assertEquals(SlotStyle(MarkStyle.AURA, opacity = 60), rarity.hotbarLook(), "resetting the style stored a hybrid hotbar look")
    }

    @Test
    fun `the hotbar's own look shows only while it does not match the inventory`() {
        val own = listOf("hotbarStyle", "hotbarOpacity", "hotbarSparkles").map { id -> rows.single { it.id == id } }
        assertTrue(own.none { it.visible })
        rarity.hotbarMatches.value = false
        assertTrue(own.all { it.visible })
    }

    @Test
    fun `a hotbar row set to another look makes it the hotbar's own`() {
        val sparkles = rows.single { it.id == "hotbarSparkles" } as Switch
        sparkles.reset()
        assertTrue(rarity.hotbarMatches.value, "the sparkles set to what they show unmatched the hotbar")
        sparkles.toggle()
        assertFalse(rarity.hotbarMatches.value, "new sparkles left the hotbar matching")
        assertEquals(SlotStyle(sparkles = true), rarity.hotbarLook())
        assertEquals(SlotStyle(), rarity.containerStyle())
    }

    @Test
    fun `every row has its own id`() {
        val ids = rows.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "repeated: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun `a feature's switch row has the feature's id and title, and no two features share an id`() {
        for (feature in settings.features) {
            assertEquals(feature.id, feature.switch.id)
            assertEquals(feature.title, feature.switch.name)
            assertTrue(feature.switch in rows, "${feature.id}'s switch is not a row of the tree")
        }
        val ids = settings.features.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "repeated: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun `names are Title Case in three words or fewer`() {
        for (row in rows) {
            val words = row.name.split(' ')
            val titled = words.withIndex().all { (i, w) -> w.first().isUpperCase() || w.first().isDigit() || i > 0 && w in MINOR }
            assertTrue(words.size <= 3 && titled, row.name)
        }
        for (group in tree.flatMap { it.groups }) group.name?.let { name -> assertTrue(name.split(' ').size <= 3, name) }
    }

    @Test
    fun `every description is one sentence that starts with a capital and ends with a period`() {
        for (row in rows) {
            val d = row.description
            assertTrue(d.first().isUpperCase() && d.endsWith(".") && !Regex("""[.!?]\s""").containsMatchIn(d), "${row.name}: $d")
        }
    }

    @Test
    fun `defaults come from a fresh config, so a loaded value shows as changed`() {
        settings.feature<Camera>().distance.value = 12f
        settings.feature<SlotLock>().slotLockKey.value = KeyBind.NONE
        val loaded = SettingsTree(config).sections.flatMap { it.walk() }.associateBy { it.id }
        assertTrue(loaded.getValue("cameraDistance").changed)
        assertTrue(loaded.getValue("slotLockKey").changed)
        assertFalse(loaded.getValue("dragon-timer").changed)
        loaded.getValue("cameraDistance").reset()
        assertEquals(4f, settings.feature<Camera>().distance.value)
        assertEquals("L", loaded.getValue("slotLockKey").default { if (it == 76) "L" else "?" })
    }

    @Test
    fun `rows dim under their switch, the group's switch, and what they depend on`() {
        val byId = rows.associateBy { it.id }
        settings.feature<ProtectorSpawnBeam>().feature.enabled = false
        assertTrue(byId.getValue("beamSections").dimmed)
        settings.feature<ProtectorSpawnBeam>().feature.enabled = true
        assertFalse(byId.getValue("beamSections").dimmed)
        settings.feature<Camera>().feature.enabled = false
        assertTrue(byId.getValue("cameraTilt").dimmed)
        rarity.feature.enabled = false
        assertTrue(byId.getValue("style").dimmed, "a feature's rows dim with the group around it")
        settings.feature<CommandBinds>().feature.enabled = false
        assertTrue(byId.getValue("sequenceGap").dimmed)
        assertFalse(byId.getValue("command-binds").dimmed)
    }

    @Test
    fun `a closed switch sums up its settings`() {
        val slotLock = rows.single { it.id == "slot-lock" } as Switch
        assertEquals("L, K", slotLock.closed { mapOf(76 to "L", 75 to "K")[it] ?: "?" })
        val hotbar = rows.single { it.id == "hotbarMarks" } as Switch
        assertEquals("", hotbar.closed { "" })
        rarity.hotbarMatches.value = false
        assertEquals("Frame, 100%", hotbar.closed { "" })
    }

    @Test
    fun `the sidebar lists only named groups, and a section with none has one unnamed group`() {
        for (section in tree) {
            if (section.named.isEmpty()) assertEquals(1, section.groups.size, section.name) else assertTrue(section.groups.all { it.name != null }, section.name)
        }
    }

    private companion object {
        val MINOR = setOf("a", "an", "the", "of", "in", "on", "to", "for", "and", "or")
    }
}
