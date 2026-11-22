package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.capture.CaptureWriter
import io.github.noxaur.dsmod.core.capture.ChangeLog
import io.github.noxaur.dsmod.core.capture.RawRecord
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.edge.id
import io.github.noxaur.dsmod.edge.itemId
import io.github.noxaur.dsmod.edge.typeId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.block.state.BlockState
import java.nio.file.Path

private val eventComponents = setOf("minecraft:custom_name", "minecraft:lore", "minecraft:custom_data")

private const val OFFER_PERIOD_TICKS = 5

private fun Double.round2() = Math.round(this * 100) / 100.0

/** @keep The cached stacks are copies, because a stack can change in place. */
private class ItemCache {
    private var last = HashMap<Int, Pair<ItemStack, JsonElement>>()
    private var seen = HashMap<Int, Pair<ItemStack, JsonElement>>()

    fun encode(slot: Int, stack: ItemStack): JsonElement {
        val entry = last[slot]?.takeIf { ItemStack.matches(it.first, stack) } ?: (stack.copy() to stack.toCaptureJson())
        seen[slot] = entry
        return entry.second
    }

    fun pass() {
        last = seen.also { seen = last }
        seen.clear()
    }
}

private fun JsonObjectBuilder.entityFields(entity: Entity) {
    put("id", entity.id)
    put("entity", entity.typeId)
    put("name", entity.name.string)
    position(entity.x.round2(), entity.y.round2(), entity.z.round2())
}

class CaptureSession(dir: Path, private val game: Game) : AutoCloseable {
    private val writer = CaptureWriter(dir)
    private val metricsSink = { snapshot: Metrics.Snapshot ->
        writer.record(RawRecord.METRICS) { Json.encodeToJsonElement(Metrics.Snapshot.serializer(), snapshot).jsonObject }
    }
    private val changes = ChangeLog { type, data -> writer.record(type) { data } }
    private val blockScan = BlockScan(changes)
    private val maps = MapRecorder(writer)
    private val items = ItemCache()
    private val entities = EntityRecorder()
    private var ticks = 0
    private var titleTime = 0

    init {
        game.tap = writer::record
        game.metrics.sinks += metricsSink
    }

    val file: Path get() = writer.file

    fun record(type: RawRecord, data: () -> JsonObject) = writer.record(type, data)

    private inline fun recordNow(type: RawRecord, build: JsonObjectBuilder.() -> Unit) {
        val data = buildJsonObject(build)
        writer.record(type) { data }
    }

    fun endedClientTick() = writer.endTick()

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val hud = client.gui.hud

        val retitled = hud.titleTime > titleTime
        if (hud.titleTime > 0) {
            val title = buildJsonObject {
                hud.title?.let { put("title", it.toCaptureJson()) }
                hud.subtitle?.let { put("subtitle", it.toCaptureJson()) }
            }
            changes.offer(RawRecord.TITLE, title, force = retitled)
        }
        titleTime = hud.titleTime

        blockScan.tick(level, player)

        if (ticks++ % OFFER_PERIOD_TICKS != 0) return

        changes.offer(
            RawRecord.BOSSBARS,
            buildJsonObject {
                put(
                    "bars",
                    buildJsonArray {
                        hud.bossOverlay.events.values.forEach { bar ->
                            add(buildJsonObject {
                                put("progress", bar.progress)
                                put("color", bar.color.name)
                                bar.name.toCaptureJson().forEach { (key, value) -> put(key, value) }
                            })
                        }
                    },
                )
            },
        )

        // @keep The tablist event carries the rows. The header and footer are client-only, so they are recorded here.
        changes.offer(
            RawRecord.TABHEADER,
            buildJsonObject {
                hud.tabList.header?.let { put("header", it.toCaptureJson()) }
                hud.tabList.footer?.let { put("footer", it.toCaptureJson()) }
            },
        )

        val menu = player.containerMenu
        game.metrics.time("capture-items") {
            changes.offerEntries(
                RawRecord.ITEMCOMPONENTS,
                buildJsonObject {
                    put("id", menu.containerId)
                    put("menu", menu.menuType?.let(BuiltInRegistries.MENU::getKey)?.toString() ?: "inventory")
                },
                buildMap<String, JsonElement> {
                    menu.slots.forEachIndexed { index, slot ->
                        if (slot.item.isEmpty) return@forEachIndexed
                        val components = (items.encode(index, slot.item) as? JsonObject)?.get("components") as? JsonObject ?: return@forEachIndexed
                        val rest = components.filterKeys { it !in eventComponents }
                        if (rest.isNotEmpty()) put(index.toString(), JsonObject(rest))
                    }
                },
            )
        }

        val mapIds = LinkedHashSet<MapId>()
        game.metrics.time("capture-entities") {
            player.inventory.nonEquipmentItems.plus(player.offhandItem).forEach { stack -> stack.get(DataComponents.MAP_ID)?.let(mapIds::add) }
            changes.offerChanges(RawRecord.ENTITIES, entities.pass(level, player, mapIds))
        }
        items.pass()
        maps.offer(level, mapIds)

        changes.offer(
            RawRecord.HUD,
            buildJsonObject {
                for (readout in game.readouts) {
                    val block = readout.hud ?: continue
                    put(readout.feature.id, buildJsonArray { block.rows.forEach { add(JsonPrimitive(it.text.plain)) } })
                }
            },
        )

        changes.offer(
            RawRecord.STATUS,
            buildJsonObject {
                position(player.blockX, player.blockY, player.blockZ)
                put("health", player.health)
                put("maxHealth", player.maxHealth)
                put("absorption", player.absorptionAmount)
                put("food", player.foodData.foodLevel)
                put("xpLevel", player.experienceLevel)
                put("xpProgress", player.experienceProgress)
            },
        )
    }

    fun packet(packet: Packet<*>) {
        when (packet) {
            is ClientboundSoundPacket -> {
                recordNow(RawRecord.SOUND) {
                    put("sound", packet.sound.value().location().toString())
                    put("source", packet.source.getName())
                    position(packet.x, packet.y, packet.z)
                    put("volume", packet.volume)
                    put("pitch", packet.pitch)
                }
            }
            is ClientboundLevelParticlesPacket -> {
                recordNow(RawRecord.PARTICLES) {
                    put("particle", BuiltInRegistries.PARTICLE_TYPE.getKey(packet.particle.type).toString())
                    position(packet.x, packet.y, packet.z)
                    put("count", packet.count)
                    put("speed", packet.maxSpeed)
                }
            }
            is ClientboundBlockUpdatePacket -> block(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::block)
        }
    }

    fun input(client: Minecraft, type: RawRecord) {
        val player = client.player ?: return
        val held = player.mainHandItem
        val hit = client.hitResult
        recordNow(type) {
            position(player.x.round2(), player.y.round2(), player.z.round2())
            put("yaw", player.yRot.toDouble().round2())
            put("pitch", player.xRot.toDouble().round2())
            put("item", held.id)
            put("name", held.hoverName.string)
            held.itemId?.let { put("itemId", it) }
            when {
                hit is EntityHitResult -> put("target", buildJsonObject { entityFields(hit.entity) })
                hit is BlockHitResult && hit.type == HitResult.Type.BLOCK ->
                    put("block", buildJsonObject { position(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) })
            }
        }
    }

    fun unload(entity: Entity) {
        if (entity !is LivingEntity || entity is Player) return
        recordNow(RawRecord.UNLOAD) {
            entityFields(entity)
            put("health", entity.health)
            put("reason", entity.removalReason?.name)
        }
    }

    private fun block(pos: BlockPos, state: BlockState) {
        // @keep The section packet reuses one mutable position, so read it now.
        recordNow(RawRecord.BLOCK) {
            position(pos.x, pos.y, pos.z)
            put("state", BlockStateParser.serialize(state))
        }
    }

    fun detach() {
        game.tap = null
        game.metrics.flush()
        game.metrics.sinks -= metricsSink
        blockScan.detach()
    }

    override fun close() = writer.close()
}
