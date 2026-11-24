package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.utilities.waypoints.Waypoints
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Colors
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.waypoint.SavedWaypoint
import io.github.noxaur.dsmod.core.world.WaypointLook
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.WaypointChat
import io.github.noxaur.dsmod.edge.World
import io.github.noxaur.dsmod.edge.ui.KitColorPicker
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

private const val BURIED = 0xFF55FFFF.toInt()

class WaypointsTest : FabricClientGameTest {
    private val waypoints get() = DsModClient.feature<Waypoints>()

    override fun runTest(context: ClientGameTestContext) {
        val chat = CopyOnWriteArrayList<String>()
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> chat += message.string }
        context.inWorld {
            context.waitTicks(20)
            looks(context)
            links(context)
            commands(context, chat)
            ListDriver(context).run()
            context.runOnClient<Nothing> {
                it.gui.setScreen(null)
                clear()
                waypoints.saved.value = listOf(SavedWaypoint("Anywhere", 0, 64, 0))
            }
        }
        context.inWorld {
            context.waitTicks(20)
            context.checkClient("A waypoint that shows everywhere was gone in the next world") { World.shown.any { (it as? World.ShownWaypoint)?.label?.name == "Anywhere" } }
            context.runOnClient<Nothing> { clear() }
        }
    }

    private fun clear() {
        waypoints.saved.value = emptyList()
        waypoints.groups.value = emptyList()
    }

    private fun looks(context: ClientGameTestContext) {
        context.runOnClient<Nothing> { client ->
            val player = checkNotNull(client.player)
            val colors = listOf(0x46AADCF8, 0x80FF5555.toInt(), 0x4055FF55, 0xC0FFAA00.toInt(), 0x46FFFFFF)
            waypoints.saved.value = WaypointLook.entries.mapIndexed { i, look ->
                SavedWaypoint(look.toString(), player.blockX - 20 + i * 10, player.blockY, player.blockZ + 14, color = colors[i], look = look, distance = i % 2 == 0)
            } + SavedWaypoint("Behind", player.blockX, player.blockY - 3, player.blockZ + 10, throughWalls = false, color = 0x80FF55FF.toInt()) +
                SavedWaypoint("Buried", player.blockX + 6, player.blockY - 3, player.blockZ + 10, look = WaypointLook.LABEL, color = BURIED)
        }
        context.waitTicks(30)
        val shot = context.screenshot("waypoints-looks")
        check(countNear(shot, BURIED and 0xFFFFFF) > 20) { "The label of a waypoint shown through walls is hidden by the ground over it" }
        context.checkClient("The saved waypoints are not all drawn") { World.shown.size >= WaypointLook.entries.size + 1 }
        // @keep Looking down at a beam close by, where the beam is shortened most and must still start above its label.
        context.runOnClient<Nothing> { client ->
            val player = checkNotNull(client.player)
            waypoints.saved.value = listOf(SavedWaypoint("Below", player.blockX, player.blockY - 3, player.blockZ + 4, color = 0xC0FFAA00.toInt()))
        }
        context.input.lookAt(0f, 50f)
        context.waitTicks(10)
        val down = context.screenshot("waypoints-looking-down")
        val muted = countNear(down, Role.MUTED.rgb, tolerance = 6)
        check(muted > 60) { "The distance under a label seen from above is buried under its own outline: $muted muted pixels" }
        context.input.lookAt(0f, 0f)
        context.runOnClient<Nothing> { clear() }
    }

    private fun links(context: ClientGameTestContext) {
        val commands = context.computeOnClient<List<String>, Nothing> {
            val linked = WaypointChat.links(Component.literal("Bob: [Nest] x: 12, y: 64, z: -30 in The End, and hi"))
            val found = ArrayList<String>()
            linked.visit({ style: Style, _: String ->
                (style.clickEvent as? ClickEvent.RunCommand)?.let { found += it.command() }
                Optional.empty<Unit>()
            }, Style.EMPTY)
            found
        }
        check(commands.distinct() == listOf("/dsmod waypoint show 12 64 -30 \"\" \"Nest\"")) { "The coordinates link to $commands" }
        val untouched = context.computeOnClient<Component, Nothing> { WaypointChat.links(Component.literal("Bob: 1 2 3")) }
        check(untouched.string == "Bob: 1 2 3" && untouched.style.clickEvent == null) { "Three bare numbers became a link" }
    }

    private fun commands(context: ClientGameTestContext, chat: List<String>) {
        fun run(line: String) {
            context.runOnClient<Nothing> { it.connection!!.sendCommand(line) }
            context.waitTicks(3)
        }
        run("dsmod waypoint show 12 64 -30 \"\" \"Nest\"")
        context.checkClient("Showing a clicked waypoint drew nothing") { World.shown.isNotEmpty() }
        check(waypoints.saved.value.isEmpty()) { "Showing a waypoint saved it" }
        run("dsmod waypoint save 12 64 -30 \"\" \"Nest\"")
        check(waypoints.saved.value.map { it.name to it.position } == listOf("Nest" to "12 64 -30")) { "Saving left ${waypoints.saved.value}" }
        run("dsmod waypoint save 12 64 -30 \"\" \"Nest\"")
        check(waypoints.saved.value.size == 1) { "Saving twice left ${waypoints.saved.value}" }

        val (x, y, z) = context.computeOnClient<Triple<Int, Int, Int>, Nothing> { checkNotNull(it.player).blockPosition().let { p -> Triple(p.x, p.y, p.z) } }
        run("dsmod wp add Here")
        check(waypoints.saved.value.last().let { it.name == "Here" && it.position == "$x $y $z" }) { "Adding where the player stands left ${waypoints.saved.value.last()}" }

        run("dsmod waypoint share Nest")
        context.waitTicks(5)
        check(chat.any { it.endsWith("[Nest] x: 12, y: 64, z: -30") }) { "Sharing Nest said $chat" }
        run("dsmod waypoint share")
        context.waitTicks(5)
        check(chat.any { it.endsWith("x: $x, y: $y, z: $z") }) { "Sharing where the player stands said $chat" }
        val said = chat.size
        run("dsmod waypoint share Nset")
        run("dsmod waypoint share chat Nest")
        context.waitTicks(5)
        check(chat.drop(said).map { it.substringAfter("> ") } == listOf("[Nest] x: 12, y: 64, z: -30")) { "A typo shared, or chat Nest did not: ${chat.drop(said)}" }
        context.runOnClient<Nothing> { clear() }
    }

    private class ListDriver(context: ClientGameTestContext) {
        private val list = BindsDriver(context, "savedWaypoints")
        private val ctx = context
        private val waypoints get() = DsModClient.feature<Waypoints>()

        private fun picker() = ctx.computeOnClient<KitColorPicker?, Nothing> { list.editorNow(it).picker() }

        fun run() = with(list) {
            open(ctx, SettingsTabs(at("Utilities")))
            list()
            press(line(foot()), "Add Waypoint")
            ctx.input.typeChars("Nest")
            key(GLFW.GLFW_KEY_ENTER)
            val added = waypoints.saved.value.single()
            check(added.name == "Nest" && added.color == waypoints.color.value && added.look == waypoints.look.value) { "Add Waypoint left $added" }

            press(lineOf(0), "Position", dx = 2)
            repeat(waypoints.saved.value.single().position.length) { ctx.input.pressKey(GLFW.GLFW_KEY_BACKSPACE) }
            ctx.input.typeChars("12 64 -30")
            key(GLFW.GLFW_KEY_ENTER)
            check(waypoints.saved.value.single().position == "12 64 -30") { "Typing the position left ${waypoints.saved.value.single()}" }

            press(lineOf(0), "Look")
            check(waypoints.saved.value.single().look == WaypointLook.BOX) { "A press on Look left ${waypoints.saved.value.single().look}" }

            press(lineOf(0), "Color")
            val picker = picker() ?: error("A press on the color opened no picker")
            ctx.shotInBothThemes("waypoints-color")
            val opacity = Colors.opacity(waypoints.saved.value.single().color)
            move(ctx.computeOnClient<Int, Nothing> { picker.left } + 20, ctx.computeOnClient<Int, Nothing> { picker.opacityY } + 4)
            ctx.input.scroll(1.0)
            ctx.waitTicks(2)
            check(picker() != null) { "The wheel over the picker, past the list, closed it" }
            check(Colors.opacity(waypoints.saved.value.single().color) == opacity + 1) { "The wheel over the opacity strip left ${Colors.show(waypoints.saved.value.single().color)}" }
            val red = ctx.computeOnClient<Int, Nothing> { picker.swatches.indexOf(0xFFFF5555.toInt()) }
            click(ctx.computeOnClient<Int, Nothing> { picker.swatchX(red) } + 2, ctx.computeOnClient<Int, Nothing> { picker.swatchY(red) } + 2)
            check(waypoints.saved.value.single().color == 0xFFFF5555.toInt()) { "A press on the red swatch left ${Integer.toHexString(waypoints.saved.value.single().color)}" }
            key(GLFW.GLFW_KEY_ESCAPE)
            check(picker() == null) { "Escape left the picker open" }

            press(lineOf(0), "Settings")
            val details = lineOf(0, details = true)
            press(details, "Through Walls")
            check(!waypoints.saved.value.single().throughWalls) { "Through Walls did not turn off" }
            ctx.shotInBothThemes("waypoints-list")
        }
    }
}
