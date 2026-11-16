package io.github.noxaur.dsmod.core.camera

import kotlin.math.exp

/** The three views F5 goes through, in its order. */
enum class View { FIRST, BACK, FRONT }

/** Which views F5 goes through. First person is never skipped. */
enum class Views(private val label: String, private val skipped: View?) {
    ALL("All", null),
    SKIP_FRONT("Skip Front", View.FRONT),
    SKIP_BACK("Skip Back", View.BACK),
    ;

    /** The view F5 lands on when the game would go to [next]. */
    fun land(next: View): View = if (next == skipped) View.entries[(next.ordinal + 1) % View.entries.size] else next

    /** The third-person view to go to from first person. */
    val third: View get() = land(View.BACK)

    override fun toString() = label
}

/** Whether a feature's key, such as Free Look's, is held down or pressed once to switch. */
enum class KeyMode(private val label: String) {
    HOLD("Hold"),
    TOGGLE("Toggle"),
    ;

    override fun toString() = label
}

/** How the third-person camera sits. At the defaults each one is the game's own. */
object ThirdPerson {
    const val DISTANCE = 4f

    /** The game asks for [vanilla] blocks, which is 4 unless the server changed it, so the setting scales it. */
    fun distance(vanilla: Float, blocks: Float): Float = vanilla * blocks / DISTANCE

    /** The camera's pitch. The front view mirrors the pitch afterwards, so there the tilt goes in the other way. */
    fun pitch(pitch: Float, tilt: Float, front: Boolean): Float = (pitch + if (front) -tilt else tilt).coerceIn(-90f, 90f)
}

/**
 * Slides the camera out to its distance. It never slides in, because a wall that comes between the player and
 * the camera has to win in the same frame, or the camera shows what is behind the wall.
 */
class Slide {
    private var shown = 0f
    private var last = 0L

    /** Back at the head, where the next slide starts. */
    fun reset() {
        shown = 0f
        last = 0L
    }

    /** The distance to draw at [nanos], on the way to [target]. [smoothing] is 0 for at once, up to 100. */
    fun at(target: Float, smoothing: Float, nanos: Long): Float {
        val seconds = if (last == 0L) 0f else ((nanos - last) / 1e9f).coerceAtMost(0.1f)
        last = nanos
        shown = if (smoothing <= 0f || target <= shown) target else shown + (target - shown) * (1 - exp(-seconds / (smoothing * SECONDS_PER_PERCENT)))
        return shown
    }

    private companion object {
        /** At 100 the camera covers two thirds of the way in each 0.15 seconds. */
        const val SECONDS_PER_PERCENT = 0.0015f
    }
}

/** The angle Free Look's camera circles the player at. The player's own head never turns. */
class Orbit(yaw: Float, pitch: Float) {
    var yaw = yaw
        private set
    var pitch = pitch
        private set

    /** Takes the mouse's turn, in the units the game gives a head, so it feels the same. */
    fun turn(x: Double, y: Double) {
        yaw += x.toFloat() * DEGREES
        pitch = (pitch + y.toFloat() * DEGREES).coerceIn(-90f, 90f)
    }

    private companion object {
        const val DEGREES = 0.15f
    }
}
