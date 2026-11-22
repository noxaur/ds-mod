package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Looks players up through `/pv` against [OnProfiles]' stub of the backend. Opens the overview, turns the model with
 * the real mouse, hovers a skill, opens the inventory and a storage page and hovers an item, and checks that each tab
 * asks for its container once. Checks that `/pv 12` is left to the server's vault command, that a missing player and
 * a hidden inventory each say so, and that `/pv` does nothing with the setting off. `ProfileTourTest` and
 * `ProfileStatesTest` cover the rest of the viewer.
 */
class ProfileViewerTest : FabricClientGameTest {
    /** The `item_id` of each stack whose tooltip another mod's hook saw, as DragSim Utils' hooks read it. */
    private val hooked = CopyOnWriteArrayList<String>()

    override fun runTest(context: ClientGameTestContext) {
        // Another mod adds to a real item's tooltip through Fabric's hook and reads the server's data from the stack.
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
                // The server's /pv <vault> is not ours, so nothing opens and the line goes on to the server.
                profiles.command("pv 12")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv 12 opened the profile viewer" }

                profiles.open("Testwyrm")
                context.waitTicks(10)
                context.takeScreenshot("profile-overview")
                check(context.computeOnClient<Boolean, Nothing> { !profiles.screen(it).overflows }) { "The overview needs a scrollbar" }
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
                context.waitTicks(2)
                context.takeScreenshot("profile-overview-vanilla")
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

                val (contentX, viewY) = context.computeOnClient<Pair<Int, Int>, Nothing> { profiles.screen(it).contentX to profiles.screen(it).viewY }
                // The model sits left of the content. A drag turns it, and the skill under the mouse shows its XP.
                profiles.move(contentX - 57, viewY + 60)
                context.input.holdMouse(0)
                profiles.move(contentX - 20, viewY + 50)
                context.takeScreenshot("profile-model-turned")
                context.input.releaseMouse(0)
                profiles.move(contentX + 40, viewY + 18)
                context.takeScreenshot("profile-skill-tooltip")

                // Kills past the first five are in the well's tooltip.
                profiles.move(contentX + 200, viewY + 150)
                context.takeScreenshot("profile-kills-tooltip")
                profiles.tab("Dungeons")
                context.waitTicks(2)
                context.takeScreenshot("profile-dungeons")
                check(context.computeOnClient<Boolean, Nothing> { !profiles.screen(it).overflows }) { "The dungeons tab needs a scrollbar" }

                // Each stat's hover breaks it down by the server's groups. Health is the second row.
                profiles.tab("Player Stats")
                profiles.move(contentX + 20, viewY + 28)
                context.takeScreenshot("profile-stat-tooltip")

                profiles.tab("Inventory")
                context.waitUntil("the inventory loaded") { profiles.asked.any { "/containers/inventory" in it } }
                context.waitTicks(5)
                // The other tabs have a title row, so their content starts lower than the overview's.
                val gridY = context.computeOnClient<Int, Nothing> { profiles.screen(it).viewY }
                val item = profiles.hoverItem(contentX, gridY)
                context.takeScreenshot("profile-inventory-tooltip")
                check(item == "reinforced_plating") { "The first item hovered is $item" }
                check("reinforced_plating" in hooked) { "Another mod's tooltip hook saw only $hooked" }

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
                profiles.command("dsmod pv Hidden")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitUntil("the hidden profile loaded") { profiles.screen(it).children().any { w -> w is KitTab } }
                profiles.tab("Inventory")
                context.waitTicks(5)
                context.takeScreenshot("profile-hidden")
                check(profiles.asked.count { "/containers/" in it } == containers) { "A hidden container was fetched" }
                check(context.computeOnClient<Boolean, Nothing> { client -> profiles.screen(client).children().any { (it as? KitTab)?.message?.string == "Pets" } }) {
                    "A hidden inventory hid the pets"
                }

                context.setScreen { null }
                context.runOnClient<Nothing> { DsModClient.feature<ProfileViewer>().feature.enabled = false }
                profiles.command("pv Testwyrm")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv opened with Profile Viewer off" }
                context.runOnClient<Nothing> { DsModClient.feature<ProfileViewer>().feature.enabled = true }
            }
        }
    }
}
