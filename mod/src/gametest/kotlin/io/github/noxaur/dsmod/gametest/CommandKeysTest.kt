package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.utilities.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.edge.CommandKeys
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

class CommandKeysTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val commandBinds = DsModClient.feature<CommandBinds>()
        commandBinds.binds.value = listOf(
            CommandBind("", "other", listOf(GLFW.GLFW_MOUSE_BUTTON_MIDDLE), where = Where.BOTH),
            CommandBind("Warp End", "/me bound", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6), group = "Dungeons"),
            CommandBind("", "/me seq", listOf(GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8), inOrder = true),
            CommandBind("", "/me end", listOf(GLFW.GLFW_KEY_F9), area = Area.END, group = "Dungeons"),
        )
        commandBinds.groups.value = listOf("Dungeons")
        context.worldBuilder().create().use { _ ->
            fun count(client: Minecraft, word: String) =
                (client.gui.hud.chat as ChatComponentAccessor).allMessages.count { it.content().string.endsWith(word) }
            fun said(word: String) = context.computeOnClient<Int, Nothing> { count(it, word) }
            fun echo(word: String, expected: Int) {
                if (expected > 0) context.waitFor({ count(it, word) >= expected }, ROUND_TRIP_TICKS)
                context.waitTicks(ROUND_TRIP_TICKS)
            }
            fun shiftF6() {
                context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
            }
            context.waitTicks(20)
            try {
                shiftF6()
                echo("bound", 1)
                check(said("bound") == 1) { "Shift + F6 sent /me bound ${said("bound")} times" }
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                echo("bound", 1)
                check(said("bound") == 1) { "F6 without Shift sent /me bound" }
                context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.input.holdKey(GLFW.GLFW_KEY_F6)
                context.runOnClient<Nothing> { CommandKeys.input(GLFW.GLFW_KEY_F6, GLFW.GLFW_REPEAT) }
                context.input.releaseKey(GLFW.GLFW_KEY_F6)
                context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                echo("bound", 2)
                check(said("bound") == 2) { "Holding Shift + F6 through a repeat sent /me bound ${said("bound") - 1} times" }

                context.input.pressKey(GLFW.GLFW_KEY_F8)
                context.input.pressKey(GLFW.GLFW_KEY_F7)
                echo("seq", 0)
                check(said("seq") == 0) { "F8 then F7 sent the sequence F7, F8" }
                context.input.pressKey(GLFW.GLFW_KEY_F7)
                context.input.pressKey(GLFW.GLFW_KEY_F8)
                echo("seq", 1)
                check(said("seq") == 1) { "F7 then F8 sent /me seq ${said("seq")} times" }

                context.input.pressKey(GLFW.GLFW_KEY_F9)
                echo("end", 0)
                check(said("end") == 0) { "F9, limited to The End, sent outside it" }

                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                echo("other", 1)
                check(said("other") == 1) { "The middle button said other ${said("other")} times in the world" }
                context.openInventory()
                context.waitTicks(2)
                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                context.waitTicks(2)
                shiftF6()
                echo("other", 2)
                check(said("other") == 2 && said("bound") == 2) { "In the inventory the menu bind said other ${said("other") - 1} times and the world bind sent ${said("bound") - 2}" }

                context.setScreen { ChatScreen("", false) }
                shiftF6()
                context.setScreen { null }
                echo("bound", 2)
                check(said("bound") == 2) { "Shift + F6 in the chat box sent /me bound" }

                commandBinds.offGroups.value = listOf("Dungeons")
                shiftF6()
                echo("bound", 2)
                check(said("bound") == 2) { "With Dungeons off Shift + F6 sent /me bound" }
                commandBinds.offGroups.value = emptyList()

                commandBinds.feature.enabled = false
                shiftF6()
                echo("bound", 2)
                check(said("bound") == 2) { "With Command Binds off Shift + F6 sent /me bound" }
            } finally {
                commandBinds.feature.enabled = true
                commandBinds.binds.value = emptyList()
                commandBinds.groups.value = emptyList()
                commandBinds.offGroups.value = emptyList()
                commandBinds.collapsedGroups.value = emptyList()
                context.setScreen { null }
            }
        }
    }

    private companion object {
        const val ROUND_TRIP_TICKS = 10
    }
}
