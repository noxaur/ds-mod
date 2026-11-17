package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EntityFilterTest {
    private val pets = listOf(Pet.STANDS)
    private fun stand(name: String) = EntitySeen(3, "minecraft:armor_stand", ServerText(ServerSpan(name)))

    @Test
    fun `a filter takes an entity by its type and the start of its name`() {
        assertEquals(true, Pet.STANDS.matches(stand("[Lvl 100] Black Cat")))
        assertEquals(false, Pet.STANDS.matches(stand("Click to talk")))
        assertEquals(false, Pet.STANDS.matches(EntitySeen(3, "minecraft:zombie", ServerText(ServerSpan("[Lvl 100] Black Cat")))))
    }

    @Test
    fun `a filter must narrow the entities it reads`() {
        assertFailsWith<IllegalArgumentException> { EntityFilter() }
    }

    @Test
    fun `a reader hears an entity's events while it reads it, and a rename can bring it in or take it out`() {
        val cat = stand("[Lvl 100] Black Cat")
        val sign = stand("Click to talk")
        assertEquals(cat, pets.view(null, cat, cat))
        assertNull(pets.view(null, sign, sign))
        val renamed = EntityRenamed(3, ServerText(ServerSpan("[Lvl 100] Black Cat")))
        assertEquals(renamed, pets.view(cat, cat, renamed))
        assertEquals(cat, pets.view(sign, cat, renamed))
        assertEquals(EntityGone(3), pets.view(cat, sign, EntityRenamed(3, sign.name)))
        assertEquals(EntityGone(3), pets.view(cat, null, EntityGone(3)))
        assertNull(pets.view(sign, null, EntityGone(3)))
    }

    @Test
    fun `the table keeps each entity with its latest name, until it is gone`() {
        val table = EntityTable()
        val cat = stand("[Lvl 99] Black Cat")
        assertEquals(true, table.apply(cat))
        val renamed = EntityRenamed(3, ServerText(ServerSpan("[Lvl 100] Black Cat")))
        table.apply(renamed)
        assertEquals(cat, table.before)
        assertEquals(listOf(cat.copy(name = renamed.name)), table.values.toList())
        table.apply(EntityGone(3))
        assertNull(table.after)
        assertEquals(emptyList(), table.values.toList())
        assertEquals(false, table.apply(WorldChanged))
    }
}
