package io.github.noxaur.dsmod.core.input

import io.github.noxaur.dsmod.core.game.Area

/**
 * Decides which bind a press fires. Keys held together fire on the press that completes them, so Shift then F6
 * and F6 then Shift both fire Shift + F6. Keys in order are a sequence of presses: one fires as soon as no longer
 * sequence could still follow, or once [idle] finds no press for the gap. The edge reports every press and
 * release, wherever it happens, so a key held before a menu opened still counts, and fires a bind only when [at]
 * says where the player is.
 */
class BindMatcher {
    private val held = LinkedHashSet<Int>()
    private val typed = ArrayList<Int>()
    private var typedAt = 0L
    private var typedWhere = Where.WORLD

    /** A key went down. Returns the bind it fires. [at] is null where no bind works, such as in chat. */
    fun press(key: Int, now: Long, binds: List<CommandBind>, at: Where?, inArea: (Area) -> Boolean): CommandBind? {
        held += key
        if (at == null) {
            typed.clear()
            return null
        }
        val live = binds.filter { it.enabled && it.keys.isNotEmpty() && it.where.covers(at) && (it.area == null || inArea(it.area)) }
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
        // A sequence that just ended waits only for a longer one that starts with all of it.
        val ended = sequences.filter(::ended).maxOfOrNull { it.keys.size } ?: 1
        return if (sequences.any { continues(it, ended) }) null else finish(sequences)
    }

    fun release(key: Int) {
        held -= key
    }

    /** Forgets held keys that are no longer down, such as one let go while the window was in the background. */
    fun forget(down: (Int) -> Boolean) {
        held.retainAll(down)
    }

    /**
     * Once [gap] has passed since the last press, fires the sequence it ended, if any.
     * [at] is where the player is on this tick. Chat, a focused field, F3, or Command Binds being off
     * pass null and drop it. So does a place no live in-order bind covers together with where the keys
     * were typed. A bind set to both still covers a move between the world and a menu.
     */
    fun idle(now: Long, gap: Long, binds: List<CommandBind>, at: Where?, inArea: (Area) -> Boolean): CommandBind? {
        val across = at != null && binds.any { liveAcross(it, at, inArea) }
        if (typed.isNotEmpty() && (at == null || !across)) {
            typed.clear()
            return null
        }
        if (typed.isEmpty() || now - typedAt < gap || at == null) return null
        return finish(binds.filter { liveAcross(it, at, inArea) })
    }

    /** An in-order bind that is on and covers both [at] and where the sequence was typed. */
    private fun liveAcross(bind: CommandBind, at: Where, inArea: (Area) -> Boolean) =
        bind.enabled && bind.inOrder && bind.keys.isNotEmpty() &&
            bind.where.covers(at) && bind.where.covers(typedWhere) &&
            (bind.area == null || inArea(bind.area))

    /** Whether an ending of what was typed, at least [shortest] keys long, starts [bind]'s sequence and stops short of its end. */
    private fun continues(bind: CommandBind, shortest: Int) =
        (shortest..minOf(typed.size, bind.keys.size - 1)).any { bind.keys.subList(0, it) == typed.subList(typed.size - it, typed.size) }

    private fun ended(bind: CommandBind) = bind.keys.size <= typed.size && typed.subList(typed.size - bind.keys.size, typed.size) == bind.keys

    /** The longest sequence that what was typed ends with, then the first of those by priority. */
    private fun finish(sequences: List<CommandBind>): CommandBind? {
        val ended = sequences.filter(::ended)
        typed.clear()
        val longest = ended.maxOfOrNull { it.keys.size } ?: return null
        return CommandBinds.first(ended.filter { it.keys.size == longest })
    }
}
