package io.github.noxaur.dragen.core.game

data class EntityFilter(val types: Set<String> = emptySet(), val prefix: String? = null) {
    init {
        require(types.isNotEmpty() || prefix != null) { "A filter that names no type and no prefix reads every entity" }
    }

    fun matches(entity: String, name: String) = (types.isEmpty() || entity in types) && (prefix == null || name.startsWith(prefix))

    fun matches(seen: EntitySeen) = matches(seen.entity, seen.name.plain)
}

fun List<EntityFilter>.view(before: EntitySeen?, after: EntitySeen?, event: GameEvent): GameEvent? {
    val had = before != null && any { it.matches(before) }
    val has = after != null && any { it.matches(after) }
    return when {
        has && had -> event
        has -> after
        had && before != null -> EntityGone(before.id)
        else -> null
    }
}

class EntityTable {
    private val held = LinkedHashMap<Int, EntitySeen>()

    val values: Collection<EntitySeen> get() = held.values

    var before: EntitySeen? = null
        private set

    var after: EntitySeen? = null
        private set

    fun apply(event: GameEvent): Boolean {
        when (event) {
            is EntitySeen -> {
                before = held.put(event.id, event)
                after = event
            }
            is EntityRenamed -> {
                before = held[event.id]
                after = before?.copy(name = event.name)?.also { held[event.id] = it }
            }
            is EntityGone -> {
                before = held.remove(event.id)
                after = null
            }
            else -> return false
        }
        return true
    }

    fun clear() = held.clear()
}
