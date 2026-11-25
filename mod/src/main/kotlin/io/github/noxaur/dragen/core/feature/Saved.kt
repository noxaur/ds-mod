package io.github.noxaur.dragen.core.feature

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val savedJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

interface Stored {
    fun load(text: String)

    fun takeChanges(): (() -> String)?

    fun unwritten()
}

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

    internal fun restore(saved: JsonElement?) {
        value = saved?.let { savedJson.decodeFromJsonElement(serializer, it) } ?: default
        changed = false
    }
}
