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
    /** One line of the list as the player sees it: a group's heading, or a bind under it. */
    sealed interface Entry {
        data class Heading(val group: String) : Entry

        class Bind(val bind: CommandBind) : Entry
    }

    /** Each of [groups] as a heading followed by its binds in order. A bind of a group that is gone sits in the first. */
    fun entries(binds: List<CommandBind>, groups: List<String>): List<Entry> = groups.flatMap { group ->
        listOf(Entry.Heading(group)) + binds.filter { (if (it.group in groups) it.group else groups.first()) == group }.map(Entry::Bind)
    }

    /**
     * Moves [bind] to line [to] of the list, where it joins the group whose heading is above it. Returns the binds
     * in the list's order, and the moved bind, which is a new one when its group changed.
     */
    fun move(binds: List<CommandBind>, groups: List<String>, bind: CommandBind, to: Int): Pair<List<CommandBind>, CommandBind> {
        val rest = entries(binds, groups).filterNot { it is Entry.Bind && it.bind === bind }.toMutableList()
        val at = to.coerceIn(1, rest.size)
        val group = (rest.subList(0, at).last { it is Entry.Heading } as Entry.Heading).group
        val moved = if (bind.group == group) bind else bind.copy(group = group)
        rest.add(at, Entry.Bind(moved))
        return rest.filterIsInstance<Entry.Bind>().map { it.bind } to moved
    }

    /** The bind that sends when several could: the highest [CommandBind.priority], then the first listed. */
    fun first(binds: Iterable<CommandBind>): CommandBind? = binds.maxByOrNull { it.priority }

    /** The other binds that [bind] clashes with, in list order. */
    fun clashes(bind: CommandBind, binds: List<CommandBind>): List<CommandBind> = binds.filter { it !== bind && bind.clashesWith(it) }
}
