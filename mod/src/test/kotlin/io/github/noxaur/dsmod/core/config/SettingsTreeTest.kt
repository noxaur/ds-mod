package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tree is the schema of the settings, so these keep it one system: every saved field has one row, worded alike. */
class SettingsTreeTest {
    private val config = DsModConfig()
    private val settings = SettingsTree(config)
    private val tree = settings.sections
    private val rows = tree.flatMap { it.walk() }
    private val rarity = settings.feature<ItemRarity>()

    /**
     * `Class.field` of every field in each category of `DsModConfig` that the config file saves, and of every slot the
     * features declared while the tree was built.
     */
    private val saved = DsModConfig.serializer().descriptor.let { config ->
        (0 until config.elementsCount).map(config::getElementDescriptor).filter { it.kind == StructureKind.CLASS }.flatMap { category ->
            category.elementNames.map { "${category.serialName.substringAfterLast('.')}.$it" }
        }
    } + Store.entries.flatMap { store -> config.stored(store).all.map { it.bind.field } }

    @Test
    fun `every saved field is reached by exactly one row, or is stored only, with a reason`() {
        assertEquals(saved, saved.distinct(), "a feature's key is also a field: ${saved.groupBy { it }.filterValues { it.size > 1 }.keys}")
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
        assertTrue(rarity.hotbarMatches.value, "a reset of the hotbar's parts turned Match Inventory off")
    }

    @Test
    fun `resetting a look part under a preset keeps that preset and the rest of its look`() {
        rarity.preset.value = RarityPreset.SHOWCASE
        val border = rows.single { it.id == "border" } as Pick<*>
        assertEquals(Border.ROUNDED, border.value)
        assertFalse(border.changed, "a preset's border reads as changed from the Gilded default")
        border.reset()
        assertEquals(RarityPreset.SHOWCASE, rarity.preset.value, "resetting the border forced Custom")
        assertEquals(Border.ROUNDED, rarity.containerStyle().border)
        assertEquals(Fill.HALO, rarity.containerStyle().fill, "resetting the border wrote a Gilded fill into the look")
        assertEquals(Floor.PEDESTAL, rarity.containerStyle().floor)
    }

    @Test
    fun `resetting a hotbar part under a preset leaves the hotbar matching the inventory`() {
        rarity.preset.value = RarityPreset.SHOWCASE
        val border = rows.filterIsInstance<Choice<*>>().single { it.id == "hotbarBorder" }
        assertEquals(Border.ROUNDED, border.value)
        assertFalse(border.changed, "a matching hotbar border reads as changed from the Gilded default")
        border.reset()
        assertTrue(rarity.hotbarMatches.value, "resetting the border turned Match Inventory off")
        assertEquals(Border.ROUNDED, rarity.hotbarLook().border)
        assertEquals(Fill.HALO, rarity.hotbarLook().fill, "resetting the border stored a hybrid hotbar look")
    }

    @Test
    fun `a hotbar part set to another look makes it the hotbar's own`() {
        val border = rows.filterIsInstance<Choice<*>>().single { it.id == "hotbarBorder" }
        border.reset()
        assertTrue(rarity.hotbarMatches.value, "the border set to what it shows unmatched the hotbar")
        border.step(1)
        assertFalse(rarity.hotbarMatches.value, "a new border left the hotbar matching")
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
    fun `a part of the custom look changes the look shown and switches the style to Custom`() {
        rarity.preset.value = RarityPreset.SHOWCASE
        val border = rows.single { it.id == "border" } as Pick<*>
        assertEquals(Border.ROUNDED, border.value, "the part shows the preset's own")
        @Suppress("UNCHECKED_CAST")
        (border as Pick<Border>).value = Border.FLAT
        assertEquals(RarityPreset.CUSTOM, rarity.preset.value)
        assertEquals(Border.FLAT, rarity.inventoryStyle.value.border)
        assertEquals(RarityPreset.SHOWCASE.style!!.fill, rarity.inventoryStyle.value.fill, "the rest of the look stays as it was shown")
    }

    @Test
    fun `rows dim under their switch, the group's switch, and what they depend on`() {
        val byId = rows.associateBy { it.id }
        settings.feature<ProtectorSpawnBeam>().enabled = false
        assertTrue(byId.getValue("beamSections").dimmed)
        settings.feature<ProtectorSpawnBeam>().enabled = true
        assertFalse(byId.getValue("beamSections").dimmed)
        settings.feature<Camera>().enabled = false
        assertTrue(byId.getValue("cameraTilt").dimmed)
        rarity.enabled = false
        assertTrue(byId.getValue("border").dimmed, "a fold's rows dim with the group around it")
        settings.feature<CommandBinds>().enabled = false
        assertTrue(byId.getValue("sequenceGap").dimmed)
        assertFalse(byId.getValue("command-binds").dimmed)
    }

    @Test
    fun `a closed switch sums up its settings`() {
        val slotLock = rows.single { it.id == "slot-lock" } as Switch
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
