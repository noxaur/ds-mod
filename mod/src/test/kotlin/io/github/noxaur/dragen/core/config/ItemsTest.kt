package io.github.noxaur.dragen.core.config

import io.github.noxaur.dragen.core.input.CommandBind
import io.github.noxaur.dragen.core.input.CommandBinds
import io.github.noxaur.dragen.core.input.Where
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemsTest {
    private val a = CommandBind(command = "/a")
    private val b = CommandBind(command = "/b")
    private val c = CommandBind(command = "/c", group = "Dungeons")
    private var list = Grouped(listOf(c, a, b), listOf("Dungeons", "Mining"))
    private val items = Items("binds", "Binds", "Edit them.", Bind("General.binds", { list }, { list = it }), CommandBinds)

    private fun names() = items.entries().map {
        when (it) {
            is Items.Entry.Item -> list.items[it.index].command
            is Items.Entry.Heading -> "# ${it.group}"
            is Items.Entry.Empty -> "(none)"
        }
    }

    @Test
    fun `the list is each heading, then its items or an empty line, and an empty list has no lines`() {
        assertEquals(listOf("# ", "/a", "/b", "# Dungeons", "/c", "# Mining", "(none)"), names())
        items.fold("Dungeons", true)
        assertEquals(listOf("# ", "/a", "/b", "# Dungeons", "# Mining", "(none)"), names())
        list = Grouped()
        assertEquals(emptyList(), items.entries(), "an empty list says what to do instead of showing a heading")
    }

    @Test
    fun `an item of a group that is gone sits in the first`() {
        list = Grouped(listOf(CommandBind(command = "/x", group = "Gone")))
        assertEquals(listOf("# ", "/x"), names())
    }

    @Test
    fun `an item moved under another heading joins that group`() {
        list = Grouped(listOf(a, b, c), listOf("Dungeons", "Mining"))
        val at = items.move(0, 4)
        assertEquals("Dungeons", list.items[at].group)
        assertEquals(listOf("/b", "/c", "/a"), list.items.map { it.command })
        list = Grouped(listOf(a, b, c), listOf("Dungeons", "Mining"))
        items.move(1, 0)
        assertEquals(listOf("/b", "/a", "/c"), list.items.map { it.command }, "nothing goes above the first heading")
        list = Grouped(listOf(a, b, c), listOf("Dungeons", "Mining"))
        val dropped = items.move(0, 6)
        assertEquals("Mining", list.items[dropped].group, "an item dropped on an empty group's line joins it")
    }

    @Test
    fun `an item dropped on a folded heading goes last in it, and the hidden items keep their place`() {
        val d = CommandBind(command = "/d", group = "Dungeons")
        list = Grouped(listOf(a, b, c, d), listOf("Dungeons", "Mining"), folded = listOf("Dungeons"))
        val at = items.move(0, 3)
        assertEquals("Dungeons", list.items[at].group)
        assertEquals(listOf("/b", "/c", "/d", "/a"), list.items.map { it.command })
    }

    @Test
    fun `groups move among themselves, and the first stays first`() {
        items.moveGroup("Dungeons", 6)
        assertEquals(listOf("Mining", "Dungeons"), list.groups)
        items.moveGroup("Dungeons", 0)
        assertEquals(listOf("Dungeons", "Mining"), list.groups, "dropped on the first heading it goes first after it")
        assertFalse(items.canNudge(Items.Entry.Heading(""), 1), "the first group does not move")
    }

    @Test
    fun `a nudge moves a line, unfolds the group it joins, and is off where it would move nothing`() {
        list = Grouped(listOf(a, b, c), listOf("Dungeons"), folded = listOf("Dungeons"))
        assertFalse(items.canNudge(Items.Entry.Item(0), -1), "the first item cannot go above the first heading")
        assertEquals(Items.Entry.Item(1), items.nudge(Items.Entry.Item(0), 1))
        assertEquals(listOf("/b", "/a", "/c"), list.items.map { it.command })
        val joined = items.nudge(Items.Entry.Item(1), 1)
        assertEquals("Dungeons", list.items[(joined as Items.Entry.Item).index].group)
        assertEquals(emptyList(), list.folded, "the group it joined unfolds")
        assertEquals(Items.Entry.Heading("Dungeons"), items.nudge(Items.Entry.Heading("Dungeons"), 1), "a lone named group stays")
    }

    @Test
    fun `a new item goes last in its group, a copy starts off after the original, and a delete takes one`() {
        val at = items.add("Mining")
        assertEquals("Mining", list.items[at].group)
        assertEquals(3, at, "after every item of the groups before it")
        val first = items.add("")
        assertEquals("", list.items[first].group)
        assertEquals(3, first, "after the first group's last item, wherever the list keeps it")
        items.set(0, list.items[0].copy(command = "/z", enabled = true))
        val copy = items.duplicate(0)
        assertEquals("/z", list.items[copy].command)
        assertFalse(list.items[copy].enabled)
        items.delete(copy)
        assertEquals(5, list.items.size)
    }

    @Test
    fun `groups are named once, renamed only to a name no other has, and deleted with their items`() {
        assertEquals("Group 3", items.newGroup())
        assertEquals("Another group is called Mining.", items.rename("Group 3", "Mining"))
        assertEquals("Another group is called General.", items.rename("Group 3", "General"))
        assertEquals("Give the group a name.", items.rename("Group 3", "  "))
        items.fold("Dungeons", true)
        items.switchGroup("Dungeons", false)
        assertNull(items.rename("Dungeons", "Slayers"))
        assertEquals(listOf("Slayers", "Mining", "Group 3"), list.groups)
        assertEquals(listOf("Slayers"), list.folded)
        assertEquals(listOf("Slayers"), list.off)
        assertEquals("Slayers", list.items[0].group, "its items follow it")
        assertEquals(1, items.deleteGroup("Slayers"))
        assertEquals(listOf("Mining", "Group 3"), list.groups)
        assertEquals(emptyList(), list.off)
        assertEquals(0, items.deleteGroup(""), "the first group stays")
    }

    @Test
    fun `a group exported is imported as a new group of the same name, its items off`() {
        items.set(1, a.copy(keys = listOf(70)))
        val text = items.export("")!!
        assertNull(items.export("Mining"), "a group with nothing to export says so")
        assertEquals("Imported" to 2, items.import(text), "the first group's name is never taken")
        assertEquals(listOf(false, false), list.items.filter { it.group == "Imported" }.map { it.enabled })
        items.import(items.export("Dungeons")!!)
        assertEquals("Dungeons 2", list.groups.last(), "a name in use takes a number")
        assertNull(items.import("hello"))
    }

    @Test
    fun `an item clashes with live items on the same keys, and a group that is off sends nothing`() {
        list = Grouped(listOf(a.copy(keys = listOf(70)), b.copy(keys = listOf(70)), c.copy(keys = listOf(70))), listOf("Dungeons"))
        assertEquals(listOf("/b", "/c"), items.clashes(0).map { it.command })
        items.switchGroup("Dungeons", false)
        assertFalse(items.live(2))
        assertEquals(listOf("/b"), items.clashes(0).map { it.command })
        assertEquals(emptyList(), items.clashes(2), "an item that sends nothing clashes with nothing")
    }

    @Test
    fun `a list never shows as changed, sums itself up, and is narrated line by line`() {
        assertFalse(items.changed)
        items.reset()
        assertEquals(3, list.items.size, "nothing resets a list the player built")
        assertEquals("3 binds", items.summary { "" })
        assertEquals("Binds, list, 3 binds, in 3 groups", items.narration({ "" }))
        list = list.copy(items = listOf(a.copy(name = "End", command = "/warp end", keys = listOf(1, 2), where = Where.BOTH)))
        val keys = { k: List<Int>, inOrder: Boolean -> k.joinToString(if (inOrder) " » " else " + ") }
        assertEquals("End, bind, on, End, /warp end, 1 + 2, Both, 2 of 6", items.lineNarration(Items.Entry.Item(0), keys))
        items.switchGroup("Dungeons", false)
        assertEquals("Dungeons, group, off, expanded, 0 binds, 3 of 6", items.lineNarration(Items.Entry.Heading("Dungeons"), keys))
        assertTrue(items.lineNarration(Items.Entry.Empty("Dungeons"), keys).startsWith("No binds in Dungeons"))
    }

    @Test
    fun `a panel is changed when what it edits differs from a fresh one, resets, and sums itself up`() {
        var tabs = listOf("a", "b")
        val panel = Panel("tabs", "Tabs", "Edit them.", Bind("BetterUi.tabs", { tabs }, { tabs = it }), Canvas.MENU_TABS) { "${it.size} tabs" }
        assertFalse(panel.changed)
        tabs = listOf("a")
        assertTrue(panel.changed)
        assertEquals("Tabs, editor, 1 tabs, changed from 2 tabs", panel.narration({ "" }))
        panel.reset()
        assertEquals(listOf("a", "b"), tabs)
    }
}
