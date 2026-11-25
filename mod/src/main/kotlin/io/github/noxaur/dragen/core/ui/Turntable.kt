package io.github.noxaur.dragen.core.ui

class Turntable(private val speed: Float = SPEED) {
    private var base = 0f

    private var since: Long? = null
    private var grabX = 0.0
    private var grabY = 0.0
    private var grabYaw = 0f
    private var grabPitch = 0f
    private var dragYaw = 0f

    var dragging = false
        private set

    var pitch = 0f
        private set

    /** @keep The game's clock is epoch milliseconds, which a `Float` cannot hold to the millisecond, so the angle is worked
     * out in `Double` and wrapped before it is narrowed. */
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

    fun cancel(now: Long): Boolean {
        if (!dragging) return false
        pitch = grabPitch
        spinFrom(grabYaw, now)
        return true
    }

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
        const val SPEED = 30f
        const val DEGREES_PER_PIXEL = 2.0
        const val MAX_PITCH = 30f
        const val KEY_YAW = 15f
        const val KEY_PITCH = 5f
    }
}
