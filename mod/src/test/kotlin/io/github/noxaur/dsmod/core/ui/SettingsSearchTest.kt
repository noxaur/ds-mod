package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.text.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {
    private class Values {
        var a = true
        var b = true
        var c = true
        var d = true
        var e = true
        var f = 0
        var mode = Mode.HOLD
    }

    private enum class Mode(private val label: String) {
        HOLD("Hold"),
        TOGGLE("Toggle"),
        ;

        override fun toString() = label
    }

    private val v = Values()
    private val sections = Sections().apply {
        section("The End", Role.EPIC) {
            group("Dragon") {
                switch(v::a, "Dragon Timer", "Show when the egg returns.", id = "timer")
                switch(v::b, "Protect Eyes", "Ignore a second click.", id = "eyes")
            }
            group("Drops") { switch(v::c, "Zealot Tracker", "Count your Summoning Eyes.", id = "zealot") }
        }
        section("Camera", Role.ACTION) {
            group("Free Look", switch = toggle(v::d, "Free Look", "Look around.")) {
                keys(v::f, "Key", "The key.", id = "key")
                choice(v::mode, "Mode", "Hold or press.", id = "mode")
            }
        }
        section("Chat", Role.WARNING) {
            group(null) {
                switch(v::e, "Copy Chat", "Copy messages.", id = "copy") {
                    choice(v::mode, "Copy Mode", "Pick what a copy takes.", id = "copyMode")
                }
            }
        }
    }.sections

    private fun ids(query: String) = SettingsSearch.hits(sections, query).flatMap { hit -> hit.rows.map { it.id } }

    @Test
    fun `a query finds rows by name and by description, ignoring case`() {
        assertEquals(listOf("timer"), ids("TIMER"))
        assertEquals(listOf("eyes", "zealot"), ids("eyes"), "a name and a description")
    }

    @Test
    fun `every word must match, in any order`() {
        assertEquals(listOf("zealot"), ids("eyes count"))
        assertEquals(emptyList(), ids("eyes timer"))
    }

    @Test
    fun `a group's heading or switch finds all its rows`() {
        assertEquals(listOf("key", "mode"), ids("free look"))
        assertEquals(listOf("timer", "eyes"), ids("dragon"))
        assertEquals(listOf("key"), ids("free key"), "a heading word and a row word")
    }

    @Test
    fun `a choice is found by one of its values`() {
        assertEquals(listOf("mode", "copy"), ids("toggle"))
    }

    @Test
    fun `a hit under a row finds the row and opens it, and a heading opens nothing`() {
        val hits = SettingsSearch.hits(sections, "toggle")
        assertEquals(setOf("copy"), hits.single { it.group.name == null }.open)
        assertEquals(emptySet(), SettingsSearch.hits(sections, "copy chat").single().open, "the parent matched by itself")
        assertEquals(setOf("copy"), SettingsSearch.hits(sections, "copy pick").single().open, "a word of the parent and one of the child")
    }

    @Test
    fun `a blank query finds nothing`() {
        assertEquals(emptyList(), ids("   "))
    }

    @Test
    fun `hits keep their section and group, and count per section`() {
        val hits = SettingsSearch.hits(sections, "o")
        assertEquals(listOf(0 to "Dragon", 0 to "Drops", 1 to "Free Look", 2 to null), hits.map { it.section to it.group.name })
        assertEquals(listOf(3, 2, 1), SettingsSearch.counts(hits, sections.size).toList())
    }

    @Test
    fun `the settings tree finds a feature by its own name, and an option inside a fold`() {
        val tree = SettingsTree.sections(DsModConfig())
        assertTrue(SettingsSearch.hits(tree, "rarity").any { hit -> hit.group.name == "Rarity Marks" })
        val halo = SettingsSearch.hits(tree, "halo").single()
        assertEquals(setOf("customLook"), halo.open)
    }

    private fun marked(text: String, query: String) =
        SettingsSearch.pieces(text, SettingsSearch.words(query)).joinToString("") { if (it.match) "[${it.text}]" else it.text }

    @Test
    fun `each word of the query is marked wherever it occurs, ignoring case`() {
        assertEquals("[Eye]s and [eye]s", marked("Eyes and eyes", "eye"))
        assertEquals("Count your [Summoning] [Eye]s.", marked("Count your Summoning Eyes.", "eye summoning"))
    }

    @Test
    fun `marks that touch or overlap are one, and no query marks nothing`() {
        assertEquals("[Dragon] Timer", marked("Dragon Timer", "drag agon"))
        assertEquals("[Zealot] Tracker", marked("Zealot Tracker", "zeal lot"))
        assertEquals("Dragon Timer", marked("Dragon Timer", "  "))
        assertEquals(listOf(Piece("Dragon Timer", false)), SettingsSearch.pieces("Dragon Timer", listOf("zz")))
    }

    @Test
    fun `a word that names an entry of an ordered list opens the list, and its name alone does not`() {
        val tree = SettingsTree.sections(DsModConfig())
        val open = SettingsSearch.hits(tree, "boots").flatMap { it.open }
        assertTrue("equipmentPieces" in open, "$open")
        assertTrue(SettingsSearch.hits(tree, "equipment pieces").none { "equipmentPieces" in it.open })
    }
}
