package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.ui.Theme
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

fun <T> ClientGameTestContext.inTheme(theme: Theme, settle: Int = 2, act: () -> T): T {
    val was = computeOnClient<Theme, Nothing> { DragenClient.settings.hud.theme.value }
    runOnClient<Nothing> { DragenClient.settings.hud.theme.value = theme }
    waitTicks(settle)
    try {
        return act()
    } finally {
        runOnClient<Nothing> { DragenClient.settings.hud.theme.value = was }
        waitTicks(settle)
    }
}

fun ClientGameTestContext.shotInBothThemes(name: String, settle: Int = 2) {
    val theme = computeOnClient<Theme, Nothing> { DragenClient.settings.hud.theme.value }
    check(theme == Theme.CHARCOAL) { "Both themes start from Charcoal, and the theme is $theme" }
    takeScreenshot(name)
    inTheme(Theme.VANILLA, settle) { takeScreenshot("$name-vanilla") }
}
