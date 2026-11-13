package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam
import java.nio.file.Path

/** One running capture: events go straight to [writer], and [tick] adds what only exists as client state. */
class CaptureSession(dir: Path) : AutoCloseable {
    private val writer = CaptureWriter(dir)
    private val changes = ChangeLog { type, data -> writer.record(type) { data } }
    private var ticks = 0
    private var actionBarTime = 0
    private var titleTime = 0

    val file: Path get() = writer.file

    fun record(type: String, data: () -> JsonObject) = writer.record(type, data)

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val hud = client.gui.hud

        // A timer that jumped up means the server sent the text again, even if it is the same text.
        hud.overlayMessageString?.takeIf { hud.overlayMessageTime > 0 }?.let {
            changes.offer("actionbar", it.toCaptureJson(), force = hud.overlayMessageTime > actionBarTime)
        }
        actionBarTime = hud.overlayMessageTime
        if (hud.titleTime > 0) {
            val title = buildJsonObject {
                hud.title?.let { put("title", it.toCaptureJson()) }
                hud.subtitle?.let { put("subtitle", it.toCaptureJson()) }
            }
            changes.offer("title", title, force = hud.titleTime > titleTime)
        }
        titleTime = hud.titleTime

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
                        scoreboard.listPlayerScores(objective)
                            .filter { !it.isHidden }
                            .sortedByDescending { it.value }
                            .forEach { score ->
                                val name = score.display
                                    ?: PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner), score.ownerName())
                                add(buildJsonObject {
                                    put("score", score.value)
                                    name.toCaptureJson().forEach { (key, value) -> put(key, value) }
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

        changes.offerEntries(
            "tablist",
            buildJsonObject {
                hud.tabList.header?.let { put("header", it.toCaptureJson()) }
                hud.tabList.footer?.let { put("footer", it.toCaptureJson()) }
            },
            client.connection?.listedOnlinePlayers.orEmpty().associate { info ->
                info.profile.id.toString() to buildJsonObject {
                    put("name", info.profile.name)
                    hud.tabList.getNameForDisplay(info).toCaptureJson().forEach { (key, value) -> put(key, value) }
                }
            },
        )

        val menu = player.containerMenu
        changes.offerEntries(
            "container",
            buildJsonObject {
                put("id", menu.containerId)
                // The player's own inventory has no menu type.
                put("menu", runCatching { BuiltInRegistries.MENU.getKey(menu.type).toString() }.getOrDefault("inventory"))
                client.gui.screen()?.title?.let { put("title", it.toCaptureJson()) }
                put("size", menu.slots.size)
            },
            buildMap<String, JsonElement> {
                menu.slots.forEachIndexed { index, slot -> if (!slot.item.isEmpty) put(index.toString(), slot.item.toCaptureJson()) }
                if (!menu.carried.isEmpty) put("carried", menu.carried.toCaptureJson())
            },
        )

        // Players, named entities and anything holding or wearing an item. Servers build mobs and holograms from these.
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
                    if (!entity.hasCustomName() && entity !is Player && equipment.isEmpty()) continue
                    put(
                        entity.id.toString(),
                        buildJsonObject {
                            put("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
                            put("nameVisible", entity.isCustomNameVisible)
                            put("name", entity.displayName.toCaptureJson())
                            if (equipment.isNotEmpty()) put("equipment", JsonObject(equipment))
                        },
                    )
                }
            },
        )

        changes.offer(
            "status",
            buildJsonObject {
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

    override fun close() = writer.close()
}
