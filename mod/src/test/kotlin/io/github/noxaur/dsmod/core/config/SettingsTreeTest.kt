package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tree is the schema of the settings, so these keep it one system: every saved field has one row, worded alike. */
class SettingsTreeTest {
    private val config = DsModConfig()
    private val tree = SettingsTree.sections(config)
    private val rows = tree.flatMap { it.walk() }

    /** `Class.field` of every field in each category of `DsModConfig` that the config file saves. */
    private val saved = DsModConfig.serializer().descriptor.let { config ->
        (0 until config.elementsCount).map(config::getElementDescriptor).filter { it.kind == StructureKind.CLASS }.flatMap { category ->
            category.elementNames.map { "${category.serialName.substringAfterLast('.')}.$it" }
        }
    }

    @Test
    fun `every saved field is reached by exactly one row, or is stored only, with a reason`() {
        val reached = rows.filterIsInstance<Valued<*>>().flatMap { it.fields }
        assertEquals(reached, reached.distinct(), "two rows reach the same field: ${reached.groupBy { it }.filterValues { it.size > 1 }.keys}")
        for (field in saved) {
            val whole = field in reached
            val parts = reached.count { it.startsWith("$field.") }
            val stored = field in SettingsTree.STORED
            assertTrue(listOf(whole, parts > 0, stored).count { it } == 1, "$field: reached $whole, by $parts parts, stored only $stored")
        }
        for (field in reached + SettingsTree.STORED.keys) assertTrue(saved.any { field == it || field.startsWith("$it.") }, "$field is not a saved field")
    }

    @Test
    fun `a reset of every row leaves the hotbar matching the inventory`() {
        for (row in rows) row.reset()
        assertTrue(config.inventory.hotbarMatches, "a reset of the hotbar's parts turned Match Inventory off")
    }

    @Test
    fun `resetting a look part under a preset keeps that preset and the rest of its look`() {
        config.inventory.rarityPreset = RarityPreset.SHOWCASE
        val border = rows.single { it.id == "border" } as Pick<*>
        assertEquals(Border.ROUNDED, border.value)
        assertFalse(border.changed, "a preset's border reads as changed from the Gilded default")
        border.reset()
        assertEquals(RarityPreset.SHOWCASE, config.inventory.rarityPreset, "resetting the border forced Custom")
        assertEquals(Border.ROUNDED, config.inventory.containerStyle().border)
        assertEquals(Fill.HALO, config.inventory.containerStyle().fill, "resetting the border wrote a Gilded fill into the look")
        assertEquals(Floor.PEDESTAL, config.inventory.containerStyle().floor)
    }

    @Test
    fun `resetting a hotbar part under a preset leaves the hotbar matching the inventory`() {
        config.inventory.rarityPreset = RarityPreset.SHOWCASE
        val border = rows.filterIsInstance<Choice<*>>().single { it.id == "hotbarBorder" }
        assertEquals(Border.ROUNDED, border.value)
        assertFalse(border.changed, "a matching hotbar border reads as changed from the Gilded default")
        border.reset()
        assertTrue(config.inventory.hotbarMatches, "resetting the border turned Match Inventory off")
        assertEquals(Border.ROUNDED, config.inventory.hotbarLook().border)
        assertEquals(Fill.HALO, config.inventory.hotbarLook().fill, "resetting the border stored a hybrid hotbar look")
    }

    @Test
    fun `a hotbar part set to another look makes it the hotbar's own`() {
        val border = rows.filterIsInstance<Choice<*>>().single { it.id == "hotbarBorder" }
        border.reset()
        assertTrue(config.inventory.hotbarMatches, "the border set to what it shows unmatched the hotbar")
        border.step(1)
        assertFalse(config.inventory.hotbarMatches, "a new border left the hotbar matching")
    }

    @Test
    fun `every row has its own id`() {
        val ids = rows.map { it.id }
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
        config.general.cameraDistance = 12f
        config.inventory.slotLockKey = KeyBind.NONE
        val loaded = SettingsTree.sections(config).flatMap { it.walk() }.associateBy { it.id }
        assertTrue(loaded.getValue("cameraDistance").changed)
        assertTrue(loaded.getValue("slotLockKey").changed)
        assertFalse(loaded.getValue("dragonTimer").changed)
        loaded.getValue("cameraDistance").reset()
        assertEquals(4f, config.general.cameraDistance)
        assertEquals("L", loaded.getValue("slotLockKey").default { if (it == 76) "L" else "?" })
    }

    @Test
    fun `a part of the custom look changes the look shown and switches the style to Custom`() {
        config.inventory.rarityPreset = RarityPreset.SHOWCASE
        val border = rows.single { it.id == "border" } as Pick<*>
        assertEquals(Border.ROUNDED, border.value, "the part shows the preset's own")
        @Suppress("UNCHECKED_CAST")
        (border as Pick<Border>).value = Border.FLAT
        assertEquals(RarityPreset.CUSTOM, config.inventory.rarityPreset)
        assertEquals(Border.FLAT, config.inventory.inventoryStyle.border)
        assertEquals(RarityPreset.SHOWCASE.style!!.fill, config.inventory.inventoryStyle.fill, "the rest of the look stays as it was shown")
    }

    @Test
    fun `rows dim under their switch, the group's switch, and what they depend on`() {
        val byId = rows.associateBy { it.id }
        config.end.protectorSpawnBeam = false
        assertTrue(byId.getValue("beamSections").dimmed)
        config.end.protectorSpawnBeam = true
        assertFalse(byId.getValue("beamSections").dimmed)
        config.general.camera = false
        assertTrue(byId.getValue("cameraTilt").dimmed)
        config.inventory.itemRarity = false
        assertTrue(byId.getValue("border").dimmed, "a fold's rows dim with the group around it")
        config.general.commandBinds = false
        assertTrue(byId.getValue("sequenceGap").dimmed)
        assertFalse(byId.getValue("commandBinds").dimmed)
    }

    @Test
    fun `a closed switch sums up its settings`() {
        val slotLock = rows.single { it.id == "slotLock" } as Switch
        assertEquals("L, K", slotLock.closed { mapOf(76 to "L", 75 to "K")[it] ?: "?" })
        assertEquals("Gilded · Glow · None", rows.single { it.id == "customLook" }.summary { "" })
    }

    @Test
    fun `the sidebar lists only named groups, and a section with none has one unnamed group`() {
        for (section in tree) {
            if (section.named.isEmpty()) assertEquals(1, section.groups.size, section.name) else assertTrue(section.groups.all { it.name != null }, section.name)
        }
    }

    private companion object {
        /** Words Title Case leaves small after the first. */
        val MINOR = setOf("a", "an", "the", "of", "in", "on", "to", "for", "and", "or")
    }
}
