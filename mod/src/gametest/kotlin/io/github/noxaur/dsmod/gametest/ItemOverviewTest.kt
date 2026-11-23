package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.ItemOverview
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.overview.ItemOverviewScreen
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import io.github.noxaur.dsmod.edge.id
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.CopyOnWriteArrayList

class ItemOverviewTest : FabricClientGameTest {
    private val tooltips = CopyOnWriteArrayList<List<String>>()

    override fun runTest(context: ClientGameTestContext) {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (Minecraft.getInstance().gui.screen() is ItemOverviewScreen) {
                val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty("item_data")?.getStringOr("item_id", "").orEmpty()
                tooltips += listOf(id.ifEmpty { stack.id }) + lines.map { it.string }
            }
        }
        OnProfiles(context).use { profiles ->
            context.worldBuilder().create().use { world ->
                world.give(mapOf(0 to ItemStack(Items.DIAMOND, 64), 1 to ItemStack(Items.DIAMOND, 6)))
                context.waitTicks(20)

                fun open(line: String) {
                    context.setScreen { null }
                    profiles.command(line)
                    context.waitForScreen(ItemOverviewScreen::class.java)
                    context.waitUntil("the items loaded") { client -> (client.gui.screen() as? ItemOverviewScreen)?.widgets<KitTab>()?.size?.let { it > 2 } == true }
                    // @keep The diamonds unlock a recipe and an advancement, whose pop-ups would cover the sort buttons.
                    context.runOnClient<Nothing> { it.gui.toastManager().clear() }
                    context.waitTicks(3)
                }
                fun grid() = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
                    (client.gui.screen() as ItemOverviewScreen).let { s -> s.widgets<KitTab>().first().x + 3 to s.widgets<KitCycleButton<*>>().first().y + 20 }
                }
                fun search() = context.computeOnClient<String, Nothing> { client -> (client.gui.screen() as ItemOverviewScreen).widgets<KitTextField>().first().value }
                fun hover(index: Int): List<String>? {
                    tooltips.clear()
                    val (left, top) = grid()
                    profiles.move(left + index % 14 * 18 + 9, top + index / 14 * 18 + 9)
                    context.waitTicks(2)
                    return tooltips.lastOrNull()
                }

                open("dsmod items")
                context.shotInBothThemes("item-overview")
                check(profiles.asked.count { it.endsWith("/containers") } == 1) { "Every container was not asked for once: ${profiles.asked}" }
                check(profiles.asked.none { "/containers/" in it }) { "A single container was asked for: ${profiles.asked}" }

                val first = hover(0)
                context.takeScreenshot("item-overview-tooltip")
                check(first?.first() == "ritual_residue") { "The most kept item is ${first?.first()}" }
                check("Ender Chest: 128" in first.orEmpty()) { "The ender chest's two stacks were not merged: $first" }
                val diamonds = (0 until 14).firstNotNullOfOrNull { i -> hover(i)?.takeIf { it.first() == "minecraft:diamond" } }
                check(diamonds != null && "Inventory: 70" in diamonds) { "The live inventory's diamonds read $diamonds" }

                context.input.typeChars("ritual")
                context.waitTicks(2)
                context.takeScreenshot("item-overview-search")
                check(hover(0)?.first() == "ritual_residue") { "The search lost the residue" }
                check(hover(1) == null) { "The search left a second item" }

                val (left, top) = grid()
                context.click(left + 20, top - 12, button = 1)
                check(search() == "") { "A right click did not clear the search" }

                context.click(context.computeOnClient<KitTab, Nothing> { client -> (client.gui.screen() as ItemOverviewScreen).widgets<KitTab>().first { it.message.string == "Storage" } })
                context.waitTicks(2)
                context.takeScreenshot("item-overview-storage")
                check(hover(0)?.first() in setOf("aspect_of_the_end", "voidfang_dagger")) { "The Storage tab shows ${hover(0)?.first()}" }

                open("dsmod items dagger")
                check(search() == "dagger") { "The command's search was not typed in" }
                check(hover(0)?.first() == "voidfang_dagger" && hover(1) == null) { "The command's search shows ${hover(0)}" }

                open("dsmod items")
                val (gridLeft, gridTop) = grid()
                val diamondAt = (0 until 14).first { i -> hover(i)?.first() == "minecraft:diamond" }
                context.click(gridLeft + diamondAt % 14 * 18 + 9, gridTop + 9)
                context.checkClient("A click on an item only the inventory holds left the overview") { it.gui.screen() is ItemOverviewScreen }
                val residue = hover(0)
                check("Click to open Ender Chest." in residue.orEmpty()) { "The residue's tooltip offers no way to open it: $residue" }
                context.click(gridLeft + 9, gridTop + 9)
                context.waitTicks(2)
                context.checkClient("A click on an item in the ender chest kept the overview open") { it.gui.screen() !is ItemOverviewScreen }

                profiles.ownDown = true
                context.setScreen { null }
                profiles.command("dsmod items")
                context.waitForScreen(ItemOverviewScreen::class.java)
                context.waitTicks(10)
                context.runOnClient<Nothing> { it.gui.toastManager().clear() }
                context.takeScreenshot("item-overview-backend-down")
                check((0 until 14).any { i -> hover(i)?.first() == "minecraft:diamond" }) { "A failed backend hid the live inventory" }
                context.checkClient("A failed backend left a Retry button over the items") { client ->
                    (client.gui.screen() as ItemOverviewScreen).widgets<KitButton>().none { it.message.string == "Retry" }
                }
                profiles.ownDown = false

                context.setScreen { null }
                context.runOnClient<Nothing> { DsModClient.feature<ItemOverview>().feature.enabled = false }
                profiles.command("dsmod items")
                context.waitTicks(5)
                context.checkClient("/dsmod items opened with Item Overview off") { it.gui.screen() !is ItemOverviewScreen }
                context.runOnClient<Nothing> { DsModClient.feature<ItemOverview>().feature.enabled = true }
            }
        }
    }
}
