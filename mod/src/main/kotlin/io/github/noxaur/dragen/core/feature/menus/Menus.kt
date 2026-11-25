package io.github.noxaur.dragen.core.feature.menus

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.menus.betterui.betterUi
import io.github.noxaur.dragen.core.feature.menus.helpers.helpers
import io.github.noxaur.dragen.core.feature.menus.keepcursor.KeepCursor
import io.github.noxaur.dragen.core.feature.menus.petsmenu.PetsMenu
import io.github.noxaur.dragen.core.feature.menus.quieterafk.QuieterAfk
import io.github.noxaur.dragen.core.feature.menus.wardrobekeys.WardrobeKeys
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.menus() = section("Menus", Role.LEGENDARY) {
    group(KeepCursor(c))
    betterUi()
    group(WardrobeKeys(c))
    group(PetsMenu(c))
    group(QuieterAfk(c))
    helpers()
}
