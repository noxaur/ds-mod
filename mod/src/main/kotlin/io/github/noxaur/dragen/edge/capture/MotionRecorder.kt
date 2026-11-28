package io.github.noxaur.dragen.edge.capture

import io.github.noxaur.dragen.edge.typeId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import kotlin.math.roundToLong

private fun moves(entity: Entity) = entity is EnderDragon || entity is Projectile || entity is FallingBlockEntity || entity is LightningBolt

private fun Double.round(places: Int): Double {
    val scale = Math.pow(10.0, places.toDouble())
    return (this * scale).roundToLong() / scale
}

private fun JsonObjectBuilder.body(entity: Entity) {
    put("x", entity.x.round(3))
    put("y", entity.y.round(3))
    put("z", entity.z.round(3))
    val velocity = entity.deltaMovement
    put("vx", velocity.x.round(4))
    put("vy", velocity.y.round(4))
    put("vz", velocity.z.round(4))
    put("yaw", entity.yRot.toDouble().round(2))
    put("pitch", entity.xRot.toDouble().round(2))
}

/** @keep The entities record keeps whole blocks four times a second, too coarse to fit a path, so the dragon, its parts,
 * everything thrown and the player are kept here exactly, every tick. An entry is written only when it changed. */
class MotionRecorder {
    private val passes = Passes<JsonObject>()

    fun pass(level: ClientLevel, player: Player): Map<String, JsonElement> {
        val changed = LinkedHashMap<String, JsonElement>()
        for (entity in level.entitiesForRendering()) {
            if (entity !== player && !moves(entity)) continue
            val json = entry(entity, player)
            if (passes.previous(entity.id) != json) changed[entity.id.toString()] = json
            passes.keep(entity.id, json)
        }
        return passes.end(changed)
    }

    private fun entry(entity: Entity, player: Player) = buildJsonObject {
        put("entity", if (entity === player) "self" else entity.typeId)
        body(entity)
        if (entity === player) put("eye", player.eyeHeight.toDouble().round(3))
        (entity as? Projectile)?.owner?.let { put("owner", it.id) }
        (entity as? FallingBlockEntity)?.let { put("block", it.blockState.block.descriptionId) }
        if (entity is EnderDragon) {
            put(
                "parts",
                buildJsonArray {
                    for (part in entity.subEntities) {
                        add(buildJsonObject {
                            put("name", part.name)
                            put("x", part.x.round(3))
                            put("y", part.y.round(3))
                            put("z", part.z.round(3))
                            put("w", part.bbWidth.toDouble().round(2))
                            put("h", part.bbHeight.toDouble().round(2))
                        })
                    }
                },
            )
        }
    }
}
