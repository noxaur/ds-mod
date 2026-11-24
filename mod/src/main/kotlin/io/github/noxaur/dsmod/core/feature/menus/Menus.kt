package io.github.noxaur.dsmod.core.feature.menus

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.menus.betterui.betterUi
import io.github.noxaur.dsmod.core.feature.menus.forgecraft.ForgeCraft
import io.github.noxaur.dsmod.core.feature.menus.hotdplanner.HotdPlanner
import io.github.noxaur.dsmod.core.feature.menus.look.look
import io.github.noxaur.dsmod.core.feature.menus.petsmenu.PetsMenu
import io.github.noxaur.dsmod.core.feature.menus.profilecooldown.ProfileCooldown
import io.github.noxaur.dsmod.core.feature.menus.storageoverview.StorageOverview
import io.github.noxaur.dsmod.core.feature.menus.wardrobekeys.WardrobeKeys
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.menus() = section("Menus", Role.LEGENDARY) {
    look()
    betterUi()
    group(StorageOverview(c))
    group(ForgeCraft(c))
    group(HotdPlanner(c))
    group(WardrobeKeys(c))
    group(PetsMenu(c))
    group(ProfileCooldown(c))
}
