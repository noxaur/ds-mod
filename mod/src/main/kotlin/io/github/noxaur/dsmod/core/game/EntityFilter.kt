package io.github.noxaur.dsmod.core.game

/**
 * A cheap test of which entities a feature reads, which the edge's tracker runs on each entity before it reads its
 * name or posts anything: its type is one of [types], or any type when there are none, and its name starts with
 * [prefix], or any name when it is null. The feature's own parser then says what each one is, as `Boss.parse` does.
 */
data class EntityFilter(val types: Set<String> = emptySet(), val prefix: String? = null) {
    init {
        require(types.isNotEmpty() || prefix != null) { "A filter that names no type and no prefix reads every entity" }
    }

    fun matches(entity: String, name: String) = (types.isEmpty() || entity in types) && (prefix == null || name.startsWith(prefix))

    fun matches(seen: EntitySeen) = matches(seen.entity, seen.name.plain)
}

/**
 * What an entity event is to a reader of these filters, from the entity as it was [before] the event to as it is
 * [after] it, null when the client no longer holds it: the event itself while the reader keeps the entity, an
 * [EntitySeen] when a rename brings it in, an [EntityGone] when one takes it out, and null when the reader never had it.
 */
fun List<EntityFilter>.view(before: EntitySeen?, after: EntitySeen?, event: GameEvent): GameEvent? {
    val had = before != null && any { it.matches(before) }
    val has = after != null && any { it.matches(after) }
    return when {
        has && had -> event
        has -> after
        had -> EntityGone(before!!.id)
        else -> null
    }
}

/** The entities the client holds, by id, as the entity events say. `Game` keeps one, and so does a fixture's cut. */
class EntityTable {
    private val held = LinkedHashMap<Int, EntitySeen>()

    /** Each entity with its latest name, oldest first. */
    val values: Collection<EntitySeen> get() = held.values

    /** The entity the last entity event [apply] took was about, as it was before that event, or null when it was new. */
    var before: EntitySeen? = null
        private set

    /** The same entity after the event, or null when the client no longer holds it. */
    var after: EntitySeen? = null
        private set

    /** Applies [event] to the table, and returns whether it was an entity event. */
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

    /** Forgets every entity, as a new world, which numbers its entities again, needs. */
    fun clear() = held.clear()
}
