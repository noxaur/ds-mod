package io.github.noxaur.dsmod.core.input

import io.github.noxaur.dsmod.core.game.Area

class BindMatcher {
    private val held = LinkedHashSet<Int>()
    private val typed = ArrayList<Int>()
    private var typedAt = 0L
    private var typedWhere = Where.WORLD

    fun press(key: Int, now: Long, binds: List<CommandBind>, at: Where?, inArea: (Area) -> Boolean): CommandBind? {
        held += key
        if (at == null) {
            typed.clear()
            return null
        }
        val live = binds.filter { live(it, at, inArea) }
        val together = CommandBinds.first(
            live.filter { !it.inOrder && key in it.keys && held.containsAll(it.keys) && (it.extraKeys || it.keys.containsAll(held)) },
        )
        if (together != null) {
            typed.clear()
            return together
        }
        val sequences = live.filter { it.inOrder }
        if (sequences.isEmpty()) return null
        typed += key
        if (typed.size > CommandBind.MAX_KEYS) typed.removeAt(0)
        typedAt = now
        typedWhere = at
        // @keep A sequence that just ended waits only for a longer one that starts with all of it.
        val ended = sequences.filter(::ended).maxOfOrNull { it.keys.size } ?: 1
        return if (sequences.any { continues(it, ended) }) null else finish(sequences)
    }

    fun release(key: Int) {
        held -= key
    }

    fun forget(down: (Int) -> Boolean) {
        held.retainAll(down)
    }

    fun idle(now: Long, gap: Long, binds: List<CommandBind>, at: Where?, inArea: (Area) -> Boolean): CommandBind? {
        if (typed.isEmpty()) return null
        if (at == null || binds.none { liveAcross(it, at, inArea) }) {
            typed.clear()
            return null
        }
        if (now - typedAt < gap) return null
        return finish(binds.filter { liveAcross(it, at, inArea) })
    }

    private fun liveAcross(bind: CommandBind, at: Where, inArea: (Area) -> Boolean) =
        bind.inOrder && live(bind, at, inArea) && bind.where.covers(typedWhere)

    private fun live(bind: CommandBind, at: Where, inArea: (Area) -> Boolean) =
        bind.enabled && bind.keys.isNotEmpty() && bind.where.covers(at) && (bind.area == null || inArea(bind.area))

    private fun continues(bind: CommandBind, shortest: Int) =
        (shortest..minOf(typed.size, bind.keys.size - 1)).any { bind.keys.subList(0, it) == typed.subList(typed.size - it, typed.size) }

    private fun ended(bind: CommandBind) = bind.keys.size <= typed.size && typed.subList(typed.size - bind.keys.size, typed.size) == bind.keys

    private fun finish(sequences: List<CommandBind>): CommandBind? {
        val ended = sequences.filter(::ended)
        typed.clear()
        val longest = ended.maxOfOrNull { it.keys.size } ?: return null
        return CommandBinds.first(ended.filter { it.keys.size == longest })
    }
}
