package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Box
import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.TickRate
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.dragon.Cycle
import io.github.noxaur.dragen.api.boss.dragon.Dragon
import io.github.noxaur.dragen.api.boss.dragon.DragonApi
import io.github.noxaur.dragen.api.boss.dragon.Movement
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.hud.HudBuilder
import io.github.noxaur.dragen.core.hud.hud
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.TextBuilder
import io.github.noxaur.dragen.core.text.fixed
import io.github.noxaur.dragen.core.text.grouped
import io.github.noxaur.dragen.core.world.Cuboid
import io.github.noxaur.dragen.core.world.Mark
import io.github.noxaur.dragen.core.world.Point
import io.github.noxaur.dragen.core.world.Segment
import io.github.noxaur.dragen.core.world.Waypoint
import io.github.noxaur.dragen.core.world.WaypointLook

private const val LOOK_AHEAD = 20.0
private const val ARROW_TICKS = 10.0
private const val ARC = 8
private const val HEAD_RAY = 80.0

private class Time(val now: Tick, val rate: TickRate) {
    fun span(ticks: Ticks) = "${fixed(ticks.count, 0)}t (${fixed(rate.seconds(ticks), 1)}s)"

    fun until(at: Tick): String = (at - now).let { if (it.count >= 0) "in ${span(it)}" else "${span(Ticks(-it.count))} ago" }

    fun at(at: Tick) = "tick ${at.whole}, ${until(at)}"
}

private fun Vec.point() = Point(x, y, z)

private fun Vec.speed(time: Time) = "${fixed(length, 3)} b/t (${fixed(length * time.rate.perSecond, 2)} b/s)"

private fun TextBuilder.known(known: Known<Tick>, time: Time) = if (known is Known.Value) value(time.at(known.value)) else muted("unknown")

private fun TextBuilder.part(label: String, show: TextBuilder.() -> Unit) {
    muted(label)
    show()
}

class DragonView(private val api: () -> DragonApi, private val eye: () -> Vec? = { null }, private val events: () -> List<String>) {
    private val aim = AimTracker()

    fun observe() {
        val api = api()
        val fight = api.cycle as? Cycle.Fighting
        aim.observe(fight?.dragons.orEmpty(), api.clock.now, eye())
    }

    fun block(): HudBlock {
        val api = api()
        val time = Time(api.clock.now, api.clock.rate)
        return hud("Dragon API") {
            row("Clock") {
                value("tick ${time.now.whole}")
                part("  rate ") { value("${fixed(time.rate.perSecond, 2)}/s, ${fixed(time.rate.millisPerTick, 1)} ms/tick") }
            }
            cycle(api.cycle, time)
            row("Diagnostics") { if (api.diagnostics.counts.isEmpty()) muted("none") else value(api.diagnostics.counts.entries.joinToString(", ") { "${it.key}=${it.value}" }) }
            row("Events") { if (events().isEmpty()) muted("none") }
            for (line in events()) line { muted("  "); body(line) }
        }
    }

    fun marks(): List<Mark> {
        val api = api()
        val fight = api.cycle as? Cycle.Fighting ?: return emptyList()
        val now = api.clock.now
        val marks = ArrayList<Mark>()
        for (dragon in fight.dragons) {
            val body = dragon.body
            val at = body.position
            marks += Waypoint(at.point(), "#${dragon.id.value} ${dragon.type.said { it.name }}", Role.ACTION, look = WaypointLook.BOX)
            marks += Segment(at.point(), (at + body.velocity * ARROW_TICKS).point(), Role.GOOD)
            marks += Segment(at.point(), (at + body.facing * ARROW_TICKS).point(), Role.WARNING)
            marks += Segment(at.point(), dragon.predict(Ticks(LOOK_AHEAD)).point(), Role.MUTED)
            for (part in body.parts) marks += cuboid(part.box, Role.VALUE)
            body.parts.firstOrNull { it.name == "head" }?.let { marks += Segment(it.box.center.point(), (it.box.center + body.facing * HEAD_RAY).point(), Role.BAD) }
            for (aimed in aim.solutions[dragon.id.value].orEmpty()) marks += aimMarks(aimed)
            for (hazard in dragon.hazards) marks += hazardMarks(hazard, now, api.clock.rate)
        }
        return marks
    }

    private fun cuboid(box: Box, role: Role): Cuboid {
        val half = box.width / 2
        return Cuboid(Point(box.center.x - half, box.center.y - box.height / 2, box.center.z - half), Point(box.center.x + half, box.center.y + box.height / 2, box.center.z + half), role)
    }

    private fun aimMarks(aimed: Aimed): List<Mark> {
        val eye = eye() ?: return emptyList()
        val role = if (aimed.shot.reachable) Role.GOOD else Role.BAD
        val arc = (1..ARC).map { Arrow.position(eye, Arrow.velocityFor(eye, aimed.shot.arrival, aimed.shot.flight), aimed.shot.flight * it / ARC).point() }
        return listOf(
            cuboid(Box(aimed.aimPoint, aimed.at.width, aimed.at.height), role),
            cuboid(aimed.at, Role.ACTION),
            Waypoint(aimed.aimPoint.point(), "aim ${aimed.part}", role, look = WaypointLook.LABEL),
        ) + (listOf(eye.point()) + arc).zipWithNext { a, b -> Segment(a, b, role) }
    }

    private fun hazardMarks(hazard: Hazard, now: Tick, rate: TickRate): List<Mark> = when (hazard) {
        is Hazard.Strike -> listOfNotNull(
            (hazard.at as? Known.Value)?.let { Waypoint(it.value.point(), "${hazard.kind.name} ${hazard.lands.said { land -> Time(now, rate).until(land) }}", Role.BAD, look = WaypointLook.BOX) },
        )
        is Hazard.Projectile -> {
            val here = hazard.at(now)
            listOf(
                Segment(hazard.from.point(), here.point(), Role.MUTED),
                Segment(here.point(), hazard.at(now + Ticks(LOOK_AHEAD)).point(), Role.BAD),
                Waypoint(here.point(), hazard.kind.name, Role.BAD, look = WaypointLook.BOX),
            )
        }
    }

    private fun HudBuilder.cycle(cycle: Cycle, time: Time) {
        when (cycle) {
            Cycle.Unknown -> row("Cycle") { muted("Unknown") }
            is Cycle.Summoning -> row("Cycle") { value("Summoning"); part("  eyes ") { value("${cycle.eyes}/${cycle.needed}") } }
            is Cycle.Arriving -> {
                row("Cycle") { value("Arriving") }
                row("  spawns") { known(cycle.spawns, time) }
                row("  tower") { value(cycle.tower.said { "${fixed(it.fraction * 100, 1)}%" }) }
            }
            is Cycle.Fighting -> {
                row("Cycle") { value("Fighting"); part("  dragons ") { value("${cycle.dragons.size}") } }
                for (dragon in cycle.dragons) dragon(dragon, time)
            }
            is Cycle.Respawning -> {
                row("Cycle") { value("Respawning") }
                row("  egg") { known(cycle.egg, time) }
                row("  rebuilt") { value(cycle.rebuilt.said { "${fixed(it.fraction * 100, 1)}%" }) }
                row("  death site") { value(cycle.deathSite.said { it.said() }) }
            }
        }
    }

    private fun HudBuilder.dragon(dragon: Dragon, time: Time) {
        val body = dragon.body
        val health = dragon.health
        val percent = if (health.max > 0) fixed(health.current * 100.0 / health.max, 1) else "?"
        line { action("Dragon #${dragon.id.value} "); value(dragon.type.said { it.name }) }
        row("  health") { value("${grouped(health.current)}/${grouped(health.max)}"); muted(" ($percent%)") }
        row("  position") { value(body.position.said()) }
        row("  velocity") { value(body.velocity.said()); muted("  speed "); value(body.velocity.speed(time)) }
        row("  facing") { value(body.facing.said()) }
        row("  predict +${fixed(LOOK_AHEAD, 0)}t") { value(dragon.predict(Ticks(LOOK_AHEAD)).said()) }
        row("  parts") { value("${body.parts.size}") }
        row("  movement") {
            when (val movement = dragon.movement) {
                Movement.Flying -> value("Flying")
                is Movement.Hovering -> { value("Hovering"); muted(" since "); value(time.at(movement.since)) }
                is Movement.Recovering -> { value("Recovering"); muted(" since "); value(time.at(movement.since)) }
                is Movement.Diving -> { value("Diving"); muted(" since "); value(time.at(movement.since)) }
            }
        }
        attack(dragon, time)
        aim(dragon)
        if (dragon.hazards.isEmpty()) row("  hazards") { muted("none") } else for (hazard in dragon.hazards) row("  hazard") { hazard(hazard, time) }
        if (dragon.debuffs.isEmpty()) row("  debuffs") { muted("none") } else for (debuff in dragon.debuffs) {
            row("  debuff") { value(debuff.kind.name); muted(" since "); value(time.at(debuff.since)); muted(", ends "); value(debuff.ends.said { time.at(it) }) }
        }
    }

    private fun HudBuilder.aim(dragon: Dragon) {
        val shots = aim.solutions[dragon.id.value].orEmpty()
        if (shots.isEmpty()) return row("  aim") { muted("no eye or no dragon path yet") }
        for (aimed in shots) row("  aim ${aimed.part}") {
            val shot = aimed.shot
            value("flight ${fixed(shot.flight, 1)}t")
            muted("  miss "); value(fixed(aimed.miss, 1))
            muted("  mode "); value(aimed.basis.name.lowercase())
            muted("  conf "); value(aimed.confidence?.let { "${fixed(it * 100, 0)}% of ${aimed.samples}" } ?: "unknown")
            if (!shot.reachable) { muted("  "); value("out of reach") }
        }
    }

    private fun HudBuilder.attack(dragon: Dragon, time: Time) {
        val attack = dragon.attack ?: return row("  attack") { muted("none") }
        row("  attack") { value(attack.kind.name); muted(" since "); value(time.at(attack.since)) }
        row("  ends") { known(attack.ends, time) }
        val next = attack.beats.firstOrNull { attack.since + it.after > time.now }
        row("  next beat") { if (next == null) muted("none left") else { value(next.hazard.name); muted(" "); value(time.until(attack.since + next.after)) } }
        for (beat in attack.beats) {
            val at = attack.since + beat.after
            line { muted("    +${fixed(beat.after.count, 0)}t "); if (at > time.now) body(beat.hazard.name) else muted(beat.hazard.name + " (past)") }
        }
    }

    private fun TextBuilder.hazard(hazard: Hazard, time: Time) {
        value(hazard.kind.name)
        when (hazard) {
            is Hazard.Strike -> {
                muted(" lands "); value(hazard.lands.said { time.until(it) })
                muted(" at "); value(hazard.at.said { it.said() })
            }
            is Hazard.Projectile -> {
                muted(" now "); value(hazard.at(time.now).said())
                muted(" speed "); value(hazard.velocity.speed(time))
            }
        }
    }
}
