package io.github.noxaur.dsmod.core.hand

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class HandStyle(private val label: String) {
    @SerialName("BLOCK_HIT")
    @JsonNames("EXHIBITION")
    V1_7("1.7"),

    @SerialName("VANILLA")
    @JsonNames("SPIN", "STILL")
    MODERN("Modern"),

    STAB("Stab"),
    ;

    override fun toString() = label
}

/** What Animations' preview shows. */
enum class HandPreview(private val label: String) {
    HELD("Held"),
    SWINGING("Swinging"),
    SCREEN("Screen view"),
    ;

    fun step(by: Int): HandPreview = entries[(ordinal + by).coerceIn(0, entries.lastIndex)]

    override fun toString() = label
}

object HeldItem {
    /**
     * How many ticks a swing of [ticks] lasts at [speed], where 1 is the game's own. Never under one tick, or the
     * swing would end before it is drawn.
     */
    fun swingTicks(ticks: Int, speed: Float): Int = if (speed == 1f) ticks else (ticks / speed).roundToInt().coerceAtLeast(1)

    const val PREVIEW_REST = 10

    fun previewSwing(nanos: Long, ticks: Int): Float {
        val at = (nanos / NANOS_PER_TICK) % (ticks + PREVIEW_REST)
        return if (at < ticks) (at / ticks).toFloat() else 0f
    }

    private const val NANOS_PER_TICK = 50_000_000.0
}

data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun union(other: Frame) = Frame(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    fun within(bounds: Frame) = Frame(
        left.coerceIn(bounds.left, bounds.right),
        top.coerceIn(bounds.top, bounds.bottom),
        right.coerceIn(bounds.left, bounds.right),
        bottom.coerceIn(bounds.top, bounds.bottom),
    )

    fun grow(by: Float) = Frame(left - by, top - by, right + by, bottom + by)

    fun growOnScreen(by: Float) = Frame(
        if (left >= 0f) maxOf(0f, left - by) else left,
        if (top >= 0f) maxOf(0f, top - by) else top,
        if (right <= 1f) minOf(1f, right + by) else right,
        if (bottom <= 1f) minOf(1f, bottom + by) else bottom,
    )

    val onScreen get() = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f

    companion object {
        val SCREEN = Frame(0f, 0f, 1f, 1f)
    }
}

object ScreenView {
    const val MARGIN = 0.03f

    const val OVERFLOW = 0.25f

    private val REACH = Frame(-OVERFLOW, -OVERFLOW, 1 + OVERFLOW, 1 + OVERFLOW)

    fun frame(item: Frame, right: Boolean, shape: Float): Frame {
        val corner = if (right) Frame(1f, 1f, 1f, 1f) else Frame(0f, 1f, 0f, 1f)
        val needed = item.within(REACH).growOnScreen(MARGIN).union(corner)
        return if (needed.width / needed.height < shape) {
            val (left, end) = widen(needed.left, needed.right, needed.height * shape, if (right) 1 else -1)
            Frame(left, needed.top, end, needed.bottom)
        } else {
            val (top, bottom) = widen(needed.top, needed.bottom, needed.width / shape, 1)
            Frame(needed.left, top, needed.right, bottom)
        }
    }

    fun fit(item: Frame, shape: Float): Frame {
        val room = item.within(REACH).grow(MARGIN)
        val middleX = (room.left + room.right) / 2
        val middleY = (room.top + room.bottom) / 2
        val width = max(room.width, room.height * shape)
        val height = width / shape
        return Frame(middleX - width / 2, middleY - height / 2, middleX + width / 2, middleY + height / 2)
    }

    private fun widen(from: Float, to: Float, size: Float, anchor: Int): Pair<Float, Float> {
        val low = min(from, 0f)
        val high = max(to, 1f)
        val roomInside = high - low
        if (size >= roomInside) {
            val spare = (size - roomInside) / 2
            return (low - spare) to (high + spare)
        }
        return if (anchor > 0) {
            val start = max(low, to - size)
            start to start + size
        } else {
            val end = min(high, from + size)
            end - size to end
        }
    }
}
