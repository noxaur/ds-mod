package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

fun <T> ClientGameTestContext.inTheme(theme: Theme, settle: Int = 2, act: () -> T): T {
    val was = computeOnClient<Theme, Nothing> { DsModClient.settings.hud.theme }
    runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
    waitTicks(settle)
    try {
        return act()
    } finally {
        runOnClient<Nothing> { DsModClient.settings.hud.theme = was }
        waitTicks(settle)
    }
}

fun ClientGameTestContext.shotInBothThemes(name: String, settle: Int = 2) {
    val theme = computeOnClient<Theme, Nothing> { DsModClient.settings.hud.theme }
    check(theme == Theme.CHARCOAL) { "Both themes start from Charcoal, and the theme is $theme" }
    takeScreenshot(name)
    inTheme(Theme.VANILLA, settle) { takeScreenshot("$name-vanilla") }
}
