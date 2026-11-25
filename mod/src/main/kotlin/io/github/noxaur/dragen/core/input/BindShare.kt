package io.github.noxaur.dragen.core.input

import com.google.gson.annotations.Expose
import io.github.noxaur.dragen.core.config.Share

object BindShare {
    private const val PREFIX = "dragen-binds:"
    private const val MAX_BINDS = 100

    const val GROUP_LENGTH = 32

    data class Shared(@Expose val group: String = "", @Expose val binds: List<CommandBind> = emptyList())

    fun encode(group: String, binds: List<CommandBind>): String = Share.encode(PREFIX, Shared(group, binds.map { bind -> bind.copy(group = "") }))

    // @keep Every bind comes in switched off, so the player reads each command before a key can send it.
    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON") // @keep Gson writes null into a field the text gets wrong.
    fun decode(text: String): Shared? {
        val shared = Share.decode(PREFIX, text, Shared::class.java) ?: return null
        val binds = (shared.binds as List<CommandBind?>?).orEmpty().filterNotNull().take(MAX_BINDS).map {
            CommandBind(
                name = (it.name as String?).orEmpty().take(CommandBind.MAX_LENGTH),
                command = (it.command as String?).orEmpty().take(CommandBind.MAX_LENGTH),
                keys = (it.keys as List<Int?>?).orEmpty().filterNotNull().take(CommandBind.MAX_KEYS),
                enabled = false,
                where = (it.where as Where?) ?: Where.WORLD,
                area = it.area,
                priority = it.priority,
                extraKeys = it.extraKeys,
                inOrder = it.inOrder,
            )
        }
        return Shared((shared.group as String?).orEmpty().trim().take(GROUP_LENGTH), binds)
    }
}
