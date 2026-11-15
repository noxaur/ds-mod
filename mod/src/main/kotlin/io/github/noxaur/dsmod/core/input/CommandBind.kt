package io.github.noxaur.dsmod.core.input

import com.google.gson.annotations.Expose
import io.github.noxaur.dsmod.core.game.Area

/** Where a bind's keys work: with no screen open, in a menu such as a chest or the inventory, or in both. */
enum class Where(val title: String) {
    WORLD("In the World"),
    MENUS("In Menus"),
    BOTH("In Both"),
    ;

    fun covers(at: Where) = this == BOTH || this == at

    fun overlaps(other: Where) = this == BOTH || other == BOTH || this == other
}

/**
 * A key, or a few keys together or in order, that sends [command]. Each key is a GLFW key code or a mouse button
 * from 0 to 9, as MoulConfig's key editor writes one. A bind fires only [where] it works and, when it names an
 * [area], only there. [extraKeys] lets other held keys, such as the one you walk with, not stop it.
 * [inOrder] makes [keys] a sequence of presses rather than keys held together. [group] is the name of the group
 * it is listed in, empty for the first one.
 */
data class CommandBind(
    @Expose val name: String = "",
    @Expose val command: String = "",
    @Expose val keys: List<Int> = emptyList(),
    @Expose val enabled: Boolean = true,
    @Expose val where: Where = Where.WORLD,
    @Expose val area: Area? = null,
    @Expose val priority: Int = 0,
    @Expose val extraKeys: Boolean = true,
    @Expose val inOrder: Boolean = false,
    @Expose val group: String = "",
) {
    /** What the bind sends: a command after a slash, a chat line without one, or null with nothing typed. */
    val message: Message?
        get() {
            val typed = command.trim()
            return when {
                typed.startsWith("/") -> typed.drop(1).trim().ifEmpty { null }?.let(Message::Command)
                typed.isEmpty() -> null
                else -> Message.Chat(typed)
            }
        }

    /** The name the player gave it, or its command. */
    val title: String get() = name.trim().ifEmpty { command.trim() }.ifEmpty { "New Bind" }

    /** Whether one press could fire both, so only the one that comes first in [CommandBinds.first] sends. */
    fun clashesWith(other: CommandBind): Boolean =
        enabled && other.enabled && keys.isNotEmpty() && inOrder == other.inOrder &&
            (if (inOrder) keys == other.keys else keys.toSet() == other.keys.toSet()) &&
            where.overlaps(other.where) && (area == null || other.area == null || area == other.area)

    sealed interface Message {
        /** A command, without its slash. */
        data class Command(val text: String) : Message

        data class Chat(val text: String) : Message
    }

    companion object {
        /** The longest line the server takes, as vanilla's chat box allows. */
        const val MAX_LENGTH = 256

        /** The most keys a bind takes. */
        const val MAX_KEYS = 4
    }
}

/** The rules over a list of binds. */
object CommandBinds {
    /** One line of the list as the player sees it: a group's heading, a bind under it, or the line of a group with none. */
    sealed interface Entry {
        data class Heading(val group: String) : Entry

        class Bind(val bind: CommandBind) : Entry

        data class Empty(val group: String) : Entry
    }

    /**
     * Each of [groups] as a heading followed by its binds in order, or by an empty line when it has none. The binds
     * of a group in [collapsed] are left out. A bind of a group that is gone sits in the first.
     */
    fun entries(binds: List<CommandBind>, groups: List<String>, collapsed: Collection<String> = emptyList()): List<Entry> = groups.flatMap { group ->
        val mine = binds.filter { home(it, groups) == group }
        listOf(Entry.Heading(group)) + when {
            group in collapsed -> emptyList()
            mine.isEmpty() -> listOf(Entry.Empty(group))
            else -> mine.map(Entry::Bind)
        }
    }

    private fun home(bind: CommandBind, groups: List<String>) = if (bind.group in groups) bind.group else groups.first()

    /**
     * Moves [bind] to line [to] of the list as shown, where it joins the group whose heading is above it. A
     * collapsed group's binds keep their order, and one moved onto its heading goes last in it. Returns the binds
     * in the list's order, and the moved bind, which is a new one when its group changed.
     */
    fun move(binds: List<CommandBind>, groups: List<String>, collapsed: Collection<String>, bind: CommandBind, to: Int): Pair<List<CommandBind>, CommandBind> {
        val shown = entries(binds, groups, collapsed).filterNot { it is Entry.Bind && it.bind === bind }.toMutableList()
        val at = to.coerceIn(1, shown.size)
        val heading = shown.subList(0, at).last { it is Entry.Heading } as Entry.Heading
        val moved = if (bind.group == heading.group) bind else bind.copy(group = heading.group)
        shown.add(at, Entry.Bind(moved))
        val order = groups.flatMap { group ->
            if (group in collapsed) {
                binds.filter { it !== bind && home(it, groups) == group } + listOfNotNull(moved.takeIf { it.group == group })
            } else {
                val start = shown.indexOf(Entry.Heading(group))
                shown.drop(start + 1).takeWhile { it !is Entry.Heading }.filterIsInstance<Entry.Bind>().map { it.bind }
            }
        }
        return order to moved
    }

    /** [order] with [group] moved to place [to], counted among the groups after the first, which stays first. */
    fun moveGroup(order: List<String>, group: String, to: Int): List<String> {
        val rest = order - group
        return rest.toMutableList().apply { add(to.coerceIn(0, rest.size), group) }
    }

    /** The bind that sends when several could: the highest [CommandBind.priority], then the first listed. */
    fun first(binds: Iterable<CommandBind>): CommandBind? = binds.maxByOrNull { it.priority }

    /** The other binds that [bind] clashes with, in list order. */
    fun clashes(bind: CommandBind, binds: List<CommandBind>): List<CommandBind> = binds.filter { it !== bind && bind.clashesWith(it) }
}
