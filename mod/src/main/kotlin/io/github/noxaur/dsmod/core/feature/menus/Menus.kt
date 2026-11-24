package io.github.noxaur.dsmod.core.feature.menus

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.menus.betterui.betterUi
import io.github.noxaur.dsmod.core.feature.menus.helpers.helpers
import io.github.noxaur.dsmod.core.feature.menus.look.look
import io.github.noxaur.dsmod.core.feature.menus.petsmenu.PetsMenu
import io.github.noxaur.dsmod.core.feature.menus.wardrobekeys.WardrobeKeys
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.menus() = section("Menus", Role.LEGENDARY) {
    look()
    betterUi()
    group(WardrobeKeys(c))
    group(PetsMenu(c))
    helpers()
}
