package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Colors
import io.github.noxaur.dsmod.core.ui.Narration

data class Grouped<T>(
    val items: List<T> = emptyList(),
    val groups: List<String> = emptyList(),
    val folded: List<String> = emptyList(),
    val off: List<String> = emptyList(),
) {
    fun withItems(items: List<T>, change: (List<String>) -> List<String>) = Grouped(items, change(groups), change(folded), change(off))

    companion object {
        // @keep A list row saves its items, its groups, the folded groups and the groups that are off in four slots.
        fun <T> bind(items: Slot<List<T>>, groups: Slot<List<String>>, folded: Slot<List<String>>, off: Slot<List<String>>) = Bind(
            items.bind.field,
            { Grouped(items.value, groups.value, folded.value, off.value) },
            {
                items.value = it.items
                groups.value = it.groups
                folded.value = it.folded
                off.value = it.off
            },
        )
    }
}

sealed class Cell<T>(val name: String) {
    class Check<T>(name: String, val get: (T) -> Boolean, val set: (T, Boolean) -> T) : Cell<T>(name)

    class Text<T>(name: String, val get: (T) -> String, val set: (T, String) -> T, val hint: String, val max: Int, val width: Int = 0) : Cell<T>(name)

    class Keys<T>(name: String, val get: (T) -> List<Int>, val set: (T, List<Int>) -> T, val max: Int, val inOrder: (T) -> Boolean, val width: Int) :
        Cell<T>(name)

    class Pick<T, V>(name: String, val get: (T) -> V, val set: (T, V) -> T, val values: List<V>, val label: (V) -> String, val width: Int) : Cell<T>(name) {
        fun step(item: T, by: Int) = set(item, values[Math.floorMod(values.indexOf(get(item)) + by, values.size)])

        fun shown(item: T) = label(get(item))
    }

    class Count<T>(name: String, val get: (T) -> Int, val set: (T, Int) -> T, val min: Int, val max: Int) : Cell<T>(name) {
        fun step(item: T, by: Int) = set(item, (get(item) + by).coerceIn(min, max))
    }

    // @keep An ARGB color, picked in the same panel as a Color row.
    class Color<T>(name: String, val get: (T) -> Int, val set: (T, Int) -> T) : Cell<T>(name)
}

interface ItemKind<T> {
    val noun: String

    // @keep What a live item does, after "none of its binds", as "send".
    val verb: String

    val first: String

    val groupLength: Int

    val cells: List<Cell<T>>

    val details: List<Cell<T>>

    fun group(item: T): String

    fun regroup(item: T, group: String): T

    fun blank(group: String): T

    fun title(item: T): String

    fun on(item: T): Boolean

    fun switched(item: T, on: Boolean): T

    fun clashes(item: T, live: List<T>): List<T>

    fun encode(group: String, items: List<T>): String

    fun decode(text: String): Pair<String, List<T>>?

    // @keep The text cell a new item opens for typing.
    val opens: String get() = cells.last { it is Cell.Text }.name

    // @keep What the player must fix before the item works, said under the list while it is picked.
    fun problem(item: T): String? = null
}

class Items<T>(id: String, name: String, description: String, bind: Bind<Grouped<T>>, val type: ItemKind<T>) :
    Valued<Grouped<T>>(id, name, description, Kind.ITEMS, bind) {
    sealed interface Entry {
        data class Heading(val group: String) : Entry

        data class Item(val index: Int) : Entry

        data class Empty(val group: String) : Entry
    }

    val groups get() = listOf("") + value.groups

    fun groupName(group: String) = group.ifEmpty { type.first }

    fun home(item: T, groups: List<String> = this.groups) = type.group(item).takeIf { it in groups } ?: ""

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

    fun live(index: Int) = value.items[index].let { type.on(it) && home(it) !in value.off }

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

    fun canNudge(line: Entry, by: Int): Boolean {
        val lines = entries()
        return when (line) {
            is Entry.Item -> lines.indexOf(line).let { if (by < 0) it > 1 else it in 0 until lines.lastIndex }
            is Entry.Heading -> line.group.isNotEmpty() && value.groups.indexOf(line.group) + by in value.groups.indices
            is Entry.Empty -> false
        }
    }

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

    fun add(group: String): Int {
        fold(group, false)
        val fresh = type.blank(group)
        val list = value.items
        val last = indicesIn(group).lastOrNull()
        val at = if (last != null) {
            last + 1
        } else {
            val before = groups.takeWhile { it != group }.toSet()
            list.indices.lastOrNull { home(list[it]) in before }?.plus(1) ?: 0
        }
        editItems { add(at, fresh) }
        return at
    }

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

    fun newGroup(): String {
        var n = groups.size
        while ("Group $n" in groups) n++
        val name = "Group $n"
        value = value.copy(groups = value.groups + name)
        return name
    }

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

    fun deleteGroup(group: String): Int {
        if (group.isEmpty()) return 0
        val list = value
        val gone = list.items.count { type.group(it) == group }
        value = list.withItems(list.items.filter { type.group(it) != group }) { it - group }
        return gone
    }

    fun export(group: String): String? {
        val mine = value.items.filter { home(it) == group }
        return if (mine.isEmpty()) null else type.encode(groupName(group), mine)
    }

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

    fun plural(count: Int) = if (count == 1) "1 ${type.noun}" else "$count ${type.noun}s"

    override val changed get() = false

    override fun reset() = Unit

    override fun summary(keys: (Int) -> String) = if (value.items.isEmpty()) "None" else plural(value.items.size)

    override fun spoken(keys: (Int) -> String, open: Boolean): String {
        val list = value
        if (list.items.isEmpty()) return "empty"
        return Narration.join(plural(list.items.size), if (list.groups.isEmpty()) null else "in ${list.groups.size + 1} groups")
    }

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
                        is Cell.Color -> Colors.spoken(cell.get(item))
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

enum class Canvas { HUD_LAYOUT, MENU_TABS, PLAYER_MODEL, HELD_ITEM }

class Preview(id: String, name: String, description: String, val canvas: Canvas) : Setting(id, name, description, Kind.PREVIEW)

class Panel<T>(id: String, name: String, description: String, bind: Bind<T>, val canvas: Canvas, private val describe: (T) -> String) :
    Valued<T>(id, name, description, Kind.PANEL, bind) {
    override fun show(v: T, keys: (Int) -> String) = describe(v)

    override fun spoken(keys: (Int) -> String, open: Boolean) = describe(value)
}
