package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.capture.ChangeLog
import io.github.noxaur.dsmod.edge.typeId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.maps.MapId

/** Unnamed, unequipped mobs that a puzzle still reads. */
private fun watched(entity: Entity) = entity is Silverfish || entity is Creeper

/** The entity's equipment slots, then the one item it shows. */
private val slots = EquipmentSlot.VALUES
private val shown = slots.size

/** What an entry says besides its text and items, compared whole to tell whether the entry changed. */
private data class Shape(
    val type: EntityType<*>,
    val x: Int,
    val y: Int,
    val z: Int,
    val nameVisible: Boolean,
    val glowing: Boolean,
    val rotation: Int,
    val facing: Direction?,
)

/**
 * The encodings of the stacks that entities changed to most recently, by value. Mobs of one kind wear the same gear
 * and it changes the same way, as helmets that burn in daylight do, so a stack one mob changed to is likely another
 * one's soon. Encoding a stack costs tens of µs, because the codec runs too rarely for the JIT to compile it.
 */
private class ItemEncodings {
    /** A stack nothing changes any more, compared by value. */
    private class Key(val stack: ItemStack) {
        private val hash = (31 * stack.item.hashCode() + stack.count) * 31 + stack.componentsPatch.hashCode()

        override fun hashCode() = hash

        override fun equals(other: Any?) = other is Key && ItemStack.matches(stack, other.stack)
    }

    private val encodings = object : LinkedHashMap<Key, JsonElement>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, JsonElement>) = size > 256
    }

    /** Encodes [stack], which the caller never changes. */
    fun encode(stack: ItemStack): JsonElement = encodings.getOrPut(Key(stack)) { stack.toCaptureJson() }
}

/**
 * The entries of the `entities` record: players, named entities and anything holding or wearing an item, which
 * servers build mobs and holograms from, and glowing entities, displays, item frames and the puzzle mobs, with what
 * each one shows.
 *
 * Each entity keeps what its entry was built from, and a pass rebuilds the entry only when one of those changed,
 * then hands [ChangeLog] only the entries that changed. Building, encoding and comparing every entry cost a recording
 * near a millisecond a pass with 40 armored mobs in view.
 */
class EntityRecorder {
    private val encodings = ItemEncodings()

    private inner class Entry {
        // Copies, because the next pass compares against them and a stack can change in place.
        val stacks = arrayOfNulls<ItemStack>(slots.size + 1)
        val encoded = arrayOfNulls<JsonElement>(slots.size + 1)
        var shape: Shape? = null
        var nameText: Component? = null
        var name: JsonElement? = null
        var displayText: Component? = null
        var text: JsonElement? = null
        var json: JsonObject? = null

        /** Keeps [stack] in [index] and encodes it when it changed, and tells whether it did. Null is no stack. */
        fun stack(index: Int, stack: ItemStack?): Boolean {
            val kept = stacks[index]
            if (stack == null) {
                if (kept == null) return false
                stacks[index] = null
                encoded[index] = null
                return true
            }
            if (kept != null && ItemStack.matches(kept, stack)) return false
            val copy = stack.copy()
            stacks[index] = copy
            encoded[index] = encodings.encode(copy)
            return true
        }
    }

    // In the order of the pass, which is the order a removed entry is recorded in.
    private var last = LinkedHashMap<Int, Entry>()
    private var seen = LinkedHashMap<Int, Entry>()

    /**
     * The entries of the entities in view but [player] that are new or changed since the last pass, keyed by id, and
     * [JsonNull] for one no longer recorded, for [ChangeLog.offerChanges]. The maps that frames and displays show go to
     * [mapIds].
     */
    fun pass(level: ClientLevel, player: Player, mapIds: MutableSet<MapId>): Map<String, JsonElement> {
        val changed = LinkedHashMap<String, JsonElement>()
        for (entity in level.entitiesForRendering()) {
            if (entity === player) continue
            entry(entity, mapIds)?.let { changed[entity.id.toString()] = it }
        }
        for (id in last.keys) if (id !in seen) changed[id.toString()] = JsonNull
        last = seen.also { seen = last }
        seen.clear()
        return changed
    }

    /**
     * Keeps the entity's entry for the next pass when it is recorded, and returns it when it is new or changed. It is
     * one call per entity rather than a loop body so that the JIT compiles it within a few passes: [pass] runs only
     * four times a second, which leaves a loop inside it interpreted for the first minute of a recording.
     */
    private fun entry(entity: Entity, mapIds: MutableSet<MapId>): JsonObject? {
        val living = entity as? LivingEntity
        val equipped = living != null && slots.any { !living.getItemBySlot(it).isEmpty }
        val frame = entity as? ItemFrame
        val item = when {
            frame != null -> frame.item
            entity is ItemEntity -> entity.item.takeIf { it.has(DataComponents.CUSTOM_NAME) }
            entity is Display.ItemDisplay -> entity.itemStack
            else -> null
        }
        item?.get(DataComponents.MAP_ID)?.let(mapIds::add)
        val named = entity.hasCustomName() || entity is Player || equipped
        if (!named && !entity.isCurrentlyGlowing && item == null && entity !is Display && !watched(entity)) return null

        val entry = last[entity.id] ?: Entry()
        seen[entity.id] = entry
        var changed = false
        if (living != null) slots.forEachIndexed { index, slot -> if (entry.stack(index, living.getItemBySlot(slot).takeUnless { it.isEmpty })) changed = true }
        if (entry.stack(shown, item)) changed = true
        val shape = Shape(
            entity.type,
            // Whole blocks, so an entity that stands still records nothing new.
            entity.blockX,
            entity.blockY,
            entity.blockZ,
            entity.isCustomNameVisible,
            entity.isCurrentlyGlowing,
            frame?.rotation ?: 0,
            frame?.direction,
        )
        if (shape != entry.shape) {
            entry.shape = shape
            changed = true
        }
        // The type already says what an entity recorded only for its glow, item or text is.
        val nameText = if (named) entity.displayName else null
        if (nameText != entry.nameText) {
            entry.nameText = nameText
            entry.name = nameText?.toCaptureJson()
            changed = true
        }
        val displayText = (entity as? Display.TextDisplay)?.text
        if (displayText != entry.displayText) {
            entry.displayText = displayText
            entry.text = displayText?.toCaptureJson()
            changed = true
        }
        if (!changed) return null

        val json = buildJsonObject {
            put("entity", entity.typeId)
            position(shape.x, shape.y, shape.z)
            put("nameVisible", shape.nameVisible)
            entry.name?.let { put("name", it) }
            if (shape.glowing) put("glowing", true)
            if (equipped) put("equipment", buildJsonObject { slots.forEachIndexed { index, slot -> entry.encoded[index]?.let { put(slot.getName(), it) } } })
            entry.encoded[shown]?.let { put("item", it) }
            if (frame != null) {
                put("rotation", shape.rotation)
                put("facing", frame.direction.getName())
            }
            entry.text?.let { put("text", it) }
        }
        // An input can change and leave the entry the same, as a name's hover can.
        if (json == entry.json) return null
        entry.json = json
        return json
    }
}
