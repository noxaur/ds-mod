package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

abstract class ProfileTour(private val theme: Theme) : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        OnProfiles(context).use { profiles ->
            context.worldBuilder().create().use {
                context.inTheme(theme) {
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
                }
                check(profiles.asked.none { "/containers/pets" in it }) { "The pets were fetched" }
            }
        }
    }
}

class ProfileTourTest : ProfileTour(Theme.CHARCOAL)

class ProfileTourVanillaTest : ProfileTour(Theme.VANILLA)

// evidence run, reverted before merge
