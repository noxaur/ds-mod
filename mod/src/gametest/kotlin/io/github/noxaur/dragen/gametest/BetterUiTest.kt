package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.ui.KitTab
import kotlinx.serialization.builtins.serializer
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class BetterUiTest : FabricClientGameTest {
    private fun tabs(context: ClientGameTestContext) = ServerMenus.widget<KitTab>(context) != null

    private fun betterUi(context: ClientGameTestContext, on: Boolean) = context.runOnClient<Nothing> {
        DragenClient.settings.stored(Store.BETTER_UI).slot("betterUi", true, Boolean.serializer(), emptyList()).value = on
    }

    override fun runTest(context: ClientGameTestContext) {
        context.withCommandMenu("storage", 6, "Storage", goBack = false) { world ->
            context.sendMenuCommand("storage", "Storage")
            ServerMenus.open(context, world, 4, "Backpack Slot 1", goBack = false)
            check(tabs(context)) { "With Better UI on the menu shows no tabs" }
            try {
                betterUi(context, false)
                context.waitTicks(2)
                check(!tabs(context)) { "With Better UI off the menu still shows tabs" }
            } finally {
                betterUi(context, true)
            }
            context.waitTicks(2)
            check(tabs(context)) { "With Better UI on again the menu shows no tabs" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
