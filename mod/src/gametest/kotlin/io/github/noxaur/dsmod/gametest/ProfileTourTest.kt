package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * Every tab and every page of the profile viewer in [theme], with the mouse off the panel, for reading the whole
 * screen. Checks too that the pets come with the profile, so their tab fetches nothing. One test per theme keeps
 * each under the game test cap.
 */
abstract class ProfileTour(private val theme: Theme) : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        OnProfiles(context).use { profiles ->
            context.worldBuilder().create().use {
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
                profiles.open("Testwyrm")
                for (name in profiles.tabs()) {
                    profiles.tab(name)
                    profiles.move(0, 0)
                    val slug = name.lowercase().replace(' ', '-')
                    val pages = profiles.pages()
                    for (page in pages.ifEmpty { listOf("1") }) {
                        if (pages.isNotEmpty()) profiles.press(page)
                        profiles.move(0, 0)
                        context.waitTicks(5)
                        context.takeScreenshot("tour-${theme.name.lowercase()}-$slug${if (page != "1") "-$page" else ""}")
                    }
                }
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
                check(profiles.asked.none { "/containers/pets" in it }) { "The pets were fetched" }
            }
        }
    }
}

class ProfileTourTest : ProfileTour(Theme.CHARCOAL)

class ProfileTourVanillaTest : ProfileTour(Theme.VANILLA)
