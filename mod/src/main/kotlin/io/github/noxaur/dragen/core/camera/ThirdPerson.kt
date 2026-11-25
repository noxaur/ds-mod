package io.github.noxaur.dragen.core.camera

import kotlin.math.exp

enum class View { FIRST, BACK, FRONT }

enum class Views(private val label: String, private val skipped: View?) {
    ALL("All", null),
    SKIP_FRONT("Skip Front", View.FRONT),
    SKIP_BACK("Skip Back", View.BACK),
    ;

    fun land(next: View): View = if (next == skipped) View.entries[(next.ordinal + 1) % View.entries.size] else next

    val third: View get() = land(View.BACK)

    override fun toString() = label
}

enum class KeyMode(private val label: String) {
    HOLD("Hold"),
    TOGGLE("Toggle"),
    ;

    override fun toString() = label
}

object ThirdPerson {
    const val DISTANCE = 4f

    fun distance(vanilla: Float, blocks: Float): Float = vanilla * blocks / DISTANCE

    // @keep The front view mirrors the pitch afterwards, so its tilt has to go the other way
    fun pitch(pitch: Float, tilt: Float, front: Boolean): Float = (pitch + if (front) -tilt else tilt).coerceIn(-90f, 90f)
}

/** @keep Never slides in, or the camera would show what is behind a wall for a frame. */
class Slide {
    private var shown = 0f
    private var last = 0L

    fun reset() {
        shown = 0f
        last = 0L
    }

    fun at(target: Float, smoothing: Float, nanos: Long): Float {
        val seconds = if (last == 0L) 0f else ((nanos - last) / 1e9f).coerceAtMost(0.1f)
        last = nanos
        shown = if (smoothing <= 0f || target <= shown) target else shown + (target - shown) * (1 - exp(-seconds / (smoothing * SECONDS_PER_PERCENT)))
        return shown
    }

    private companion object {
        const val SECONDS_PER_PERCENT = 0.0015f
    }
}

class Orbit(yaw: Float, pitch: Float) {
    var yaw = yaw
        private set
    var pitch = pitch
        private set

    fun turn(x: Double, y: Double) {
        yaw += x.toFloat() * DEGREES
        pitch = (pitch + y.toFloat() * DEGREES).coerceIn(-90f, 90f)
    }

    private companion object {
        const val DEGREES = 0.15f
    }
}
