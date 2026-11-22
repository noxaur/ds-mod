package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import java.util.Optional

class ProfileStatesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        OnProfiles(context).use { profiles ->
            context.worldBuilder().create().use { world ->
                context.runOnClient<Nothing> { it.gui.hud.chat.clearMessages(false) }
                world.server.runCommand("""tellraw @a [{"text":"[MVP+] ","color":"aqua"},{"text":"Testwyrm","color":"aqua"},{"text":": anyone for F7?","color":"white"}]""")
                context.waitTicks(5)
                val linked = context.computeOnClient<Map<String, Pair<String?, String?>>, Nothing> {
                    val line = (it.gui.hud.chat as ChatComponentAccessor).allMessages.first().content()
                    val pieces = LinkedHashMap<String, Pair<String?, String?>>()
                    line.visit({ style, piece ->
                        val command = (style.clickEvent as? ClickEvent.RunCommand)?.command()
                        val hover = (style.hoverEvent as? HoverEvent.ShowText)?.value?.string
                        pieces[piece] = command to hover
                        Optional.empty<Unit>()
                    }, Style.EMPTY)
                    pieces
                }
                check(linked == mapOf(
                    "[MVP+] " to (null to null),
                    "Testwyrm" to ("/dsmod pv Testwyrm" to "Click to view Testwyrm's profile"),
                    ": anyone for F7?" to (null to null),
                )) { "The delivered line linked as $linked" }

                context.setScreen { ChatScreen("", false) }
                val height = context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight }
                val nameX = 52.0
                val nameY = (height - 45).toDouble()
                profiles.move(nameX.toInt(), nameY.toInt())
                context.takeScreenshot("profile-chat-name")
                val clicked = context.computeOnClient<Boolean, Nothing> { client ->
                    (client.gui.screen() as ChatScreen).mouseClicked(MouseButtonEvent(nameX, nameY, MouseButtonInfo(0, 0)), false)
                }
                check(clicked) { "The chat screen did not take the click on the name" }
                context.waitForScreen(ProfileScreen::class.java)

                context.setScreen { null }
                profiles.command("pv Slow")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitTicks(10)
                context.takeScreenshot("state-loading")
                context.waitUntil("the slow profile loaded") { profiles.screen(it).widgets<KitTab>().isNotEmpty() }

                context.setScreen { null }
                profiles.command("pv Down")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitUntil("the retry button showed") { profiles.screen(it).widgets<AbstractWidget>().any { w -> w.message.string == "Retry" } }
                context.takeScreenshot("state-upstream-down")

                profiles.open("Stale")
                val corner = context.computeOnClient<Pair<Int, Int>, Nothing> { profiles.screen(it).contentX - 55 to profiles.screen(it).viewY + 30 }
                profiles.move(corner.first, corner.second)
                context.takeScreenshot("state-stale")
            }
        }
    }
}
