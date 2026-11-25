package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.utilities.chat.profileviewer.ProfileViewer
import io.github.noxaur.dragen.core.ui.Theme
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.profile.ProfileScreen
import io.github.noxaur.dragen.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import java.util.concurrent.CopyOnWriteArrayList

class ProfileViewerTest : FabricClientGameTest {
    private val hooked = CopyOnWriteArrayList<String>()

    override fun runTest(context: ClientGameTestContext) {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty("item_data")?.getStringOr("item_id", "")
            if (!id.isNullOrEmpty()) {
                hooked += id
                lines += Component.literal("Added by another mod")
            }
        }
        OnProfiles(context).use { profiles ->
            context.worldBuilder().create().use {
                context.waitTicks(20)
                profiles.command("pv 12")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv 12 opened the profile viewer" }

                profiles.open("Testwyrm")
                context.waitTicks(10)
                context.takeScreenshot("profile-overview")
                check(context.computeOnClient<Boolean, Nothing> { !profiles.screen(it).overflows }) { "The overview needs a scrollbar" }
                context.inTheme(Theme.VANILLA) { context.takeScreenshot("profile-overview-vanilla") }

                val (contentX, viewY) = context.computeOnClient<Pair<Int, Int>, Nothing> { profiles.screen(it).contentX to profiles.screen(it).viewY }
                profiles.move(contentX - 57, viewY + 60)
                context.input.holdMouse(0)
                profiles.move(contentX - 20, viewY + 50)
                context.takeScreenshot("profile-model-turned")
                context.input.releaseMouse(0)
                profiles.move(contentX + 40, viewY + 18)
                context.takeScreenshot("profile-skill-tooltip")

                profiles.move(contentX + 200, viewY + 150)
                context.takeScreenshot("profile-kills-tooltip")
                profiles.tab("Dungeons")
                context.waitTicks(2)
                context.takeScreenshot("profile-dungeons")
                check(context.computeOnClient<Boolean, Nothing> { !profiles.screen(it).overflows }) { "The dungeons tab needs a scrollbar" }

                profiles.tab("Player Stats")
                profiles.move(contentX + 20, viewY + 28)
                context.takeScreenshot("profile-stat-tooltip")

                profiles.tab("Inventory")
                context.waitUntil("the inventory loaded") { profiles.asked.any { "/containers/inventory" in it } }
                context.waitTicks(5)
                val gridY = context.computeOnClient<Int, Nothing> { profiles.screen(it).viewY }
                hooked.clear()
                val item = profiles.hoverItem(contentX, gridY, hooked)
                context.takeScreenshot("profile-inventory-tooltip")
                check(item == "reinforced_plating") { "The first item hovered is $item" }
                check("reinforced_plating" in hooked) { "Another mod's tooltip hook saw only $hooked" }

                profiles.tab("Pets")
                context.waitTicks(2)
                profiles.move(contentX + 200, gridY + 45)
                context.takeScreenshot("profile-pet-progress-tooltip")

                profiles.tab("Storage")
                context.waitTicks(5)
                profiles.press("2")
                context.waitTicks(5)
                context.takeScreenshot("profile-storage-2")
                profiles.tab("Inventory")
                context.waitTicks(2)
                val inventories = profiles.asked.count { "/containers/inventory" in it }
                check(inventories == 1) { "The inventory was asked for $inventories times" }
                check(profiles.asked.count { "/containers/storage:1" in it } == 1) { "Storage 2 was asked for ${profiles.asked.filter { "storage" in it }}" }

                context.setScreen { null }
                profiles.command("pv Nobody")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitTicks(10)
                context.takeScreenshot("profile-not-found")

                context.setScreen { null }
                val containers = profiles.asked.count { "/containers/" in it }
                profiles.command("dragen pv Hidden")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitUntil("the hidden profile loaded") { profiles.screen(it).widgets<KitTab>().isNotEmpty() }
                profiles.tab("Inventory")
                context.waitTicks(5)
                context.takeScreenshot("profile-hidden")
                check(profiles.asked.count { "/containers/" in it } == containers) { "A hidden container was fetched" }
                check(context.computeOnClient<Boolean, Nothing> { client -> profiles.screen(client).widgets<KitTab>().any { it.message.string == "Pets" } }) {
                    "A hidden inventory hid the pets"
                }

                context.setScreen { null }
                context.runOnClient<Nothing> { DragenClient.feature<ProfileViewer>().feature.enabled = false }
                profiles.command("pv Testwyrm")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv opened with Profile Viewer off" }
                context.runOnClient<Nothing> { DragenClient.feature<ProfileViewer>().feature.enabled = true }
            }
        }
    }
}
