package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Turns snapshots taken on a timer into events: a snapshot is recorded only when it differs from
 * the last one of its type.
 */
class ChangeLog(private val record: (type: String, data: JsonObject) -> Unit) {
    private val values = HashMap<String, JsonObject>()
    private val headers = HashMap<String, JsonObject>()
    private val entries = HashMap<String, Map<String, JsonElement>>()

    fun offer(type: String, value: JsonObject, force: Boolean = false) {
        if (values.put(type, value) != value || force) record(type, value)
    }

    /**
     * For keyed collections such as container slots. A new [header] records every entry with
     * `"full": true`. Otherwise only changed entries are recorded, and a removed key maps to null.
     */
    fun offerEntries(type: String, header: JsonObject, current: Map<String, JsonElement>) {
        val full = headers.put(type, header) != header
        val previous = entries.put(type, current).orEmpty()
        val changed = if (full) {
            current
        } else {
            current.filter { (key, value) -> previous[key] != value } +
                (previous.keys - current.keys).associateWith { JsonNull }
        }
        if (!full && changed.isEmpty()) return
        record(
            type,
            buildJsonObject {
                header.forEach { (key, value) -> put(key, value) }
                put("full", full)
                put("entries", JsonObject(changed))
            },
        )
    }
}
