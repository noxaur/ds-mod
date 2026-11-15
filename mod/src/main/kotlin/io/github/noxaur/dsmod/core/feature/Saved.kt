package io.github.noxaur.dsmod.core.feature

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * The state a feature keeps between sessions. It starts as the default, and the edge replaces it with what
 * the last session saved. A feature only sets [value], and the edge writes it to disk after it changed.
 * Keep [S] immutable, because setting [value] is what marks it changed.
 */
class Saved<S : Any>(private val serializer: KSerializer<S>, default: S) {
    var value: S = default
        set(new) {
            // A caller that hands back the same value changed nothing, so nothing is written.
            if (new === field) return
            field = new
            changed = true
        }

    private var changed = false

    fun encode(): String = json.encodeToString(serializer, value)

    /** Replaces [value] with what [text] says, and throws without touching it when [text] does not parse. */
    fun load(text: String) {
        value = json.decodeFromString(serializer, text)
        changed = false
    }

    /** Marks [value] unwritten again, after writing it failed. */
    fun unwritten() {
        changed = true
    }

    /** The JSON to write when [value] was set since the last call, or null when there is nothing to write. */
    fun takeChanges(): String? = if (changed) encode().also { changed = false } else null
}
