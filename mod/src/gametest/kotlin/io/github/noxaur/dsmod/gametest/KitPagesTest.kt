package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class KitPagesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = KitTest.inKit(context) {
        for (page in listOf(SettingsGalleryScreen.Page.LISTS, SettingsGalleryScreen.Page.PANELS)) {
            context.setScreen { SettingsGalleryScreen(page) }
            context.waitTicks(2)
            context.point(0, 0, settle = 2)
            val name = page.name.lowercase()
            context.takeScreenshot("kit-$name-charcoal")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-$name-vanilla") }
        }
    }
}
