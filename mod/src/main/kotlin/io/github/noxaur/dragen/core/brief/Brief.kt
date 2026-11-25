package io.github.noxaur.dragen.core.brief

import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.hud.HudRow
import io.github.noxaur.dragen.core.hud.TextRow
import io.github.noxaur.dragen.core.hud.hud
import io.github.noxaur.dragen.core.text.clock
import io.github.noxaur.dragen.core.text.secondsLeft
import io.github.noxaur.dragen.core.text.text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.readText

private const val WRAP = 44

@Serializable
data class Brief(val name: String, val steps: List<Step>) {
    @Serializable
    data class Step(@SerialName("do") val action: String, val note: String? = null, val seconds: Int? = null)

    init {
        require(name.isNotBlank()) { "A brief needs a name, which tells its window from another agent's." }
        require(steps.isNotEmpty()) { "A brief needs at least one step." }
        steps.forEach { require(it.action.isNotBlank() && (it.seconds == null || it.seconds > 0)) { "A step needs a 'do', and its 'seconds' is above zero: $it" } }
    }

    companion object {
        fun read(file: Path): Brief = Json.decodeFromString(serializer(), file.readText())
    }
}

class BriefRun(val brief: Brief, private val openedAt: Long = 0, private val timeoutSeconds: Int? = null) {
    var current = -1
        private set
    private var since = 0L

    val done get() = current >= brief.steps.size

    fun advance(now: Long): Boolean {
        if (done) return false
        current++
        since = now
        return true
    }

    fun finish(now: Long) {
        current = brief.steps.size
        since = now
    }

    fun timedOut(now: Long) = timeoutSeconds != null && !done && now - openedAt >= timeoutSeconds * 1000L

    fun timeLeft(now: Long): Int? {
        val seconds = timeoutSeconds ?: return null
        return secondsLeft(seconds * 1000L - (now - openedAt)).toInt()
    }

    fun tick(now: Long) = remaining(now) == 0 && advance(now)

    fun remaining(now: Long): Int? {
        val seconds = brief.steps.getOrNull(current)?.seconds ?: return null
        return secondsLeft(seconds * 1000L - (now - since)).toInt()
    }

    fun step(now: Long, presses: Int): List<JsonObject> = buildList {
        if (tick(now)) add(record())
        repeat(presses) { if (advance(now)) add(record()) }
    }

    fun record(): JsonObject = buildJsonObject {
        val step = brief.steps.getOrNull(current)
        if (step == null) put("done", true) else {
            put("index", current + 1)
            put("do", step.action)
        }
    }

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
