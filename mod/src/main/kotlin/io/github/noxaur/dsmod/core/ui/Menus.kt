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
