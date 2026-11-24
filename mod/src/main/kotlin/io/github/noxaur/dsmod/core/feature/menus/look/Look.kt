package io.github.noxaur.dsmod.core.feature.menus.look

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.menus.look.fixedmenulayout.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.menus.look.hideglasspanes.HideGlassPanes
import io.github.noxaur.dsmod.core.feature.menus.look.keepcursor.KeepCursor
import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme

internal fun Groups.look() = group("Look") {
    feature(MenuTheme(c))
    feature(FixedMenuLayout(c))
    feature(HideGlassPanes(c))
    feature(KeepCursor(c))
}
