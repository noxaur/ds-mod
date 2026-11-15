package io.github.noxaur.dsmod.core.ui

/**
 * The turn of a model shown on a screen, such as the player in the profile viewer. It spins on its own at [speed]
 * degrees a second. A drag turns it with the mouse, sideways for its yaw and up and down for its pitch, and on release
 * it spins on from where it was let go. Escape during a drag puts it back where the drag began, and the arrow keys
 * turn it by a step. Times are in milliseconds.
 */
class Turntable(private val speed: Float = SPEED) {
    private var base = 0f

    // When the spin from [base] began, null until the first frame asks, so it starts facing the player.
    private var since: Long? = null
    private var grabX = 0.0
    private var grabY = 0.0
    private var grabYaw = 0f
    private var grabPitch = 0f
    private var dragYaw = 0f

    var dragging = false
        private set

    /** Degrees up or down, kept within [MAX_PITCH] so the model never turns over. */
    var pitch = 0f
        private set

    /**
     * Degrees around, from facing the player, within a turn. The game's clock is epoch milliseconds, which a `Float`
     * cannot hold to the millisecond, so the angle is worked out in `Double` and wrapped before it is narrowed.
     */
    fun yaw(now: Long): Float {
        if (dragging) return dragYaw
        val start = since ?: now.also { since = it }
        return ((base + (now - start) * speed / 1000.0) % 360.0).toFloat()
    }

    fun press(x: Double, y: Double, now: Long) {
        grabYaw = yaw(now)
        grabPitch = pitch
        dragYaw = grabYaw
        grabX = x
        grabY = y
        dragging = true
    }

    fun drag(x: Double, y: Double) {
        if (!dragging) return
        dragYaw = grabYaw + ((x - grabX) * DEGREES_PER_PIXEL).toFloat()
        pitch = (grabPitch + ((y - grabY) * DEGREES_PER_PIXEL).toFloat()).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun release(now: Long) {
        if (!dragging) return
        spinFrom(dragYaw, now)
    }

    /** Ends a drag with the model back where it began, and says whether there was one to end. */
    fun cancel(now: Long): Boolean {
        if (!dragging) return false
        pitch = grabPitch
        spinFrom(grabYaw, now)
        return true
    }

    /** Turns it by [yaw] and [pitch] degrees at once, as an arrow key does. */
    fun turn(yaw: Float, pitch: Float, now: Long) {
        if (dragging) return
        this.pitch = (this.pitch + pitch).coerceIn(-MAX_PITCH, MAX_PITCH)
        spinFrom(yaw(now) + yaw, now)
    }

    private fun spinFrom(yaw: Float, now: Long) {
        base = yaw % 360f
        since = now
        dragging = false
    }

    companion object {
        /** A turn every 12 seconds, slow enough to read the armor as it goes by. */
        const val SPEED = 30f
        const val DEGREES_PER_PIXEL = 2.0
        const val MAX_PITCH = 30f
        const val KEY_YAW = 15f
        const val KEY_PITCH = 5f
    }
}
