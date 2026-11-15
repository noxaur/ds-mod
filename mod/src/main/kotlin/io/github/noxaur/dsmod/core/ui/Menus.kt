package io.github.noxaur.dsmod.core.ui

/** The rules for how the game's menus sit on the screen and treat the cursor, in GUI pixels and milliseconds. */
object Menus {
    /** The height of a six-row chest menu, the tallest the server opens. */
    const val TALLEST = 222

    /**
     * The top of a menu [imageHeight] tall on a screen [screenHeight] tall, placed so that its bottom edge sits where a
     * six-row menu's does. The server puts Go Back and Close in a menu's bottom row, and the player's inventory sits
     * below it, so both stay put when one menu opens another of a different height.
     */
    fun top(screenHeight: Int, imageHeight: Int) = (screenHeight - TALLEST) / 2 + TALLEST - imageHeight

    /**
     * How soon after a screen closes the next one counts as opened from it. The server closes a menu and opens the
     * next one in the same tick, and the game takes the mouse back in between, which centers the cursor.
     */
    const val SWAP_MILLIS = 500L

    /** How soon after the player sends a command a menu counts as the one that command opened, with the server's ping. */
    const val COMMAND_MILLIS = 2_000L
}

/**
 * Follows the player through a run of menus, so a menu the server gave no Go Back can still lead back to the last
 * menu in the run that a command opened. A run goes on while each menu closes and the next opens within
 * [Menus.SWAP_MILLIS], as the server opens one menu from another. A menu opened from an NPC or an item starts a run
 * with no command, because nothing the player could send reopens it. The edge calls [sent] for each command the
 * player sends, [opened] for each new menu and [closed] as one closes.
 */
class MenuTrail {
    private var sent: String? = null
    private var sentAt = 0L
    private var closedAt: Long? = null
    private var command: String? = null
    private var title: String? = null

    /** The command that opened the menu open now, or null when none did. A menu tab for it shows as selected. */
    var opener: String? = null
        private set

    fun sent(now: Long, command: String) {
        sent = command
        sentAt = now
    }

    fun closed(now: Long) {
        closedAt = now
    }

    /**
     * The command that goes back from the menu titled [title] that opened at [now], or null when the run has none.
     * The menu that command opens gets null, because going back from it would open it again.
     */
    fun opened(now: Long, title: String): String? {
        val by = sent?.takeIf { now - sentAt <= Menus.COMMAND_MILLIS }
        sent = null
        val inRun = closedAt?.let { now - it <= Menus.SWAP_MILLIS } == true
        closedAt = null
        if (by != null) {
            command = by
            this.title = title
        } else if (!inRun) {
            command = null
            this.title = null
        }
        opener = command?.takeIf { title == this.title }
        return command?.takeIf { title != this.title }
    }
}

/**
 * Remembers where the cursor was when a screen closed, so the screen that opens next can put it back.
 * The edge calls [left] when the game takes the mouse back and [reopened] when it lets go again.
 */
class CursorMemory {
    private var x = 0.0
    private var y = 0.0
    private var at = Long.MIN_VALUE

    fun left(now: Long, x: Double, y: Double) {
        this.x = x
        this.y = y
        at = now
    }

    /** Where to put the cursor, or null to let the game center it. It is handed out once. */
    fun reopened(now: Long): Pair<Double, Double>? {
        if (at == Long.MIN_VALUE || now - at > Menus.SWAP_MILLIS) return null
        at = Long.MIN_VALUE
        return x to y
    }
}
