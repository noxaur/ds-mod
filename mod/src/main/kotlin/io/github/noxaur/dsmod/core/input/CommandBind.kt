package io.github.noxaur.dsmod.core.input

import com.google.gson.annotations.Expose
import io.github.noxaur.dsmod.core.config.Cell
import io.github.noxaur.dsmod.core.config.ItemKind
import io.github.noxaur.dsmod.core.game.Area

/** Where a bind's keys work: with no screen open, in a menu such as a chest or the inventory, or in both. */
enum class Where(val title: String, val short: String) {
    WORLD("In the World", "World"),
    MENUS("In Menus", "Menus"),
    BOTH("In Both", "Both"),
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

/**
 * The rules over a list of binds, and what the settings' list of them shows: a line of a switch, a name, the
 * command, the keys and where they work, with the area, the priority and how the keys are read under it.
 */
object CommandBinds : ItemKind<CommandBind> {
    /** Anywhere, then each area a bind can be limited to. */
    private val AREAS: List<Area?> = listOf(null) + (Area.entries - Area.ANYWHERE)

    override val noun = "bind"
    override val first = "General"
    override val groupLength = BindShare.GROUP_LENGTH

    override val cells: List<Cell<CommandBind>> = listOf(
        Cell.Check("On", CommandBind::enabled) { b, on -> b.copy(enabled = on) },
        Cell.Text("Name", CommandBind::name, { b, name -> b.copy(name = name) }, hint = "Name", max = CommandBind.MAX_LENGTH, width = 64),
        Cell.Text("Command", CommandBind::command, { b, command -> b.copy(command = command) }, hint = "/warp end", max = CommandBind.MAX_LENGTH),
        Cell.Keys("Keys", CommandBind::keys, { b, keys -> b.copy(keys = keys) }, CommandBind.MAX_KEYS, CommandBind::inOrder, width = 92),
        Cell.Pick("Where", CommandBind::where, { b, where -> b.copy(where = where) }, Where.entries, Where::short, width = 42),
    )

    override val details: List<Cell<CommandBind>> = listOf(
        Cell.Pick("Area", CommandBind::area, { b, area -> b.copy(area = area) }, AREAS, { it?.title ?: "Anywhere" }, width = 96),
        Cell.Count("Priority", CommandBind::priority, { b, priority -> b.copy(priority = priority) }, -MAX_PRIORITY, MAX_PRIORITY),
        Cell.Check("Other Keys Held Too", CommandBind::extraKeys) { b, on -> b.copy(extraKeys = on) },
        Cell.Check("Keys in Order", CommandBind::inOrder) { b, on -> b.copy(inOrder = on) },
    )

    override fun group(item: CommandBind) = item.group

    override fun regroup(item: CommandBind, group: String) = item.copy(group = group)

    override fun blank(group: String) = CommandBind(group = group)

    override fun title(item: CommandBind) = item.title

    override fun on(item: CommandBind) = item.enabled

    override fun switched(item: CommandBind, on: Boolean) = item.copy(enabled = on)

    /** The other binds that [item] clashes with, in list order. */
    override fun clashes(item: CommandBind, live: List<CommandBind>) = live.filter { it !== item && item.clashesWith(it) }

    override fun encode(group: String, items: List<CommandBind>) = BindShare.encode(group, items)

    override fun decode(text: String) = BindShare.decode(text)?.let { it.group to it.binds }

    /** The bind that sends when several could: the highest [CommandBind.priority], then the first listed. */
    fun first(binds: Iterable<CommandBind>): CommandBind? = binds.maxByOrNull { it.priority }

    /** The furthest a priority goes either way. */
    const val MAX_PRIORITY = 9
}
