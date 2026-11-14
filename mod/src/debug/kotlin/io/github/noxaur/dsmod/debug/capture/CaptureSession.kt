package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.Metrics
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
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.nio.file.Path

/** The item components that the `slot` event already carries. */
private val eventComponents = setOf("minecraft:custom_name", "minecraft:lore", "minecraft:custom_data")

/** Unnamed, unequipped mobs that a puzzle still reads. */
private fun watched(entity: Entity) = entity is Silverfish || entity is Creeper

private fun Double.round2() = Math.round(this * 100) / 100.0

private fun JsonObjectBuilder.entityFields(entity: Entity) {
    put("id", entity.id)
    put("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
    put("name", entity.name.string)
    put("x", entity.x.round2())
    put("y", entity.y.round2())
    put("z", entity.z.round2())
}

/**
 * One running capture: the events [game] posts go straight to [writer], and [tick] adds what only exists
 * as client state, plus what the mod showed (`hud`) and how long it took (`metrics`).
 */
class CaptureSession(dir: Path, private val game: Game) : AutoCloseable {
    private val writer = CaptureWriter(dir)
    private val metricsSink = { snapshot: Metrics.Snapshot ->
        writer.record("metrics") { Json.encodeToJsonElement(Metrics.Snapshot.serializer(), snapshot).jsonObject }
    }
    private val changes = ChangeLog { type, data -> writer.record(type) { data } }
    private val blockScan = BlockScan(changes)
    private val maps = MapRecorder(writer)
    private var ticks = 0
    private var titleTime = 0

    init {
        game.tap = writer::record
        game.metrics.sinks += metricsSink
    }

    val file: Path get() = writer.file

    fun record(type: String, data: () -> JsonObject) = writer.record(type, data)

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val hud = client.gui.hud

        // A timer that jumped up means the server sent the text again, even if it is the same text.
        if (hud.titleTime > 0) {
            val title = buildJsonObject {
                hud.title?.let { put("title", it.toCaptureJson()) }
                hud.subtitle?.let { put("subtitle", it.toCaptureJson()) }
            }
            changes.offer("title", title, force = hud.titleTime > titleTime)
        }
        titleTime = hud.titleTime

        blockScan.tick(level, player)

        // The rest is compared four times a second, which is enough to catch every visible change.
        if (ticks++ % 5 != 0) return

        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        changes.offer(
            "sidebar",
            buildJsonObject {
                if (objective == null) return@buildJsonObject
                put("title", objective.displayName.toCaptureJson())
                put(
                    "lines",
                    buildJsonArray {
                        // The client draws each line as the team's prefix, the name and the team's suffix, then the formatted score.
                        val format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
                        scoreboard.listPlayerScores(objective)
                            .filter { !it.isHidden }
                            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner })
                            .take(15)
                            .forEach { score ->
                                val name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner), score.ownerName())
                                add(buildJsonObject {
                                    put("score", score.value)
                                    name.toCaptureJson().forEach { (key, value) -> put(key, value) }
                                    put("number", score.formatValue(format).toCaptureJson())
                                })
                            }
                    },
                )
            },
        )

        changes.offer(
            "bossbars",
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

        // The rows are the `tablist` event. The header and footer are private to the client and no feature reads them.
        changes.offer(
            "tabheader",
            buildJsonObject {
                hud.tabList.header?.let { put("header", it.toCaptureJson()) }
                hud.tabList.footer?.let { put("footer", it.toCaptureJson()) }
            },
        )

        // The `container` and `slot` events carry the id, count, name, lore and custom data. This is the rest.
        val menu = player.containerMenu
        changes.offerEntries(
            "itemcomponents",
            buildJsonObject {
                put("id", menu.containerId)
                // The player's own inventory has no menu type.
                put("menu", runCatching { BuiltInRegistries.MENU.getKey(menu.type).toString() }.getOrDefault("inventory"))
            },
            buildMap<String, JsonElement> {
                menu.slots.forEachIndexed { index, slot ->
                    if (slot.item.isEmpty) return@forEachIndexed
                    val components = (slot.item.toCaptureJson() as? JsonObject)?.get("components") as? JsonObject ?: return@forEachIndexed
                    val rest = components.filterKeys { it !in eventComponents }
                    if (rest.isNotEmpty()) put(index.toString(), JsonObject(rest))
                }
            },
        )

        // Players, named entities and anything holding or wearing an item. Servers build mobs and holograms from these.
        // Glowing entities, displays, item frames and the puzzle mobs come too, with what each one shows.
        val mapIds = LinkedHashSet<MapId>()
        player.inventory.nonEquipmentItems.plus(player.offhandItem).forEach { stack -> stack.get(DataComponents.MAP_ID)?.let(mapIds::add) }
        changes.offerEntries(
            "entities",
            JsonObject(emptyMap()),
            buildMap {
                for (entity in level.entitiesForRendering()) {
                    if (entity === player) continue
                    val equipment = (entity as? LivingEntity)?.let { living ->
                        EquipmentSlot.VALUES.filter { !living.getItemBySlot(it).isEmpty }
                            .associate { it.getName() to living.getItemBySlot(it).toCaptureJson() }
                    }.orEmpty()
                    val item = when (entity) {
                        is ItemFrame -> entity.item
                        is ItemEntity -> entity.item.takeIf { it.has(DataComponents.CUSTOM_NAME) }
                        is Display.ItemDisplay -> entity.itemStack
                        else -> null
                    }
                    item?.get(DataComponents.MAP_ID)?.let(mapIds::add)
                    val named = entity.hasCustomName() || entity is Player || equipment.isNotEmpty()
                    if (!named && !entity.isCurrentlyGlowing && item == null && entity !is Display && !watched(entity)) continue
                    put(
                        entity.id.toString(),
                        buildJsonObject {
                            put("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
                            // Whole blocks, so an entity that stands still records nothing new.
                            put("x", entity.blockX)
                            put("y", entity.blockY)
                            put("z", entity.blockZ)
                            put("nameVisible", entity.isCustomNameVisible)
                            // The type already says what an entity recorded only for its glow, item or text is.
                            if (named) put("name", entity.displayName.toCaptureJson())
                            if (entity.isCurrentlyGlowing) put("glowing", true)
                            if (equipment.isNotEmpty()) put("equipment", JsonObject(equipment))
                            if (item != null) put("item", item.toCaptureJson())
                            if (entity is ItemFrame) {
                                put("rotation", entity.rotation)
                                put("facing", entity.direction.getName())
                            }
                            if (entity is Display.TextDisplay) put("text", entity.text.toCaptureJson())
                        },
                    )
                }
            },
        )
        maps.offer(level, mapIds)

        changes.offer(
            "hud",
            buildJsonObject {
                for (readout in game.readouts) {
                    val block = readout.hud ?: continue
                    put(readout.feature.id, buildJsonArray { block.rows.forEach { add(JsonPrimitive(it.text.plain)) } })
                }
            },
        )

        changes.offer(
            "status",
            buildJsonObject {
                put("x", player.blockX)
                put("y", player.blockY)
                put("z", player.blockZ)
                put("health", player.health)
                put("maxHealth", player.maxHealth)
                put("absorption", player.absorptionAmount)
                put("food", player.foodData.foodLevel)
                put("xpLevel", player.experienceLevel)
                put("xpProgress", player.experienceProgress)
            },
        )
    }

    /** Server packets that leave nothing behind for [tick] to read. */
    fun packet(packet: Packet<*>) {
        when (packet) {
            is ClientboundSoundPacket -> {
                val data = buildJsonObject {
                    put("sound", packet.sound.value().location().toString())
                    put("source", packet.source.getName())
                    put("x", packet.x)
                    put("y", packet.y)
                    put("z", packet.z)
                    put("volume", packet.volume)
                    put("pitch", packet.pitch)
                }
                writer.record("sound") { data }
            }
            is ClientboundLevelParticlesPacket -> {
                val data = buildJsonObject {
                    put("particle", BuiltInRegistries.PARTICLE_TYPE.getKey(packet.particle.type).toString())
                    put("x", packet.x)
                    put("y", packet.y)
                    put("z", packet.z)
                    put("count", packet.count)
                    put("speed", packet.maxSpeed)
                }
                writer.record("particles") { data }
            }
            is ClientboundBlockUpdatePacket -> block(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::block)
        }
    }

    /**
     * The player's click, before the game decides what it does: `swing` for the attack button, `use` for
     * the use button, each with the held item and what the crosshair is on.
     */
    fun input(client: Minecraft, type: String) {
        val player = client.player ?: return
        val held = player.mainHandItem
        val hit = client.hitResult
        val data = buildJsonObject {
            put("x", player.x.round2())
            put("y", player.y.round2())
            put("z", player.z.round2())
            put("yaw", player.yRot.toDouble().round2())
            put("pitch", player.xRot.toDouble().round2())
            put("item", BuiltInRegistries.ITEM.getKey(held.item).toString())
            put("name", held.hoverName.string)
            held.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound("item_data")?.flatMap { it.getString("item_id") }
                ?.orElse(null)?.let { put("itemId", it) }
            when {
                hit is EntityHitResult -> put("target", buildJsonObject { entityFields(hit.entity) })
                hit is BlockHitResult && hit.type == HitResult.Type.BLOCK ->
                    put("block", buildJsonObject { put("x", hit.blockPos.x); put("y", hit.blockPos.y); put("z", hit.blockPos.z) })
            }
        }
        writer.record(type) { data }
    }

    /** A living entity the client dropped. The [Entity.getRemovalReason] says whether it died here, or the server removed it. */
    fun unload(entity: Entity) {
        if (entity !is LivingEntity || entity is Player) return
        val data = buildJsonObject {
            entityFields(entity)
            put("health", entity.health)
            put("reason", entity.removalReason?.name)
        }
        writer.record("unload") { data }
    }

    private fun block(pos: BlockPos, state: BlockState) {
        // The section packet reuses one mutable position, so read it now.
        val data = buildJsonObject {
            put("x", pos.x)
            put("y", pos.y)
            put("z", pos.z)
            put("state", BlockStateParser.serialize(state))
        }
        writer.record("block") { data }
    }

    override fun close() {
        game.tap = null
        game.metrics.flush()
        game.metrics.sinks -= metricsSink
        writer.close()
    }
}
