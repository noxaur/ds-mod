package io.github.noxaur.dragen.debug.zone

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.hud.hud
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.world.Cuboid
import io.github.noxaur.dragen.core.world.Mark
import io.github.noxaur.dragen.core.world.Point
import io.github.noxaur.dragen.core.world.Waypoint
import io.github.noxaur.dragen.core.world.WaypointLook
import io.github.noxaur.dragen.edge.Keys
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ZoneTool(config: DragenConfig) : HasFeature {
    private val pick = ZonePick()
    private var picking = false
    private var held = false

    override val feature = Feature(
        config, "zone-picker", Area.ANYWHERE, Store.DEV,
        "Press the key to pick a zone: left click sets corner 1, right click corner 2, and scrolling moves the last corner the way you look. Save it with /dszone save <name>.",
        default = true, name = "Zone Picker", hud = { block() }, marks = { marks() },
    )

    private val key = feature.keys("zonePickerKey", "Picker Key", "Pick the key that turns zone picking on and off.", GLFW.GLFW_KEY_Z, clicks = false)

    private fun block(): HudBlock? {
        if (!picking && pick.first == null && pick.second == null) return null
        return hud("Zone Picker") {
            row("Mode") { if (picking) value("picking, ${Keys.name(key.value)} to stop") else muted("off, ${Keys.name(key.value)} to pick") }
            row("Corner 1") { corner(1)?.let { value(it) } ?: muted("left click") }
            row("Corner 2") { corner(2)?.let { value(it) } ?: muted("right click") }
            pick.area?.let { area ->
                val (x, y, z) = ZonePick.size(area)
                row("Size") { value("$x × $y × $z") }
            }
            row("Place") { feature.areaName?.let { value(it) } ?: muted("unknown") }
        }
    }

    private fun corner(which: Int): String? {
        val at = (if (which == 1) pick.first else pick.second) ?: return null
        return if (picking && pick.selected == which) "$at  ← scroll" else at.toString()
    }

    private fun marks(): List<Mark> {
        val marks = ArrayList<Mark>()
        pick.first?.let { marks += corner(it, "1", Role.GOOD) }
        pick.second?.let { marks += corner(it, "2", Role.BAD) }
        pick.area?.let { marks += Cuboid(Point(it.minX.toDouble(), it.minY.toDouble(), it.minZ.toDouble()), Point(it.maxX + 1.0, it.maxY + 1.0, it.maxZ + 1.0), Role.ACTION) }
        return marks
    }

    private fun corner(at: Corner, label: String, role: Role) =
        Waypoint(Point(at.x + 0.5, at.y + 0.5, at.z + 0.5), "$label: $at", role, look = WaypointLook.OUTLINE, distance = false)

    // @keep The crosshair's own target stops at reach, so a corner is found by a longer ray, or is the block the player stands in.
    private fun target(client: Minecraft): Corner? {
        val player = client.player ?: return null
        val hit = player.pick(REACH, 0f, false)
        val pos = if (hit.type == HitResult.Type.BLOCK) (hit as BlockHitResult).blockPos else player.blockPosition()
        return Corner(pos.x, pos.y, pos.z)
    }

    fun tick(client: Minecraft) {
        val down = client.gui.screen() == null && Keys.held(client, key.value)
        if (down && !held && feature.on) {
            picking = !picking
            feature.redraw()
        }
        held = down
        if (picking && (!feature.on || client.player == null)) {
            picking = false
            feature.redraw()
        }
    }

    // @keep While picking, a click or scroll in the world sets or moves a corner, and the game never sees it, so it neither hits nor changes the held slot.
    fun button(button: Int, action: Int): Boolean {
        val client = Minecraft.getInstance()
        if (!picking || client.gui.screen() != null) return false
        val which = when (button) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> 1
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 2
            else -> return false
        }
        if (action == GLFW.GLFW_PRESS) target(client)?.let {
            pick.set(which, it)
            feature.redraw()
        }
        return true
    }

    fun scroll(amount: Double): Boolean {
        val client = Minecraft.getInstance()
        if (!picking || client.gui.screen() != null || amount == 0.0) return false
        val look = client.player?.lookAngle ?: return true
        if (pick.nudge(look.x, look.y, look.z, if (amount > 0) 1 else -1)) feature.redraw()
        return true
    }

    private fun save(source: FabricClientCommandSource, name: String): Int {
        val area = pick.area ?: return 0.also { source.sendError(Component.literal("Set both corners first.")) }
        val line = ZonePick.constant(name, area, feature.areaName)
        val client = source.client
        client.keyboardHandler.clipboard = line
        val file = client.gameDirectory.toPath().resolve("dragen/zones.txt")
        Files.createDirectories(file.parent)
        Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        source.sendFeedback(Component.literal("Copied and saved to $file: $line"))
        return 1
    }

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val root = ClientCommands.literal("dszone")
        root.then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes { save(it.source, StringArgumentType.getString(it, "name")) }))
        root.then(
            ClientCommands.literal("clear").executes {
                pick.clear()
                feature.redraw()
                it.source.sendFeedback(Component.literal("Zone cleared."))
                1
            },
        )
        dispatcher.register(root)
    }

    private companion object {
        const val REACH = 128.0
    }
}
