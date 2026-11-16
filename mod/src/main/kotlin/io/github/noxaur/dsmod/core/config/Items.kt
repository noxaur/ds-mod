package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Narration

/**
 * A list the player builds under groups, as [Items] edits it: the [items] in the order the list shows them, the
 * named [groups] after the first, which has no name, the groups [folded] away, and the groups switched [off].
 */
data class Grouped<T>(
    val items: List<T> = emptyList(),
    val groups: List<String> = emptyList(),
    val folded: List<String> = emptyList(),
    val off: List<String> = emptyList(),
) {
    /** The list with [items], and [change] made to every list of group names, which stay together. */
    fun withItems(items: List<T>, change: (List<String>) -> List<String>) = Grouped(items, change(groups), change(folded), change(off))
}

/**
 * One part of an item that its line shows and edits, such as a bind's switch, its name or its keys. A line shows
 * [Items.cells] across the pane's width, and its details open the [Items.details] under it.
 */
sealed class Cell<T>(val name: String) {
    /** On or off, drawn as a switch. */
    class Check<T>(name: String, val get: (T) -> Boolean, val set: (T, Boolean) -> T) : Cell<T>(name)

    /** Text the player types, [width] wide or, at 0, sharing the room left. [hint] shows while it is empty. */
    class Text<T>(name: String, val get: (T) -> String, val set: (T, String) -> T, val hint: String, val max: Int, val width: Int = 0) : Cell<T>(name)

    /** Up to [max] keys, held together or, where [inOrder] says so, pressed in turn. */
    class Keys<T>(name: String, val get: (T) -> List<Int>, val set: (T, List<Int>) -> T, val max: Int, val inOrder: (T) -> Boolean, val width: Int) :
        Cell<T>(name)

    /** One of a few [values], stepped by a cycle button [width] wide, each shown as its [label]. */
    class Pick<T, V>(name: String, val get: (T) -> V, val set: (T, V) -> T, val values: List<V>, val label: (V) -> String, val width: Int) : Cell<T>(name) {
        fun step(item: T, by: Int) = set(item, values[Math.floorMod(values.indexOf(get(item)) + by, values.size)])

        fun shown(item: T) = label(get(item))
    }

    /** A whole number from [min] to [max], stepped by a minus and a plus. */
    class Count<T>(name: String, val get: (T) -> Int, val set: (T, Int) -> T, val min: Int, val max: Int) : Cell<T>(name) {
        fun step(item: T, by: Int) = set(item, (get(item) + by).coerceIn(min, max))
    }
}

/** What an [Items] list holds: how an item is grouped, made, named and shared, and the cells its line shows. */
interface ItemKind<T> {
    /** What one item is called, such as "bind". */
    val noun: String

    /** The name the first group shows, which it has no name of its own for. */
    val first: String

    /** The longest group name. */
    val groupLength: Int

    /** The cells an item's line shows, the first of them its switch. */
    val cells: List<Cell<T>>

    /** The cells that open under an item's line. */
    val details: List<Cell<T>>

    fun group(item: T): String

    fun regroup(item: T, group: String): T

    /** A new item in [group]. */
    fun blank(group: String): T

    /** What tells the item apart in a line the screen says, such as its name or command. */
    fun title(item: T): String

    /** Whether the item is on by its own switch. */
    fun on(item: T): Boolean

    /** [item] switched [on] or off. */
    fun switched(item: T, on: Boolean): T

    /** The items of [live] that [item] would fire with, so neither can be told apart by its keys. */
    fun clashes(item: T, live: List<T>): List<T>

    /** [items] as text another player pastes with [decode], under the name [group]. */
    fun encode(group: String, items: List<T>): String

    /** The group's name and its items, switched off, or null when [text] holds none. */
    fun decode(text: String): Pair<String, List<T>>?
}

/**
 * A list the player builds, edited in the settings: items under groups that fold, each a line of [type]'s cells.
 * Its value is the whole [Grouped] list, and each rule below returns the list it makes, so the face only lays out
 * and draws. A list the player built has no default worth going back to, so it never shows as changed, and a
 * delete asks for a second press instead.
 */
class Items<T>(id: String, name: String, description: String, bind: Bind<Grouped<T>>, val type: ItemKind<T>) :
    Valued<Grouped<T>>(id, name, description, Kind.ITEMS, bind) {
    /** One line of the list: a group's heading, an item by its place in [Grouped.items], or the line of a group with none. */
    sealed interface Entry {
        data class Heading(val group: String) : Entry

        data class Item(val index: Int) : Entry

        data class Empty(val group: String) : Entry
    }

    /** Every group, the first one included, which has no name. */
    val groups get() = listOf("") + value.groups

    fun groupName(group: String) = group.ifEmpty { type.first }

    /** Where [item] is listed: its group, or the first when its group is gone. */
    fun home(item: T, groups: List<String> = this.groups) = type.group(item).takeIf { it in groups } ?: ""

    /**
     * Each group as its heading then its items, or an empty line when it has none, with a folded group's items left
     * out. An empty list has no lines, so it can say what to do instead.
     */
    fun entries(list: Grouped<T> = value): List<Entry> {
        if (list.items.isEmpty() && list.groups.isEmpty()) return emptyList()
        val groups = listOf("") + list.groups
        return groups.flatMap { group ->
            val mine = indicesIn(group, list, groups)
            listOf(Entry.Heading(group)) + when {
                group in list.folded -> emptyList()
                mine.isEmpty() -> listOf(Entry.Empty(group))
                else -> mine.map(Entry::Item)
            }
        }
    }

    private fun indicesIn(group: String, list: Grouped<T> = value, groups: List<String> = this.groups) =
        list.items.indices.filter { home(list.items[it], groups) == group }

    fun count(group: String) = value.items.count { home(it) == group }

    /** Whether the item at [index] sends: on, in a group that is on. */
    fun live(index: Int) = value.items[index].let { type.on(it) && home(it) !in value.off }

    /** The other items that the one at [index] fires with, while both are live. */
    fun clashes(index: Int): List<T> {
        if (!live(index)) return emptyList()
        val list = value.items
        val live = list.indices.filter { it != index && live(it) }.map { list[it] }
        return type.clashes(list[index], live)
    }

    fun set(index: Int, item: T) {
        editItems { this[index] = item }
    }

    private inline fun editItems(edit: MutableList<T>.() -> Unit) {
        value = value.copy(items = value.items.toMutableList().apply(edit))
    }

    /**
     * Moves the item at [index] to line [to] of the list, where it joins the group whose heading is above it. A
     * folded group's items keep their order, and one dropped on its heading goes last in it. Returns where it went.
     */
    fun move(index: Int, to: Int): Int {
        val list = value
        val moving = list.items[index]
        val shown = entries(list).filterNot { it == Entry.Item(index) }.toMutableList<Any>()
        val at = to.coerceIn(1, shown.size)
        val heading = shown.subList(0, at).last { it is Entry.Heading } as Entry.Heading
        val moved = if (home(moving) == heading.group) moving else type.regroup(moving, heading.group)
        shown.add(at, MOVED)
        val groups = groups
        val order = ArrayList<T>()
        var landed = -1
        for (group in groups) {
            if (group in list.folded) {
                indicesIn(group, list, groups).filter { it != index }.mapTo(order) { list.items[it] }
                if (heading.group == group) {
                    landed = order.size
                    order += moved
                }
                continue
            }
            val start = shown.indexOf(Entry.Heading(group))
            for (line in shown.drop(start + 1).takeWhile { it !is Entry.Heading }) {
                when {
                    line === MOVED -> {
                        landed = order.size
                        order += moved
                    }
                    line is Entry.Item -> order += list.items[line.index]
                }
            }
        }
        value = list.copy(items = order)
        return landed
    }

    /** Moves [group] to the place of the group whose heading is at or above line [to]. The first group stays first. */
    fun moveGroup(group: String, to: Int) {
        val lines = entries()
        val over = lines.subList(0, (to + 1).coerceIn(1, lines.size)).last { it is Entry.Heading } as Entry.Heading
        if (over.group == group) return
        val order = value.groups
        place(group, if (over.group.isEmpty()) 0 else order.indexOf(over.group))
    }

    private fun place(group: String, to: Int) {
        val rest = value.groups - group
        value = value.copy(groups = rest.toMutableList().apply { add(to.coerceIn(0, rest.size), group) })
    }

    /** Whether [line] can move [by] a line: an item past any line but the first, a named group past a neighbor. */
    fun canNudge(line: Entry, by: Int): Boolean {
        val lines = entries()
        return when (line) {
            is Entry.Item -> lines.indexOf(line).let { if (by < 0) it > 1 else it in 0 until lines.lastIndex }
            is Entry.Heading -> line.group.isNotEmpty() && value.groups.indexOf(line.group) + by in value.groups.indices
            is Entry.Empty -> false
        }
    }

    /**
     * Moves [line] a line [by], as Alt with Up or Down does, and returns the line it is on after. An item moved into
     * a folded group unfolds it, so it stays in sight.
     */
    fun nudge(line: Entry, by: Int): Entry {
        if (!canNudge(line, by)) return line
        return when (line) {
            is Entry.Item -> {
                val at = move(line.index, entries().indexOf(line) + by)
                fold(home(value.items[at]), false)
                Entry.Item(at)
            }
            is Entry.Heading -> line.also { place(it.group, value.groups.indexOf(it.group) + by) }
            is Entry.Empty -> line
        }
    }

    /** Adds a new item last in [group], unfolded, and returns its place. */
    fun add(group: String): Int {
        fold(group, false)
        val fresh = type.blank(group)
        val list = value.items
        val last = indicesIn(group).lastOrNull()
        val at = if (last != null) {
            last + 1
        } else {
            // A group with none goes after every item of the groups before it.
            val before = groups.takeWhile { it != group }.toSet()
            list.indices.lastOrNull { home(list[it]) in before }?.plus(1) ?: 0
        }
        editItems { add(at, fresh) }
        return at
    }

    /** Puts a copy of the item at [index] after it, switched off so it cannot clash before the player reads it. */
    fun duplicate(index: Int): Int {
        editItems { add(index + 1, type.switched(get(index), false)) }
        return index + 1
    }

    fun delete(index: Int) {
        value = value.copy(items = value.items.filterIndexed { i, _ -> i != index })
    }

    fun fold(group: String, folded: Boolean) {
        val list = value
        if (folded == group in list.folded) return
        value = list.copy(folded = if (folded) list.folded + group else list.folded - group)
    }

    fun switchGroup(group: String, on: Boolean) {
        val list = value
        if (on == group !in list.off) return
        value = list.copy(off = if (on) list.off - group else list.off + group)
    }

    /** Adds a group with a name no other has, and returns it. */
    fun newGroup(): String {
        var n = groups.size
        while ("Group $n" in groups) n++
        val name = "Group $n"
        value = value.copy(groups = value.groups + name)
        return name
    }

    /** Why [typed] cannot name [group], or null once it is renamed, which a name no other group has does. */
    fun rename(group: String, typed: String): String? {
        val name = typed.trim()
        if (group.isEmpty() || name == group) return null
        if (name.isEmpty()) return "Give the group a name."
        if (name == type.first || name in groups) return "Another group is called $name."
        fun List<String>.renamed() = map { if (it == group) name else it }
        val list = value
        value = list.withItems(list.items.map { if (type.group(it) == group) type.regroup(it, name) else it }) { it.renamed() }
        return null
    }

    /** Deletes a named [group] and its items, and returns how many items went with it. */
    fun deleteGroup(group: String): Int {
        if (group.isEmpty()) return 0
        val list = value
        val gone = list.items.count { type.group(it) == group }
        value = list.withItems(list.items.filter { type.group(it) != group }) { it - group }
        return gone
    }

    /** The items of [group] as text to paste with [import], or null when it has none. */
    fun export(group: String): String? {
        val mine = value.items.filter { home(it) == group }
        return if (mine.isEmpty()) null else type.encode(groupName(group), mine)
    }

    /** Adds the group in [text] as a new group, after the rest, and returns its name and how many items it brought. */
    fun import(text: String): Pair<String, Int>? {
        val (shared, items) = type.decode(text)?.takeIf { it.second.isNotEmpty() } ?: return null
        val base = shared.takeUnless { it.isEmpty() || it == type.first } ?: "Imported"
        var name = base
        var n = 2
        while (name in groups) name = "$base ${n++}"
        val list = value
        value = list.copy(items = list.items + items.map { type.regroup(it, name) }, groups = list.groups + name)
        return name to items.size
    }

    /** "1 bind" or "3 binds". */
    fun plural(count: Int) = if (count == 1) "1 ${type.noun}" else "$count ${type.noun}s"

    // A list has no default to go back to, so it shows no changed marker and nothing resets it whole.
    override val changed get() = false

    override fun reset() = Unit

    override fun summary(keys: (Int) -> String) = if (value.items.isEmpty()) "None" else plural(value.items.size)

    override fun spoken(keys: (Int) -> String, open: Boolean): String {
        val list = value
        if (list.items.isEmpty()) return "empty"
        return Narration.join(plural(list.items.size), if (list.groups.isEmpty()) null else "in ${list.groups.size + 1} groups")
    }

    /** What the narrator says for [line]: an item's title, switch and keys, or a group's name, switch and count, and its place. */
    fun lineNarration(line: Entry, keys: (List<Int>, Boolean) -> String): String {
        val lines = entries()
        val place = Narration.of(lines.indexOf(line), lines.size)
        return when (line) {
            is Entry.Item -> {
                val item = value.items[line.index]
                val parts = type.cells.drop(1).map { cell ->
                    when (cell) {
                        is Cell.Text -> cell.get(item).ifBlank { null }
                        is Cell.Keys -> keys(cell.get(item), cell.inOrder(item))
                        is Cell.Pick<T, *> -> cell.shown(item)
                        is Cell.Check -> null
                        is Cell.Count -> cell.get(item).toString()
                    }
                }
                Narration.join(type.title(item), type.noun, if (type.on(item)) "on" else "off", *parts.toTypedArray(), place)
            }
            is Entry.Heading -> Narration.join(
                groupName(line.group),
                "group",
                if (line.group in value.off) "off" else "on",
                if (line.group in value.folded) "collapsed" else "expanded",
                plural(count(line.group)),
                place,
            )
            is Entry.Empty -> Narration.join("No ${type.noun}s in ${groupName(line.group)}", place)
        }
    }

    override val labels: List<String> get() = emptyList()

    private companion object {
        val MOVED = Any()
    }
}

/** Which of ds-mod's editors a [Panel] or a [Preview] draws, which the edge gives a face. */
enum class Canvas { HUD_LAYOUT, MENU_TABS, PLAYER_MODEL }

/**
 * A picture of what the rows around it do, such as the player's model with Player Model's settings on it. It holds
 * no value, so it has no changed marker and no reset, and the edge draws it on its [canvas] under the row's name.
 */
class Preview(id: String, name: String, description: String, val canvas: Canvas) : Setting(id, name, description, Kind.PREVIEW)

/**
 * An editor that is a picture rather than rows, such as the HUD's layout: it fills the row's width under its name,
 * and the edge draws it on its [canvas]. Its value is the whole of what it edits, so the changed marker and reset
 * work on it as on any row, and [describe] sums it up for the summary and the narrator.
 */
class Panel<T>(id: String, name: String, description: String, bind: Bind<T>, val canvas: Canvas, private val describe: (T) -> String) :
    Valued<T>(id, name, description, Kind.PANEL, bind) {
    override fun show(v: T, keys: (Int) -> String) = describe(v)

    override fun spoken(keys: (Int) -> String, open: Boolean) = describe(value)
}
