package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitTab
import kotlinx.serialization.builtins.serializer
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class BetterUiTest : FabricClientGameTest {
    private data class Shown(val goBack: Boolean, val tabs: Boolean)

    private fun shown(context: ClientGameTestContext) =
        Shown(context.goBackButton() != null, ServerMenus.widget<KitTab>(context) != null)

    private fun betterUi(context: ClientGameTestContext, on: Boolean) = context.runOnClient<Nothing> {
        DsModClient.settings.stored(Store.BETTER_UI).slot("betterUi", true, Boolean.serializer(), emptyList()).value = on
    }

    override fun runTest(context: ClientGameTestContext) {
        context.withCommandMenu("storage", 6, "Storage", goBack = false) { world ->
            context.sendMenuCommand("storage", "Storage")
            ServerMenus.open(context, world, 4, "Backpack Slot 1", goBack = false)
            val both = Shown(goBack = true, tabs = true)
            check(shown(context) == both) { "With Better UI on the menu shows ${shown(context)}" }
            try {
                betterUi(context, false)
                context.waitTicks(2)
                check(shown(context) == Shown(goBack = false, tabs = false)) { "With Better UI off the menu shows ${shown(context)}" }
            } finally {
                betterUi(context, true)
            }
            context.waitTicks(2)
            check(shown(context) == both) { "With Better UI on again the menu shows ${shown(context)}" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
