package io.github.noxaur.dsmod.core.brief

import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudRow
import io.github.noxaur.dsmod.core.hud.TextRow
import io.github.noxaur.dsmod.core.hud.hud
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.readText

private const val WRAP = 44

/**
 * What an agent asks the developer to record: [name] tells this client's window from another agent's,
 * and [steps] are done in order.
 */
@Serializable
data class Brief(val name: String, val steps: List<Step>) {
    /** One thing to do. With [seconds] the step is timed and moves on by itself, and without it the developer says when it is done. */
    @Serializable
    data class Step(@SerialName("do") val action: String, val note: String? = null, val seconds: Int? = null)

    init {
        require(name.isNotBlank()) { "A brief needs a name, which tells its window from another agent's." }
        require(steps.isNotEmpty()) { "A brief needs at least one step." }
        steps.forEach { require(it.action.isNotBlank() && (it.seconds == null || it.seconds > 0)) { "A step needs a 'do', and its 'seconds' is above zero: $it" } }
    }

    companion object {
        /** Reads a brief, and fails on a key it does not know, so a typo is not a step that silently lost its timer. */
        fun read(file: Path): Brief = Json.decodeFromString(serializer(), file.readText())
    }
}

/**
 * One pass through a [brief]. It waits before the first step until the player starts it. With a [timeoutSeconds],
 * the pass is [timedOut] that long after [openedAt], whether or not it has started.
 */
class BriefRun(val brief: Brief, private val openedAt: Long = 0, private val timeoutSeconds: Int? = null) {
    /** The index of the step being done: -1 before the first, and the number of steps after the last. */
    var current = -1
        private set
    private var since = 0L

    val done get() = current >= brief.steps.size

    /** Moves to the next step. False when the last one is already done. */
    fun advance(now: Long): Boolean {
        if (done) return false
        current++
        since = now
        return true
    }

    /** Ends the run before its last step, for a player who has recorded what the request asked for. */
    fun finish(now: Long) {
        current = brief.steps.size
        since = now
    }

    /** Whether the run has taken longer than it may and is not done. */
    fun timedOut(now: Long) = timeoutSeconds != null && !done && now - openedAt >= timeoutSeconds * 1000L

    /** The seconds left before the pass times out, or null when it has no limit. */
    fun timeLeft(now: Long): Int? {
        val seconds = timeoutSeconds ?: return null
        return secondsLeft(seconds, openedAt, now)
    }

    /** Moves on when a timed step has run out. True when it did. */
    fun tick(now: Long) = remaining(now) == 0 && advance(now)

    /** The seconds left on a timed step, or null when the current step has no timer. */
    fun remaining(now: Long): Int? {
        val seconds = brief.steps.getOrNull(current)?.seconds ?: return null
        return secondsLeft(seconds, since, now)
    }

    /**
     * Moves on when a timed step has run out, and once more for each of the player's [presses]. Returns the `step`
     * record of each move, built as it happens: a record built later would name whatever step the run is on by then.
     */
    fun step(now: Long, presses: Int): List<JsonObject> = buildList {
        if (tick(now)) add(record())
        repeat(presses) { if (advance(now)) add(record()) }
    }

    /** The `step` record for the capture: the step that starts now, or that the last one is done. */
    fun record(): JsonObject = buildJsonObject {
        val step = brief.steps.getOrNull(current)
        if (step == null) put("done", true) else {
            put("index", current + 1)
            put("do", step.action)
        }
    }

    /** The checklist. [key] is the name of the key that moves on, and [saved] the capture's file once it is written. */
    fun hud(now: Long, key: String, saved: String?): HudBlock {
        val rows = mutableListOf<HudRow>()
        brief.steps.forEachIndexed { i, step ->
            val number = "${i + 1}. "
            wrap(step.action).forEachIndexed { l, line ->
                val lead = if (l == 0) number else " ".repeat(number.length)
                rows += TextRow(
                    text {
                        when {
                            i < current -> good(lead + line)
                            i > current -> muted(lead + line)
                            else -> {
                                body(lead + line)
                                val left = remaining(now)
                                if (l == 0 && left != null) value(" " + clock(left * 1000L))
                            }
                        }
                    },
                )
            }
            if (i == current) step.note?.let { note -> wrap(note).forEach { rows += TextRow(text { muted(" ".repeat(number.length) + it) }) } }
        }
        rows += TextRow(
            text {
                when {
                    done && saved != null -> { body("Saved "); value(saved) }
                    done -> body("Done.")
                    current < 0 -> action("Press $key to start recording.")
                    remaining(now) != null -> action("Press $key to skip the timer.")
                    else -> action("Press $key when done.")
                }
            },
        )
        val left = timeLeft(now)
        if (left != null && !done) rows += TextRow(text { muted("Time left "); value(clock(left * 1000L)) })
        return hud(brief.name) {}.copy(rows = rows)
    }
}

/** Whole seconds left of [seconds] counted from [since], rounded up, and never below zero. */
private fun secondsLeft(seconds: Int, since: Long, now: Long) = ((seconds * 1000L - (now - since) + 999) / 1000).toInt().coerceAtLeast(0)

private fun wrap(text: String): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(' ')) {
        if (line.isNotEmpty() && line.length + 1 + word.length > WRAP) {
            lines += line
            line = word
        } else {
            line = if (line.isEmpty()) word else "$line $word"
        }
    }
    return lines + line
}
