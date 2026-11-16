package io.github.noxaur.dsmod.core.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.RarityPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tree is the schema of the settings, so these keep it one system: every saved field has one row, worded alike. */
class SettingsTreeTest {
    private val config = DsModConfig()
    private val tree = SettingsTree.sections(config)
    private val rows = tree.flatMap { it.walk() }

    /** `Class.field` of every field of `DsModConfig` that Gson saves. */
    private val saved = DsModConfig::class.java.declaredFields.filter { it.isAnnotationPresent(Expose::class.java) }.flatMap { category ->
        category.type.declaredFields.filter { it.isAnnotationPresent(Expose::class.java) }.map { "${category.type.simpleName}.${it.name}" }
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
    fun `every description fits the settings screen at full size, as MoulConfig's must`() {
        val long = rows.filter { it.description.length > Setting.DESCRIPTION_MAX }.map { "${it.name}: ${it.description}" }
        assertEquals(emptyList(), long, "Cut each description to at most ${Setting.DESCRIPTION_MAX} characters. The player docs hold the rest: $long\n")
    }

    @Test
    fun `a row that is a MoulConfig option says what the option says, where that is one sentence`() {
        val options = DsModConfig::class.java.declaredClasses.flatMap { category ->
            category.declaredFields.mapNotNull { f -> f.getAnnotation(ConfigOption::class.java)?.let { "${category.simpleName}.${f.name}" to it.desc } }
        }.toMap()
        val drift = rows.filterIsInstance<Valued<*>>().mapNotNull { row ->
            options[row.field]?.takeIf { !Regex("""[.!?]\s""").containsMatchIn(it) && it != row.description }?.let { "${row.name}: \"${row.description}\", not \"$it\"" }
        }
        assertEquals(emptyList(), drift, "Give the row its @ConfigOption's description: $drift\n")
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
        config.end.bossBeacon = false
        assertTrue(byId.getValue("beamSections").dimmed)
        config.end.bossBeacon = true
        assertFalse(byId.getValue("beamSections").dimmed)
        config.general.camera = false
        assertTrue(byId.getValue("cameraTilt").dimmed)
        config.inventory.rarityMarks = false
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
