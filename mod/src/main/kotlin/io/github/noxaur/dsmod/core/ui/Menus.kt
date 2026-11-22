package io.github.noxaur.dsmod.core.ui

import kotlin.math.roundToInt

enum class MenuLook(private val label: String) {
    SOLID("Solid"),

    GLASS("Glass"),
    ;

    override fun toString() = label
}

object Menus {
    const val TALLEST = 222

    /** @keep The server puts Go Back and Close in most menus' bottom row, and the player's inventory sits below it, so both
     * stay put when one menu opens another of a different height. */
    fun top(screenHeight: Int, imageHeight: Int) = (screenHeight - TALLEST) / 2 + TALLEST - imageHeight

    /** @keep The server closes a menu and opens the next one in the same tick, and the game takes the mouse back in between,
     * which centers the cursor. */
    const val SWAP_MILLIS = 500L

    const val COMMAND_MILLIS = 2_000L

    fun glassAlpha(opacity: Float) = (opacity.coerceIn(0f, 100f) * 2.55f).roundToInt()
}

class MenuTrail {
    private var sent: String? = null
    private var sentAt = 0L
    private var closedAt: Long? = null
    private var command: String? = null
    private var title: String? = null

    var opener: String? = null
        private set

    fun sent(now: Long, command: String) {
        sent = command
        sentAt = now
    }

    fun closed(now: Long) {
        closedAt = now
    }

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

class CursorMemory {
    private var x = 0.0
    private var y = 0.0
    private var at = Long.MIN_VALUE

    fun left(now: Long, x: Double, y: Double) {
        this.x = x
        this.y = y
        at = now
    }

    fun reopened(now: Long): Pair<Double, Double>? {
        if (at == Long.MIN_VALUE || now - at > Menus.SWAP_MILLIS) return null
        at = Long.MIN_VALUE
        return x to y
    }
}
