package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of each kind of row, which the edge's faces only draw. */
class SettingsTest {
    private class Values {
        var on = true
        var distance = 4f
        var height = 0f
        var gap = 250f
        var tier = Tier.EPIC
        var key = 76
        var spans = listOf(Section(175, 200))
        var other = false
        var pieces = mutableListOf(Piece.HELMET, Piece.BOOTS, Piece.PET)
    }

    private enum class Tier { RARE, EPIC, RNGESUS }

    private enum class Piece(private val label: String) {
        HELMET("Helmet"),
        CHESTPLATE("Chestplate"),
        BOOTS("Boots"),
        PET("Pet"),
        ;

        override fun toString() = label
    }

    private val v = Values()
    private val rows = Rows().apply {
        switch(v::on, "Feature", "Turn it on.") {
            number(v::distance, "Distance", "Set it.", 1f..50f, step = 0.5f)
            keys(v::key, "Key", "Pick it.")
        }
        number(v::height, "Height", "Set it.", -2f..3f, step = 0.1f)
        number(v::gap, "Gap", "Set it.", 50f..1000f, step = 50f, unit = " ms")
        choice(v::tier, "Tier", "Pick it.")
        span(Bind.of(v::spans), "Timing", "Pick it.", max = 200)
        order(v::pieces, "Pieces", "Pick them.")
        pick(Bind("Values.other", v::other::get, v::other::set), "Look", "Pick it.", listOf(false, true)) { Look.Slots(io.github.noxaur.dsmod.core.container.SlotStyle(), if (it) 3 else 1) }
    }.rows.also { SettingsTree.link(it, null) }
    private val byName = rows.flatMap { it.walk().toList() }.associateBy { it.name }

    private fun number(name: String) = byName.getValue(name) as Number

    @Test
    fun `a number clamps, keeps to its steps from the minimum, and has the step's decimals`() {
        val distance = number("Distance")
        distance.value = 60f
        assertEquals(50f, v.distance)
        distance.value = 4.3f
        assertEquals(4.5f, v.distance)
        val height = number("Height")
        height.value = 0.3f
        assertEquals(0.3f, v.height, "a tenth stays a tenth, not 0.30000001")
        assertEquals(1, height.decimals)
        assertEquals(0, number("Gap").decimals)
        assertEquals("0.3", height.format())
    }

    @Test
    fun `a number steps, by ten with Shift, and stops at its ends`() {
        val gap = number("Gap")
        gap.step(1)
        assertEquals(300f, v.gap)
        gap.step(-10)
        assertEquals(50f, v.gap)
    }

    @Test
    fun `a number reads what is typed, with or without its unit, and refuses what is not a number`() {
        val gap = number("Gap")
        assertEquals(450f, gap.parse("440 ms"))
        assertEquals(1000f, gap.parse("5000"))
        assertNull(gap.parse("soon"))
        assertEquals(-1.5f, number("Height").parse("-1,5"))
    }

    @Test
    fun `a signed number fills from zero, and a press along the track lands on a step`() {
        val height = number("Height")
        assertEquals(0f, height.zero)
        assertEquals(1f, number("Distance").zero)
        height.at(1.0)
        assertEquals(3f, v.height)
        height.at(0.5)
        assertEquals(0.5f, v.height)
        assertEquals(0.5f, height.fraction(0.5f))
    }

    @Test
    fun `a choice steps both ways and wraps, and search knows its values`() {
        @Suppress("UNCHECKED_CAST")
        val tier = byName.getValue("Tier") as Choice<Tier>
        tier.step(1)
        assertEquals(Tier.RNGESUS, v.tier)
        tier.step(1)
        assertEquals(Tier.RARE, v.tier)
        tier.step(-1)
        assertEquals(Tier.RNGESUS, v.tier)
        assertTrue("rngesus" in tier.searchText)
    }

    @Test
    fun `a changed row shows it, resets to its default, and names the default`() {
        val distance = number("Distance")
        assertFalse(distance.changed)
        distance.value = 10f
        assertTrue(distance.changed)
        assertEquals("4.0", distance.default { "" })
        distance.reset()
        assertEquals(4f, v.distance)
        val key = byName.getValue("Key") as KeyBind
        key.clear()
        assertEquals(KeyBind.NONE, v.key)
        assertTrue(key.changed)
    }

    @Test
    fun `each kind sums itself up for a closed parent`() {
        val names = { k: Int -> if (k == 76) "L" else "None" }
        assertEquals("4.0, L", (byName.getValue("Feature") as Switch).closed(names))
        assertEquals("250 ms", byName.getValue("Gap").summary(names))
        assertEquals("175–200", byName.getValue("Timing").summary(names))
        v.spans = emptyList()
        assertEquals("Never", byName.getValue("Timing").summary(names))
        assertNull(byName.getValue("Feature").summary(names), "a switch leaves itself out")
    }

    @Test
    fun `rows under an off switch dim and stay editable`() {
        v.on = false
        assertTrue(number("Distance").dimmed)
        number("Distance").step(1)
        assertEquals(4.5f, v.distance)
        assertFalse(number("Height").dimmed)
    }

    @Test
    fun `a fold is changed when a row in it is, and resets them all`() {
        val fold = Rows().apply { fold("f", "Fold", "More.") { number(v::height, "Height", "Set it.", -2f..3f, step = 0.1f) } }.rows.single()
        assertFalse(fold.changed)
        v.height = 1f
        assertTrue(fold.changed)
        fold.reset()
        assertEquals(0f, v.height)
    }

    @Test
    fun `the narrator hears each kind's name, kind and value`() {
        val keys = { code: Int -> if (code == 76) "L" else "K" }
        val switch = byName.getValue("Feature")
        assertEquals("Feature, switch, on, collapsed", switch.narration(keys))
        assertEquals("Feature, switch, on, expanded", switch.narration(keys, open = true))
        assertEquals("Distance, number, 4.0", byName.getValue("Distance").narration(keys))
        assertEquals("Gap, number, 250 ms", byName.getValue("Gap").narration(keys))
        assertEquals("Tier, choice, EPIC, 2 of 3", byName.getValue("Tier").narration(keys))
        assertEquals("Look, pick, false, 1 of 2", byName.getValue("Look").narration(keys))
        assertEquals("Key, key bind, L", byName.getValue("Key").narration(keys))
        assertEquals("Timing, span, 175 to 200", byName.getValue("Timing").narration(keys))
        val more = Rows().apply {
            fold("f", "Fold", "More.") { switch(v::other, "Other", "Turn it on.") }
            action("moveHud", "Move HUD", "Drag a block.", button = "Move") {}
        }.rows
        assertEquals("Fold, fold, collapsed", more[0].narration(keys))
        assertEquals("Move HUD, button, Move", more[1].narration(keys))
    }

    @Test
    fun `the narrator hears that a row is changed, and which switch dims it`() {
        val keys = { _: Int -> "K" }
        number("Distance").value = 7.5f
        assertEquals("Distance, number, 7.5, changed from 4.0", byName.getValue("Distance").narration(keys))
        v.on = false
        assertEquals("Distance, number, 7.5, changed from 4.0, dimmed, Feature is off", byName.getValue("Distance").narration(keys))
        assertEquals("Feature, switch, off, collapsed, changed from On", byName.getValue("Feature").narration(keys))
        val fold = Rows().apply { fold("f", "Fold", "More.") { number(v::height, "Height", "Set it.", -2f..3f, step = 0.1f) } }.rows.single()
        v.height = 1f
        assertEquals("Fold, fold, expanded, changed", fold.narration(keys, open = true), "a fold has no default of its own to name")
    }

    @Test
    fun `a row names the switch that dims it, the nearest first`() {
        v.on = false
        assertEquals("Feature", number("Distance").dimmedBy?.name)
        assertNull(number("Height").dimmedBy)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pieces() = byName.getValue("Pieces") as Order<Piece>

    @Test
    fun `an ordered list shows the shown entries in their order, then the hidden ones`() {
        val pieces = pieces()
        assertEquals(listOf(Piece.HELMET, Piece.BOOTS, Piece.PET, Piece.CHESTPLATE), pieces.entries)
        assertEquals(listOf(true, true, true, false), pieces.entries.indices.map(pieces::shown))
        assertEquals("Helmet, Boots, Pet", pieces.summary { "" })
        assertTrue("chestplate" in pieces.searchText, "search knows the hidden entries too")
    }

    @Test
    fun `an entry hides and shows again in its place, and the last one stays shown`() {
        val pieces = pieces()
        pieces.move(3, 1)
        assertEquals(listOf(Piece.HELMET, Piece.CHESTPLATE, Piece.BOOTS, Piece.PET), pieces.entries, "a hidden entry moves too")
        assertEquals(listOf(Piece.HELMET, Piece.BOOTS, Piece.PET), v.pieces, "and the value keeps only the shown ones")
        pieces.switch(1)
        assertEquals(listOf(Piece.HELMET, Piece.CHESTPLATE, Piece.BOOTS, Piece.PET), v.pieces, "it shows where it sits")
        pieces.switch(0)
        assertEquals(listOf(Piece.CHESTPLATE, Piece.BOOTS, Piece.PET), v.pieces)
        assertEquals(Piece.HELMET, pieces.entries[0], "a hidden entry keeps its place")
        pieces.switch(0)
        assertEquals(Piece.HELMET, v.pieces.first(), "and shows in it again")
        for (i in 1..3) pieces.switch(i)
        assertEquals(listOf(Piece.HELMET), v.pieces)
        assertFalse(pieces.canSwitch(0))
        pieces.switch(0)
        assertEquals(listOf(Piece.HELMET), v.pieces, "the last shown entry cannot hide")
    }

    @Test
    fun `an entry moves by drag or key, stops at the ends, and Escape puts the list back`() {
        val pieces = pieces()
        val before = pieces.entries
        val shown = pieces.value
        assertEquals(2, pieces.move(0, 2), "a drag drops it on the line under the mouse")
        assertEquals(listOf(Piece.BOOTS, Piece.PET, Piece.HELMET), v.pieces)
        assertEquals(3, pieces.move(2, 3 + 1), "a key past the end stays at the end")
        assertEquals(0, pieces.move(0, -1))
        pieces.restore(before, shown)
        assertEquals(before, pieces.entries)
        assertEquals(shown, v.pieces)
    }

    @Test
    fun `an ordered list is changed when its value is, resets, and is narrated`() {
        val pieces = pieces()
        assertFalse(pieces.changed)
        pieces.move(0, 1)
        assertTrue(pieces.changed)
        assertEquals("Pieces, list, Boots, Helmet, Pet, 3 of 4 shown, collapsed, changed from Helmet, Boots, Pet", pieces.narration({ "" }))
        assertEquals("Boots, switch, on, 1 of 4", pieces.entryNarration(0))
        assertEquals("Chestplate, switch, off, 4 of 4", pieces.entryNarration(3))
        pieces.reset()
        assertEquals(listOf(Piece.HELMET, Piece.BOOTS, Piece.PET), v.pieces)
        assertEquals(Piece.HELMET, pieces.entries[0], "a reset rebuilds the list around the value")
        v.pieces = mutableListOf(Piece.PET, Piece.PET, Piece.BOOTS)
        pieces.value = v.pieces
        assertEquals(listOf(Piece.PET, Piece.BOOTS), v.pieces, "a value holds each entry once")
    }

    @Test
    fun `a row reaches the field it is bound to`() {
        assertEquals("Values.distance", (number("Distance")).field)
        assertEquals("Values.other", (byName.getValue("Look") as Valued<*>).field)
    }
}
