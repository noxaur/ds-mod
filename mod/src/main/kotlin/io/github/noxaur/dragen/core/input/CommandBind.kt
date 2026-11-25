package io.github.noxaur.dragen.core.input

import com.google.gson.annotations.Expose
import io.github.noxaur.dragen.core.config.Cell
import io.github.noxaur.dragen.core.config.ItemKind
import io.github.noxaur.dragen.core.game.Area
import kotlinx.serialization.Serializable

enum class Where(val short: String) {
    WORLD("World"),
    MENUS("Menus"),
    BOTH("Both"),
    ;

    fun covers(at: Where) = this == BOTH || this == at

    fun overlaps(other: Where) = this == BOTH || other == BOTH || this == other
}

@Serializable
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
    val message: Message?
        get() {
            val typed = command.trim()
            return when {
                typed.startsWith("/") -> typed.drop(1).trim().ifEmpty { null }?.let(Message::Command)
                typed.isEmpty() -> null
                else -> Message.Chat(typed)
            }
        }

    val title: String get() = name.trim().ifEmpty { command.trim() }.ifEmpty { "New Bind" }

    fun clashesWith(other: CommandBind): Boolean =
        enabled && other.enabled && keys.isNotEmpty() && inOrder == other.inOrder &&
            (if (inOrder) keys == other.keys else keys.toSet() == other.keys.toSet()) &&
            where.overlaps(other.where) && (area == null || other.area == null || area == other.area)

    sealed interface Message {
        data class Command(val text: String) : Message

        data class Chat(val text: String) : Message
    }

    companion object {
        // @keep The longest line the server takes, as vanilla's chat box allows.
        const val MAX_LENGTH = 256

        const val MAX_KEYS = 4
    }
}

object CommandBinds : ItemKind<CommandBind> {
    private val AREAS: List<Area?> = listOf(null) + (Area.entries - Area.ANYWHERE)

    override val noun = "bind"
    override val verb = "send"
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

    override fun clashes(item: CommandBind, live: List<CommandBind>) = live.filter { it !== item && item.clashesWith(it) }

    override fun encode(group: String, items: List<CommandBind>) = BindShare.encode(group, items)

    override fun decode(text: String) = BindShare.decode(text)?.let { it.group to it.binds }

    override fun problem(item: CommandBind) = if (item.command.isBlank()) "Type a command, such as /warp end." else null

    fun first(binds: Iterable<CommandBind>): CommandBind? = binds.maxByOrNull { it.priority }

    const val MAX_PRIORITY = 9
}
