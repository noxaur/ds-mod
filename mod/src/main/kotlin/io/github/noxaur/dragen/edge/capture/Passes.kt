package io.github.noxaur.dragen.edge.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

class Passes<T : Any> {
    private var last = HashMap<Int, T>()
    private var seen = HashMap<Int, T>()

    fun previous(id: Int): T? = last[id]

    fun keep(id: Int, value: T) {
        seen[id] = value
    }

    fun end(changed: MutableMap<String, JsonElement>): Map<String, JsonElement> {
        for (id in last.keys) if (id !in seen) changed[id.toString()] = JsonNull
        last = seen.also { seen = last }
        seen.clear()
        return changed
    }
}
