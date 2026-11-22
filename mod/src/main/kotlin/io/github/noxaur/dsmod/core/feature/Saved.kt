package io.github.noxaur.dsmod.core.feature

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// @keep Keep S immutable, because setting value is what marks it changed
class Saved<S : Any>(private val serializer: KSerializer<S>, default: S) {
    var value: S = default
        set(new) {
            if (new === field) return
            field = new
            changed = true
        }

    private var changed = false

    fun encode(): String = json.encodeToString(serializer, value)

    fun load(text: String) {
        value = json.decodeFromString(serializer, text)
        changed = false
    }

    fun unwritten() {
        changed = true
    }

    fun takeChanges(): String? = if (changed) encode().also { changed = false } else null
}
