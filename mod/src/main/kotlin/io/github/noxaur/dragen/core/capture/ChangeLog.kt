package io.github.noxaur.dragen.core.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChangeLog(private val record: (type: RawRecord, data: JsonObject) -> Unit) {
    private val values = HashMap<RawRecord, JsonObject>()
    private val headers = HashMap<RawRecord, JsonObject>()
    private val entries = HashMap<RawRecord, Map<String, JsonElement>>()
    private val offered = HashSet<RawRecord>()

    fun offer(type: RawRecord, value: JsonObject, force: Boolean = false) {
        if (values.put(type, value) != value || force) record(type, value)
    }

    fun offerEntries(type: RawRecord, header: JsonObject, current: Map<String, JsonElement>) {
        val full = headers.put(type, header) != header
        val current = current.toMap()
        val previous = entries.put(type, current).orEmpty()
        val changed = if (full) {
            current
        } else {
            current.filter { (key, value) -> previous[key] != value } +
                (previous.keys - current.keys).associateWith { JsonNull }
        }
        if (!full && changed.isEmpty()) return
        recordEntries(type, header, full, changed)
    }

    fun offerChanges(type: RawRecord, changed: Map<String, JsonElement>) {
        val full = offered.add(type)
        if (!full && changed.isEmpty()) return
        recordEntries(type, JsonObject(emptyMap()), full, changed)
    }

    private fun recordEntries(type: RawRecord, header: JsonObject, full: Boolean, entries: Map<String, JsonElement>) = record(
        type,
        buildJsonObject {
            header.forEach { (key, value) -> put(key, value) }
            put("full", full)
            put("entries", JsonObject(entries))
        },
    )
}
