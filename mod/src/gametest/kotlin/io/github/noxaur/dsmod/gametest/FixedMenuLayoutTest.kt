package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/**
 * Walks from a six-row menu to a four-row and a three-row one, as the server's menus go, and checks that Go Back and the
 * inventory stay put. With the setting off they move, as in vanilla. Read the screenshots: in the vanilla one the
 * texture must line up with its slots.
 */
class FixedMenuLayoutTest : FabricClientGameTest {
    /** Where Go Back and the first inventory slot sit in menus of six, four and three rows. */
    private fun walk(context: ClientGameTestContext, world: TestSingleplayerContext, shot: String) =
        listOf(6, 4, 3).map { rows ->
            ServerMenus.open(context, world, rows, "$rows Rows")
            context.takeScreenshot("$shot-$rows-rows")
            ServerMenus.corner(context, rows * 9 - 6) to ServerMenus.corner(context, rows * 9)
        }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val stable = walk(context, world, "stable")
            check(stable.distinct().size == 1) { "Go Back and the inventory moved between menus: $stable" }

            try {
                context.runOnClient<Nothing> { DsModClient.settings.inventory.menuTheme = false }
                val vanilla = walk(context, world, "stable-vanilla")
                check(vanilla.distinct().size == 1) { "Without Menu Theme, Go Back and the inventory moved between menus: $vanilla" }

                context.runOnClient<Nothing> { DsModClient.settings.inventory.fixedMenuLayout = false }
                val centered = walk(context, world, "centered")
                check(centered.distinct().size == 3) { "With Fixed Menu Layout off the menus should move as vanilla's do: $centered" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.settings.inventory.fixedMenuLayout = true
                    DsModClient.settings.inventory.menuTheme = true
                }
            }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
