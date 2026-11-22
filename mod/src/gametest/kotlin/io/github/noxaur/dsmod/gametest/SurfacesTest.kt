package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.hud
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Waypoint
import io.github.noxaur.dsmod.edge.Alerts
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.World
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.Items

class SurfacesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.inWorld { world ->
            world.commands("give @a diamond 3")
            context.waitTicks(20)

            context.runOnClient<Nothing> { client ->
                val player = checkNotNull(client.player)
                val look = player.lookAngle
                fun ahead(forward: Double, right: Double, up: Double = 0.0) =
                    Point(player.x + look.x * forward - look.z * right, player.y + up, player.z + look.z * forward + look.x * right)
                val stats = HudGroup("stats", 6, 6, background = true)
                Hud.show("scales", stats, hud("Scale Tracker") {
                    scaleRows()
                    items(listOf(Item("minecraft:prismarine_shard", 12), null, Item("minecraft:dragon_breath", 1)))
                })
                Hud.show("boss", stats, hud("Boss Timer") {
                    row("Next") { value("2m 5s") }
                })
                Hud.show("plain", HudGroup("plain", 6, 110), hud("No Background") {
                    row("Ping") { value(42); muted("ms") }
                })
                World.show("test", listOf(Waypoint(ahead(14.0, 4.0), "Dragon Nest")))
                Alerts.title(text { warning("Storage Full") }, text { body("Storage is full. Sell your scales to keep earning.") })
                Alerts.actionBar(text { muted("Next boss: "); value("2m 5s") })
                Chat.notice("Scale Tracker", text { value("Dragon Scale"); body(" x12") })
            }
            context.waitTicks(30)
            context.takeScreenshot("surfaces")

            context.openInventory()
            val diamondSlot = context.computeOnClient<Int, Nothing> { client ->
                val screen = client.gui.screen() as InventoryScreen
                val diamond = screen.menu.slots.first { it.item.item == Items.DIAMOND }
                Slots.mark(
                    "test",
                    mapOf(
                        diamond.index to SlotMark(Role.GOOD, text { good("1") }),
                        diamond.index - 18 to SlotMark(Role.BAD),
                        diamond.index - 9 to SlotMark(Role.LEGENDARY, rarity = true),
                        diamond.index + 1 to SlotMark(Role.ACTION, outline = true),
                    ),
                )
                diamond.index
            }
            context.hoverSlot(diamondSlot, settle = 10)
            context.takeScreenshot("container")

            context.runOnClient<Nothing> {
                check(it.gui.screen() is InventoryScreen) { "Inventory closed before the screenshot" }
                it.gui.setScreen(null)
                listOf("scales", "boss", "plain").forEach(Hud::hide)
                World.hide("test")
            }

            context.showDragonTimer(world)
            context.runOnClient<Nothing> { client ->
                client.options.guiScale().set(2)
                client.resizeGui()
                DsModClient.settings.hud.scale.value = 1.3f
            }
            context.waitTicks(5)
            context.runOnClient<Nothing> {
                val scale = Hud.find(DRAGON_TIMER)?.scale
                check(scale == 1.5f) { "The HUD scale snapped to $scale, not 1.5" }
            }
            context.takeScreenshot("hud-scale-1.5")
            context.runOnClient<Nothing> { DsModClient.settings.hud.scale.value = 1f }
            context.restoreDefaultGameOptions()
            context.runOnClient<Nothing> { it.resizeGui() }
        }
    }
}
