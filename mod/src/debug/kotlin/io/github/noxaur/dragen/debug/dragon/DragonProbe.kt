package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.dragon.DragonApi
import io.github.noxaur.dragen.api.boss.dragon.DragonEvent
import io.github.noxaur.dragen.core.text.fixed

private const val KEPT = 10

internal fun <T> Known<T>.said(show: (T) -> String) = when (this) {
    is Known.Value -> show(value)
    Known.Unknown -> "unknown"
}

internal fun Vec.said() = "${fixed(x, 1)}, ${fixed(y, 1)}, ${fixed(z, 1)}"

private fun Hazard.said() = when (this) {
    is Hazard.Strike -> "${kind.name} at ${at.said { it.said() }}"
    is Hazard.Projectile -> "${kind.name} from ${from.said()}"
}

private fun said(event: DragonEvent): String = when (event) {
    is DragonEvent.EyePlaced -> "Eye placed ${event.eyes}/${event.needed}"
    is DragonEvent.EyeRemoved -> "Eye removed ${event.eyes}/${event.needed}"
    is DragonEvent.Summoned -> "Summoned, spawns at ${event.spawns.said { it.whole.toString() }}"
    is DragonEvent.Spawned -> "Spawned #${event.dragon.value} ${event.type.said { it.name }}"
    is DragonEvent.AttackStarted -> "#${event.dragon.value} began ${event.attack.kind.name}, ${event.attack.beats.size} beats"
    is DragonEvent.BeatReached -> "#${event.dragon.value} ${event.attack.name} beat ${event.beat.hazard.name}"
    is DragonEvent.AttackEnded -> "#${event.dragon.value} ended ${event.attack.name}"
    is DragonEvent.HazardIncoming -> "#${event.dragon.value} incoming ${event.hazard.said()}"
    is DragonEvent.HazardLanded -> "#${event.dragon.value} landed ${event.hazard.said()}"
    is DragonEvent.DebuffApplied -> "#${event.dragon.value} debuff ${event.debuff.kind.name}"
    is DragonEvent.DebuffEnded -> "#${event.dragon.value} debuff ${event.debuff.name} ended"
    is DragonEvent.DiveStarted -> "#${event.dragon.value} dove from ${event.from.said()}"
    is DragonEvent.DiveEnded -> "#${event.dragon.value} dive bottomed at ${event.bottom.said()}" + event.purge?.let { " (${it.name.lowercase()} purge)" }.orEmpty()
    is DragonEvent.Despawned -> "#${event.dragon.value} died at ${event.deathSite.said()}"
    is DragonEvent.EggRespawning -> "Egg rebuilding"
    is DragonEvent.EggSpawned -> "Egg whole"
}

/** @keep The API scans the world only while something watches it or listens, so the probe watches and listens only while on, and a listener closed here is gone from the API, so being off costs nothing. */
class DragonProbe(private val api: () -> DragonApi) {
    private var open: DragonApi? = null
    private val held = ArrayList<AutoCloseable>()
    private val lines = ArrayDeque<String>()

    val recent: List<String> get() = lines

    fun update(on: Boolean) {
        val wanted = if (on) api() else null
        if (wanted === open) return
        held.forEach(AutoCloseable::close)
        held.clear()
        lines.clear()
        open = wanted
        if (wanted == null) return
        held += wanted.watch()
        for (type in DragonEvent::class.java.permittedSubclasses) {
            @Suppress("UNCHECKED_CAST")
            held += wanted.on(type as Class<DragonEvent>) { record(it) }
        }
    }

    private fun record(event: DragonEvent) {
        lines.addFirst("${event.tick.whole}  ${said(event)}")
        if (lines.size > KEPT) lines.removeLast()
    }
}
