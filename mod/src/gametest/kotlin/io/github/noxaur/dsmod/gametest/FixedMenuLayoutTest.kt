package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

class FixedMenuLayoutTest : FabricClientGameTest {
    private fun walk(context: ClientGameTestContext, world: TestSingleplayerContext, shot: String) =
        listOf(6, 4, 3).map { rows ->
            ServerMenus.open(context, world, rows, "$rows Rows")
            context.takeScreenshot("$shot-$rows-rows")
            context.slotCorner(rows * 9 - 6) to context.slotCorner(rows * 9)
        }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val stable = walk(context, world, "stable")
            check(stable.distinct().size == 1) { "Go Back and the inventory moved between menus: $stable" }

            try {
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
                val vanilla = walk(context, world, "stable-vanilla")
                check(vanilla.distinct().size == 1) { "Without Menu Theme, Go Back and the inventory moved between menus: $vanilla" }

                context.runOnClient<Nothing> { DsModClient.feature<FixedMenuLayout>().feature.enabled = false }
                val centered = walk(context, world, "centered")
                check(centered.distinct().size == 3) { "With Fixed Menu Layout off the menus should move as vanilla's do: $centered" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.feature<FixedMenuLayout>().feature.enabled = true
                    DsModClient.feature<MenuTheme>().feature.enabled = true
                }
            }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
