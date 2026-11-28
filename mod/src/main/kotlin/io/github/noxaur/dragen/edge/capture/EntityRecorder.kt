package io.github.noxaur.dragen.edge.capture

import io.github.noxaur.dragen.edge.typeId
import kotlinx.serialization.json.JsonElement
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

private fun watched(entity: Entity) = entity is Silverfish || entity is Creeper

private val slots = EquipmentSlot.VALUES
private val shown = slots.size

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

/** @keep Encoding a stack costs tens of µs, because the codec runs too rarely for the JIT to compile it. */
private class ItemEncodings {
    private class Key(val stack: ItemStack) {
        private val hash = (31 * stack.item.hashCode() + stack.count) * 31 + stack.componentsPatch.hashCode()

        override fun hashCode() = hash

        override fun equals(other: Any?) = other is Key && ItemStack.matches(stack, other.stack)
    }

    private val encodings = object : LinkedHashMap<Key, JsonElement>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, JsonElement>) = size > 256
    }

    /** @keep [stack] must be a copy, because the game changes a stack in place. */
    fun encode(stack: ItemStack): JsonElement = encodings.getOrPut(Key(stack)) { stack.toCaptureJson() }
}

/** @keep Building, encoding and comparing every entry cost a recording near a millisecond a pass with 40 armored mobs in view. */
class EntityRecorder {
    private val encodings = ItemEncodings()

    private inner class Entry {
        val stacks = arrayOfNulls<ItemStack>(slots.size + 1)
        val encoded = arrayOfNulls<JsonElement>(slots.size + 1)
        var shape: Shape? = null
        var nameText: Component? = null
        var name: JsonElement? = null
        var displayText: Component? = null
        var text: JsonElement? = null
        var json: JsonObject? = null

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

    private val passes = Passes<Entry>()

    fun pass(level: ClientLevel, player: Player, mapIds: MutableSet<MapId>): Map<String, JsonElement> {
        val changed = LinkedHashMap<String, JsonElement>()
        for (entity in level.entitiesForRendering()) {
            if (entity === player) continue
            entry(entity, mapIds)?.let { changed[entity.id.toString()] = it }
        }
        return passes.end(changed)
    }

    /** @keep One call per entity rather than a loop body so that the JIT compiles it within a few passes: [pass] runs only four times a second, which leaves a loop inside it interpreted for the first minute of a recording. */
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

        val entry = passes.previous(entity.id) ?: Entry()
        passes.keep(entity.id, entry)
        var changed = false
        if (living != null) slots.forEachIndexed { index, slot -> if (entry.stack(index, living.getItemBySlot(slot).takeUnless { it.isEmpty })) changed = true }
        if (entry.stack(shown, item)) changed = true
        val shape = Shape(
            entity.type,
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
        if (json == entry.json) return null
        entry.json = json
        return json
    }
}
