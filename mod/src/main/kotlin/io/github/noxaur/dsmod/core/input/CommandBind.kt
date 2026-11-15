package io.github.noxaur.dsmod.core.input

import com.google.gson.annotations.Expose

/**
 * A key that sends [command] when the player presses it in the world. [key] is a GLFW key code, a mouse button
 * from 0 to 9, or [NONE], as MoulConfig's key editor writes one.
 */
data class CommandBind(@Expose val command: String = "", @Expose val key: Int = NONE) {
    /** What the key sends: a command after a slash, a chat line without one, or null for a row with nothing typed. */
    val message: Message?
        get() {
            val typed = command.trim()
            return when {
                typed.startsWith("/") -> typed.drop(1).trim().ifEmpty { null }?.let(Message::Command)
                typed.isEmpty() -> null
                else -> Message.Chat(typed)
            }
        }

    sealed interface Message {
        /** A command, without its slash. */
        data class Command(val text: String) : Message

        data class Chat(val text: String) : Message
    }

    companion object {
        const val NONE = -1

        /** The longest line the server takes, as vanilla's chat box allows. */
        const val MAX_LENGTH = 256

        /** The keys two or more of [binds] share, so one press would send each of their commands. */
        fun clashes(binds: List<CommandBind>): Set<Int> =
            binds.map { it.key }.filter { it != NONE }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
}
