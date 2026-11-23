package io.github.noxaur.dsmod.core.feature

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val savedJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** @keep What the edge writes to one file. `takeChanges` runs on the client thread and hands back the encoding, which
 * the writer runs on its own thread, so a large state costs the frame nothing */
interface Stored {
    fun load(text: String)

    fun takeChanges(): (() -> String)?

    fun unwritten()
}

// @keep Keep S immutable, because setting value is what marks it changed, and the writer encodes it on another thread
class Saved<S : Any>(internal val serializer: KSerializer<S>, private val default: S, val profiled: Boolean = false) : Stored {
    var value: S = default
        set(new) {
            if (new === field) return
            field = new
            changed = true
        }

    private var changed = false

    override fun load(text: String) {
        value = savedJson.decodeFromString(serializer, text)
        changed = false
    }

    override fun unwritten() {
        changed = true
    }

    override fun takeChanges(): (() -> String)? {
        if (!changed) return null
        changed = false
        val value = value
        return { savedJson.encodeToString(serializer, value) }
    }

    internal fun json(): () -> JsonElement {
        val value = value
        return { savedJson.encodeToJsonElement(serializer, value) }
    }

    // @keep A profile switch hands the state another profile's value, which is no change to write
    internal fun restore(saved: JsonElement?) {
        value = saved?.let { savedJson.decodeFromJsonElement(serializer, it) } ?: default
        changed = false
    }
}
