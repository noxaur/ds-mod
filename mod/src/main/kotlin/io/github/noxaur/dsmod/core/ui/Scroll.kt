package io.github.noxaur.dsmod.core.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

class Scroll {
    var content = 0
        set(value) {
            field = value
            to(target)
        }

    var view = 0
        set(value) {
            field = value
            to(target)
        }

    private var position = 0.0
    private var target = 0

    val offset get() = position.roundToInt()

    val max get() = (content - view).coerceAtLeast(0)

    val overflows get() = content > view

    val gliding get() = position != target.toDouble()

    fun to(y: Int) {
        target = y.coerceIn(0, max)
        position = target.toDouble()
    }

    fun by(dy: Int) = to(target + dy)

    fun glide(y: Int) {
        target = y.coerceIn(0, max)
    }

    fun step(ms: Long): Boolean {
        if (!gliding) return false
        val before = offset
        val gap = target - position
        position = if (abs(gap) < 0.5) target.toDouble() else position + gap * (1 - exp(-ms / GLIDE_MS))
        return offset != before
    }

    fun thumb(track: Int) = if (!overflows) track else (track.toLong() * view / content).toInt().coerceIn(MIN_THUMB.coerceAtMost(track), track)

    fun thumbY(track: Int) = if (max == 0) 0 else ((track - thumb(track)).toLong() * offset / max).toInt()

    fun drag(y: Double, track: Int) {
        val room = track - thumb(track)
        if (room > 0) to((y / room * max).roundToInt())
    }

    private companion object {
        const val MIN_THUMB = 8
        const val GLIDE_MS = 50.0
    }
}
