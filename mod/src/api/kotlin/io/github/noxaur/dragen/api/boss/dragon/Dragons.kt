package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.world.BlockArea
import io.github.noxaur.dragen.api.Clock
import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.TickRate
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.HazardKind

private val CODES = Regex("§.")

private fun amount(written: String): Long? {
    val scale = when (written.lastOrNull()) {
        'k' -> 1_000.0
        'M' -> 1_000_000.0
        'B' -> 1_000_000_000.0
        else -> 1.0
    }
    val digits = written.trimEnd('k', 'M', 'B').replace(",", "")
    return digits.toDoubleOrNull()?.let { (it * scale).toLong() }
}

private data class Throw(val id: Int, var at: Vec, var hazard: Hazard.Projectile?)

internal class Dragons(private val server: ServerClock) : DragonApi, DragonFeed {
    private val ticking = object : Clock {
        override var now: Tick = Tick(0.0)
        override val rate: TickRate get() = server.rate
    }
    override val clock: Clock get() = ticking

    private var forming = Tick(1.0)

    override var cycle: Cycle = Cycle.Unknown
        private set

    private val counts = LinkedHashMap<String, Int>()
    override val diagnostics: Diagnostics get() = Diagnostics(counts.toMap())

    private fun count(name: String) {
        counts.merge(name, 1, Int::plus)
    }

    private val handlers = HashMap<Class<out DragonEvent>, MutableList<(DragonEvent) -> Unit>>()
    private var watchers = 0
    private val pending = ArrayList<DragonEvent>()

    private var altar = Altar()
    private val dragons = LinkedHashMap<Int, Tracked>()
    private val throws = HashMap<Int, Throw>()
    private val bolts = HashSet<Int>()
    private val dead = HashSet<Int>()
    private val disagreed = HashSet<Set<String>>()
    private var next = 0L

    private var inEnd = false
    private var tabEyes: Pair<Int, Int>? = null
    private var tabDragon = false
    private var tabEgg = false
    private var tabTypes = emptyMap<String, Long>()
    private var barTypes = emptyList<Pair<String, Double>>()
    private var sidebarHp: Long? = null
    private var chatType: String? = null
    private var chatSpawnAt: Tick? = null
    private var announced = false
    private var chatDespawnAt: Tick? = null
    private var chatEgg: Tick? = null
    private var summonedAt: Tick? = null
    private var fullAt: Tick? = null
    private var despawnedAt: Tick? = null
    private var deathSite: Vec? = null
    private var eyesChanged = Tick(0.0)
    private var tabChanged = Tick(0.0)

    override fun find(id: DragonId): Dragon? = dragons.values.firstOrNull { it.id == id }

    override fun watch(): AutoCloseable {
        watchers++
        var open = true
        return AutoCloseable {
            if (open) watchers--
            open = false
        }
    }

    override fun <E : DragonEvent> on(type: Class<E>, handler: (E) -> Unit): AutoCloseable {
        @Suppress("UNCHECKED_CAST")
        val each = handler as (DragonEvent) -> Unit
        handlers.getOrPut(type) { ArrayList() } += each
        return AutoCloseable {
            val list = handlers[type] ?: return@AutoCloseable
            list.remove(each)
            if (list.isEmpty()) handlers.remove(type)
        }
    }

    private var watching = Watch.NOTHING

    override val watch: Watch
        get() {
            val wanted = watchers > 0 || handlers.isNotEmpty()
            val area = altar.area
            if (!wanted) watching = Watch.NOTHING
            else if (watching === Watch.NOTHING || watching.areas.firstOrNull() != area) watching = Watch(BLOCKS, listOfNotNull(area), ENTITIES)
            return watching
        }

    override fun block(x: Int, y: Int, z: Int, state: String) = altar.block(x, y, z, state)

    override fun chat(text: String) {
        val now = forming
        SPAWNED.matchEntire(text)?.let {
            chatType = it.groupValues[1]
            chatSpawnAt = now
            announced = true
            return
        }
        DESPAWNED.matchEntire(text)?.let {
            chatDespawnAt = now
            announced = false
            if (dragons.isEmpty()) despawnedAt = now
            return
        }
        PLACED.matchEntire(text)?.let {
            val (eyes, needed) = it.groupValues[2].toInt() to it.groupValues[3].toInt()
            if (eyes == needed) fullAt = now
            if (!altar.known) pending += DragonEvent.EyePlaced(now, eyes, needed)
            return
        }
        if (text == EGG_SPAWNED) {
            altar.eggSpawned()
            return
        }
        RESPAWNING.matchEntire(text)?.let { chatEgg = now + Ticks((it.groupValues[1].toDoubleOrNull() ?: return) * TICKS_PER_SECOND); return }
        val callout = CALLOUT.matchEntire(text) ?: return
        val dragon = dragons.values.firstOrNull { it.type == Known.Value(DragonType(callout.groupValues[1])) } ?: dragons.values.firstOrNull() ?: return
        if (read(dragon, Signal.Line(callout.groupValues[2]))) return
        count("unread.callout")
        Scope(dragon).begin(AttackKind(callout.groupValues[2]), null, emptyList())
    }

    override fun tabList(rows: List<String>) {
        val info = rows.mapNotNull { row -> row.trim().split(": ", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val area = info["Area"]
        inEnd = area == END
        val eyes = info["Eyes Placed"]?.let(EYES::matchEntire)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        if (eyes != tabEyes) tabChanged = forming
        tabEyes = eyes
        tabDragon = info["Dragon"] == "Spawned"
        tabEgg = info["Egg"] == "Respawning"
        val start = rows.indexOfFirst { it.trim() == "Dragons:" }
        tabTypes = if (start < 0) emptyMap() else rows.drop(start + 1).map(String::trim).takeWhile { it.isNotEmpty() }
            .mapNotNull(TAB_DRAGON::matchEntire).associate { it.groupValues[1] to (amount(it.groupValues[2]) ?: 0) }
    }

    override fun sidebar(lines: List<String>) {
        sidebarHp = lines.firstNotNullOfOrNull { SIDEBAR_HP.matchEntire(it.trim())?.groupValues?.get(1)?.let(::amount) }
    }

    override fun bossBars(bars: List<SeenBossBar>) {
        barTypes = bars.mapNotNull { bar -> BAR_DRAGON.matchEntire(bar.name.replace(CODES, "").trim())?.let { it.groupValues[1] to bar.progress } }
    }

    override fun entities(seen: List<SeenEntity>) {
        val now = forming
        val present = HashSet<Int>()
        for (entity in seen) {
            present += entity.id
            when (entity.type) {
                DRAGON -> if (entity.id !in dead) dragon(entity, now)
                LIGHTNING -> if (bolts.add(entity.id)) lightning(entity.position, now)
                else -> thrown(entity, now)
            }
        }
        bolts.retainAll(present)
        for (dragon in dragons.values) if (dragon.entity !in present && dragon.missingSince == null) dragon.missingSince = now
        val gone = throws.keys - present
        for (id in gone) {
            val hazard = throws.remove(id)?.hazard ?: continue
            for (dragon in dragons.values) if (dragon.hazardList.remove(hazard)) pending += DragonEvent.HazardLanded(now, dragon.id, hazard)
        }
    }

    private fun dragon(entity: SeenEntity, now: Tick) {
        val dragon = dragons.getOrPut(entity.id) {
            Tracked(DragonId(next++), entity.id, Kits.of(DragonType(chatType ?: "")).traits.map { it() }, now).also {
                val announced = chatSpawnAt?.let { at -> now - at <= MATCH } == true || cycle is Cycle.Arriving
                if (announced) pending += DragonEvent.Spawned(now, it.id, type(entity.id))
            }
        }
        val before = dragon.movement
        dragon.moved(now, entity.position, entity.yaw, entity.pitch, entity.parts)
        NAMETAG.find(entity.name)?.let { tag ->
            val max = amount(tag.groupValues[2]) ?: return@let
            val shown = amount(tag.groupValues[1]) ?: return@let
            if (shown == 0L) dragon.killed = true
            if (dragon.health.max != max) dragon.health = Health(dragon.health.current.takeIf { it > 0 } ?: max, max)
            reading(dragon, HealthSource.NAMETAG, shown, now)
        }
        val moving = dragon.movement
        if (moving is Movement.Hovering && before !is Movement.Hovering) read(dragon, Signal.Moved(dragon.body, moving))
        if (moving is Movement.Diving && before !is Movement.Diving) {
            dragon.dove = dragon.body.position
            pending += DragonEvent.DiveStarted(now, dragon.id, dragon.body.position)
            dragon.attack?.takeIf { it.kind == DragonAttacks.CIRCLING }?.let { Scope(dragon).end() }
        }
        if (before is Movement.Diving && moving !is Movement.Diving) {
            val bottom = dragon.body.position - dragon.body.velocity
            pending += DragonEvent.DiveEnded(now, dragon.id, bottom, Purge.of(bottom))
        }
    }

    /** @keep A bolt lands on a beat of the attack that called it, the wind-ups on the dragon and the strike on each player in
     * the same tick, so its beat names it, and the distance from the dragon only names a bolt no beat is due for. */
    private fun lightning(at: Vec, now: Tick) {
        val dragon = dragons.values.minByOrNull { (it.body.position - at).length } ?: return
        val due = due(dragon, now) { it in BOLTS }
        val kind = due?.kind ?: dragon.bolt?.takeIf { it.first == now }?.second
            ?: if ((dragon.body.position - at).length <= WIND_UP_REACH) DragonHazards.LIGHTNING_WIND_UP else DragonHazards.LIGHTNING
        dragon.bolt = now to kind
        due?.let { dragon.hazardList.remove(it) }
        pending += DragonEvent.HazardLanded(now, dragon.id, Hazard.Strike(kind, Known.Value(now), Known.Value(at)))
    }

    private fun due(dragon: Tracked, now: Tick, kind: (HazardKind) -> Boolean): Hazard.Strike? = dragon.hazardList.filterIsInstance<Hazard.Strike>()
        .filter { kind(it.kind) && it.at is Known.Unknown }
        .mapNotNull { strike -> (strike.lands as? Known.Value)?.value?.let { strike to kotlin.math.abs((it - now).count) } }
        .filter { it.second <= DUE.count }
        .minByOrNull { it.second }?.first

    private fun thrown(entity: SeenEntity, now: Tick) {
        val known = throws[entity.id]
        if (known == null) {
            throws[entity.id] = Throw(entity.id, entity.position, null)
            return
        }
        if (known.hazard == null) {
            val dragon = dragons.values.minByOrNull { (it.body.position - known.at).length } ?: return
            val velocity = entity.position - known.at
            if (!read(dragon, Signal.Thrown(entity.type, known.at, velocity))) return
            val projectile = dragon.hazardList.lastOrNull() as? Hazard.Projectile
            known.hazard = projectile
            projectile?.let { thrown -> due(dragon, now) { it == thrown.kind } }?.let { dragon.hazardList.remove(it) }
        }
        known.at = entity.position
    }

    private fun read(dragon: Tracked, signal: Signal): Boolean {
        val scope = Scope(dragon)
        for (trait in dragon.traits) trait.read(signal, scope)
        return scope.acted
    }

    private inner class Scope(override val dragon: Tracked) : TraitScope {
        var acted = false
        override val clock: Clock get() = ticking

        override fun begin(kind: AttackKind, lasts: Ticks?, beats: List<Beat>) {
            acted = true
            val now = forming
            dragon.attack?.let { pending += DragonEvent.AttackEnded(now, dragon.id, it.kind) }
            val attack = Attack(kind, now, lasts?.let { Known.Value(now + it) } ?: Known.Unknown, beats)
            dragon.attack = attack
            pending += DragonEvent.AttackStarted(now, dragon.id, attack)
            for (beat in beats) hazard(Hazard.Strike(beat.hazard, Known.Value(now + beat.after), Known.Unknown))
        }

        override fun end() {
            val attack = dragon.attack ?: return
            acted = true
            dragon.attack = null
            pending += DragonEvent.AttackEnded(forming, dragon.id, attack.kind)
        }

        override fun hazard(hazard: Hazard) {
            acted = true
            dragon.hazardList += hazard
            pending += DragonEvent.HazardIncoming(forming, dragon.id, hazard)
        }

        override fun landed(hazard: Hazard) {
            acted = true
            dragon.hazardList.remove(hazard)
            pending += DragonEvent.HazardLanded(forming, dragon.id, hazard)
        }

        override fun debuff(kind: DebuffKind, lasts: Ticks?) {
            acted = true
            val now = forming
            val debuff = Debuff(kind, now, lasts?.let { Known.Value(now + it) } ?: Known.Unknown)
            dragon.debuffList += debuff
            pending += DragonEvent.DebuffApplied(now, dragon.id, debuff)
        }
    }

    private fun type(entity: Int): Known<DragonType> {
        val settled = dragons[entity]?.let { forming - it.born >= BAR_SETTLES } == true
        val names = (listOfNotNull(chatType) + tabTypes.keys + if (settled) barTypes.map { it.first } else emptyList()).distinct()
        if (names.size > 1 && dragons.size <= 1 && disagreed.add(names.toSet())) count("disagree.type")
        if (dragons.size > 1 && dragons.keys.first() != entity) return Known.Unknown
        return names.firstOrNull()?.let { Known.Value(DragonType(it)) } ?: Known.Unknown
    }

    override fun tick(gameTime: Long) {
        val now = Tick(gameTime.toDouble())
        val before = ticking.now
        ticking.now = now
        forming = now
        val placed = altar.placed
        val removed = altar.removed
        if (altar.known && altar.eyes == altar.needed) fullAt = now
        val emptied = altar.known && altar.eyes == 0 && fullAt?.let { now - it <= MATCH } == true
        val summoned = altar.settle() || emptied && summonedAt == null
        if (summoned) {
            summonedAt = now
            fullAt = null
        }
        settleDragons(before, now)
        val was = cycle
        cycle = cycle(now, placed + removed > 0)
        events(was, summoned, placed, removed, now)
        forming = now + ONE
        val sent = ArrayList(pending)
        pending.clear()
        for (event in sent) {
            for ((type, list) in handlers) {
                if (!type.isInstance(event)) continue
                for (handler in list.toList()) {
                    try {
                        handler(event)
                    } catch (e: Exception) {
                        count("handler.failed")
                    }
                }
            }
        }
    }

    private fun settleDragons(before: Tick, now: Tick) {
        val each = dragons.values.iterator()
        while (each.hasNext()) {
            val dragon = each.next()
            dragon.type = type(dragon.entity)
            health(dragon, now)
            val attack = dragon.attack
            if (attack != null) {
                for (beat in attack.beats) {
                    val at = attack.since + beat.after
                    if (at > before && at <= now) pending += DragonEvent.BeatReached(now, dragon.id, attack.kind, beat)
                }
                val ends = (attack.ends as? Known.Value)?.value
                val over = when {
                    ends != null -> now >= ends
                    attack.kind == DragonAttacks.CIRCLING -> false
                    else -> dragon.movement !is Movement.Hovering && now - attack.since > UNNAMED_GRACE
                }
                if (over) {
                    dragon.attack = null
                    pending += DragonEvent.AttackEnded(now, dragon.id, attack.kind)
                }
            } else {
                val hovering = dragon.movement as? Movement.Hovering
                if (hovering != null && now - hovering.since >= UNNAMED_AFTER) Scope(dragon).begin(UNNAMED, null, emptyList())
            }
            dragon.hazardList.removeAll { it is Hazard.Strike && (it.lands as? Known.Value)?.value?.let { lands -> now - lands > MATCH } == true }
            val killed = dragon.killed
            val announced = chatDespawnAt?.let { at -> now - at <= MATCH } == true && (dragons.size == 1 || killed)
            val missing = dragon.missingSince
            if (!killed && !announced && (missing == null || now - missing <= MATCH)) continue
            each.remove()
            if (killed || announced) {
                dead += dragon.entity
                chatDespawnAt = null
                despawnedAt = now
                deathSite = dragon.body.position
                pending += DragonEvent.Despawned(now, dragon.id, dragon.type, dragon.body.position)
            }
        }
    }

    private fun reading(dragon: Tracked, source: HealthSource, value: Long, now: Tick) {
        if (dragon.readings[source]?.first != value) dragon.readings[source] = value to now
    }

    /** @keep Across 28 fights the sidebar's exact `Dragon HP` changed every 10 ticks, the name tag's rounded value every 6, the
     * boss bar's fraction every 20 and the tab list's exact value every 40, so the freshest reading is the health, an exact one
     * first when two changed on the same tick. */
    private fun health(dragon: Tracked, now: Tick) {
        if (dragon.killed) return
        val max = dragon.health.max
        if (dragons.size == 1) sidebarHp?.let { reading(dragon, HealthSource.SIDEBAR, it, now) }
        val type = (dragon.type as? Known.Value)?.value?.name
        if (type != null) {
            tabTypes[type]?.let { reading(dragon, HealthSource.TAB, it, now) }
            barTypes.firstOrNull { it.first == type }?.second?.takeIf { now - dragon.born >= BAR_SETTLES && max > 0 }?.let { reading(dragon, HealthSource.BAR, (it * max).toLong(), now) }
        }
        val freshest = dragon.readings.entries.maxWithOrNull(compareBy<Map.Entry<HealthSource, Pair<Long, Tick>>> { it.value.second }.thenByDescending { it.key.ordinal }) ?: return
        val current = freshest.value.first
        if (current != dragon.health.current) {
            dragon.health = Health(current, max)
            dragon.healthAt = now
        }
    }

    private fun cycle(now: Tick, eyesMoved: Boolean): Cycle {
        if (dragons.isEmpty() && !announced && !altar.known && !inEnd) return Cycle.Unknown
        if (dragons.isNotEmpty() || announced) {
            summonedAt = null
            return Cycle.Fighting(dragons.values.toList())
        }
        if (altar.known) {
            checkEyes(now, eyesMoved)
            val site = deathSite ?: altar.beacon?.let { (x, y, z) -> Vec(x + 0.5, y.toDouble(), z + 0.5) }
            return when {
                !altar.eggComplete && summonedAt == null -> Cycle.Respawning(
                    despawnedAt?.let { Known.Value(it + EGG_AFTER_DESPAWN) } ?: chatEgg?.let { Known.Value(it) } ?: Known.Unknown,
                    Known.Value(altar.rebuilt),
                    site?.let { Known.Value(it) } ?: Known.Unknown,
                )
                summonedAt != null || !altar.towerStanding -> {
                    val spawns = summonedAt?.let { it + SPAWN_AFTER_SUMMON }
                        ?: now.takeIf { altar.towerCount > 0 }?.let { it + Ticks(altar.towerCount * DRAIN_TICKS) }
                    Cycle.Arriving(spawns?.let { Known.Value(it) } ?: Known.Unknown, Known.Value(altar.drained))
                }
                else -> {
                    despawnedAt = null
                    deathSite = null
                    Cycle.Summoning(altar.eyes, altar.needed)
                }
            }
        }
        val eyes = tabEyes
        return when {
            tabDragon -> Cycle.Fighting(emptyList())
            tabEgg -> Cycle.Respawning(chatEgg?.let { Known.Value(it) } ?: Known.Unknown, Known.Unknown, Known.Unknown)
            eyes != null -> Cycle.Summoning(eyes.first, eyes.second)
            else -> Cycle.Unknown
        }
    }

    private var told: Pair<Int, Int>? = null

    private fun checkEyes(now: Tick, moved: Boolean) {
        if (moved) eyesChanged = now
        val tab = tabEyes ?: return
        val world = altar.eyes to altar.needed
        if (tab == world || now - eyesChanged < SETTLE || now - tabChanged < SETTLE || told == tab) return
        told = tab
        count("disagree.eyes")
    }

    private fun events(was: Cycle, summoned: Boolean, placed: Int, removed: Int, now: Tick) {
        if (altar.known && !summoned && placed > 0) pending += DragonEvent.EyePlaced(now, altar.eyes, altar.needed)
        if (altar.known && !summoned && removed == 1) pending += DragonEvent.EyeRemoved(now, altar.eyes, altar.needed)
        val cycle = cycle
        if (was is Cycle.Unknown || was::class == cycle::class) return
        when (cycle) {
            is Cycle.Arriving -> pending += DragonEvent.Summoned(now, cycle.spawns)
            is Cycle.Respawning -> pending += DragonEvent.EggRespawning(now, cycle.egg)
            is Cycle.Summoning -> if (was is Cycle.Respawning) pending += DragonEvent.EggSpawned(now)
            else -> Unit
        }
    }

    override fun forget(area: BlockArea) = altar.forget(area)

    override fun worldChanged() {
        altar = Altar()
        dragons.clear()
        dead.clear()
        disagreed.clear()
        throws.clear()
        bolts.clear()
        pending.clear()
        inEnd = false
        tabEyes = null
        tabDragon = false
        tabEgg = false
        tabTypes = emptyMap()
        barTypes = emptyList()
        sidebarHp = null
        chatType = null
        chatSpawnAt = null
        announced = false
        chatDespawnAt = null
        chatEgg = null
        summonedAt = null
        fullAt = null
        despawnedAt = null
        deathSite = null
        told = null
        cycle = Cycle.Unknown
    }

    private companion object {
        const val END = "The End"
        const val DRAGON = "minecraft:ender_dragon"
        const val LIGHTNING = "minecraft:lightning_bolt"
        val BLOCKS = setOf("minecraft:end_portal_frame", "minecraft:beacon")
        val ENTITIES = setOf(DRAGON, LIGHTNING, "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball")
        const val TICKS_PER_SECOND = 20.0

        val SPAWNED = Regex("""☠ The (.+) Dragon has spawned!""")
        val DESPAWNED = Regex("""☠ The (.+) Dragon has de-spawned\.""")
        val RESPAWNING = Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")
        val PLACED = Regex("""» (.+) placed a Summoning Eye! \((\d+)/(\d+)\)""")
        const val EGG_SPAWNED = "✦ The Egg has Spawned!"
        val CALLOUT = Regex("""\[BOSS] (.+) Dragon: (.*)""")
        val EYES = Regex("""(\d+)/(\d+)""")
        val TAB_DRAGON = Regex("""(.+) Dragon ([\d,]+)❤""")
        val BAR_DRAGON = Regex("""(.+) Dragon""")
        val SIDEBAR_HP = Regex("""Dragon HP: ([\d,]+)❤""")
        val NAMETAG = Regex("""([\d.,]+[kMB]?)/([\d.,]+[kMB]?)$""")

        /** @keep After a dragon spawns the boss bar keeps the previous dragon's name and last health for up to 21 ticks, in 27 of
         * 28 spawns. */
        val BAR_SETTLES = Ticks(25.0)

        val ONE = Ticks(1.0)
        val DUE = Ticks(5.0)

        /** @keep The dragon stops a tick or two after its callout, so an attack with no known length waits this long for the hover. */
        val UNNAMED_GRACE = Ticks(10.0)

        val UNNAMED = AttackKind("Unnamed")
        val UNNAMED_AFTER = Ticks(5.0)
        val MATCH = Ticks(40.0)
        val SETTLE = Ticks(60.0)
        const val WIND_UP_REACH = 8.0
        val BOLTS = setOf(DragonHazards.LIGHTNING_WIND_UP, DragonHazards.LIGHTNING)

        /** @keep Across 28 fights a dead dragon's body stayed 98 or 99 ticks after the de-spawn line for its death animation, the
         * dragon spawned 168 to 170 ticks after the frames emptied, and the egg was whole 198 or 199 ticks after the de-spawn. */
        val SPAWN_AFTER_SUMMON = Ticks(169.0)
        const val DRAIN_TICKS = 2.0
        val EGG_AFTER_DESPAWN = Ticks(199.0)
    }
}
