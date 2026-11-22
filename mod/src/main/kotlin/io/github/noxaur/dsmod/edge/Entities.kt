package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.EntityFilter
import io.github.noxaur.dsmod.core.game.EntityGone
import io.github.noxaur.dsmod.core.game.EntityRenamed
import io.github.noxaur.dsmod.core.game.EntitySeen
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/**
 * The one walk over the client's entities. Each tick it tells [game] which entities match the filters of the
 * features that read them, as [EntitySeen], [EntityRenamed] and [EntityGone], and while none does it reads nothing.
 * A filter's types are tested first, by identity, so an entity of another type costs one lookup, and a name is read
 * again only when the server gave the entity another, which it does a mob on each hit. Entities are kept by their id
 * unboxed, so a tick allocates only for a new entity, a new name or an event.
 */
val Entity.typeId: String get() = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()

fun entityType(id: String): EntityType<*> = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id))

class EntityTracker(private val game: Game) {
    private class Known(val id: Int, var name: Component?, var head: ItemStack?, var matched: Boolean, var pass: Int)

    /** A filter with its types resolved to the game's own. */
    private class Test(val types: Set<EntityType<*>>, val prefix: String?)

    private val known = Int2ObjectOpenHashMap<Known>()
    private var filters = emptyList<EntityFilter>()
    private var tests = emptyList<Test>()

    /** Every type a filter names, and whether a filter takes an entity of any type by its name. */
    private var types = emptySet<EntityType<*>>()
    private var anyType = false
    private var pass = 0

    /** How many entities this pass kept, so the sweep runs only when some entity it kept before is missing. */
    private var held = 0
    private var tapped = game.tap

    fun track(client: Minecraft) {
        val wanted = game.entityFilters()
        // A capture that just started hears every entity again, so it begins with what the client holds.
        val tap = game.tap !== tapped
        val retest = wanted !== filters || tap
        tapped = game.tap
        if (wanted !== filters) {
            filters = wanted
            tests = wanted.map { filter -> Test(filter.types.mapTo(HashSet()) { entityType(it) }, filter.prefix) }
            types = tests.flatMapTo(HashSet()) { it.types }
            anyType = tests.any { it.types.isEmpty() }
        }
        if (filters.isEmpty() && known.isEmpty()) return
        val level = client.level ?: return
        val time = System.currentTimeMillis()
        pass++
        held = 0
        if (filters.isNotEmpty()) for (entity in level.entitiesForRendering()) see(entity, retest, tap, time)
        if (held != known.size) known.values.removeIf { gone ->
            if (gone.pass == pass) return@removeIf false
            if (gone.matched) game.post(time, EntityGone(gone.id))
            true
        }
    }

    /**
     * Tests [entity] when it is new, renamed or the filters changed, and posts what changed for it. It is one call per
     * entity rather than a loop body so that the JIT compiles it within a few ticks.
     */
    private fun see(entity: Entity, retest: Boolean, tap: Boolean, time: Long) {
        val name = entity.customName
        // An unnamed entity of a type no filter names matches nothing, and a dying mob is gone, so neither is kept.
        if (!entity.isAlive || name == null && entity.type !in types || !anyType && entity.type !in types) return
        var known = known.get(entity.id)
        held++
        if (known == null) {
            known = Known(entity.id, null, null, false, pass)
            this.known.put(entity.id, known)
        } else {
            known.pass = pass
            val head = (entity as? LivingEntity)?.getItemBySlot(EquipmentSlot.HEAD)
            if (!retest && known.name === name && (!known.matched || known.head === head)) return
        }
        val plain = name?.string.orEmpty()
        val matched = tests.any { (it.types.isEmpty() || entity.type in it.types) && (it.prefix == null || plain.startsWith(it.prefix)) }
        val was = known.matched
        val named = known.name
        known.matched = matched
        known.name = name
        if (!matched) {
            known.head = null
            if (was) game.post(time, EntityGone(entity.id))
            return
        }
        val head = (entity as? LivingEntity)?.getItemBySlot(EquipmentSlot.HEAD)
        val before = known.head
        known.head = head
        // A head can come a packet after its entity, so a head that changed is the entity seen again.
        val newHead = before !== head && (before == null || head == null || !ItemStack.isSameItemSameComponents(before, head))
        when {
            !was || tap || newHead -> game.post(
                time,
                EntitySeen(entity.id, entity.typeId, (name ?: Component.empty()).toText(), head?.takeUnless { it.isEmpty }?.toItem()),
            )
            named != name -> game.post(time, EntityRenamed(entity.id, (name ?: Component.empty()).toText()))
        }
    }

    /** Drops what the last world held, since a new world numbers its entities again and `Game` forgets them with it. */
    fun forget() {
        known.clear()
    }
}
