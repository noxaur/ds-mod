package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.general.abilitycooldowns.AbilityCooldowns
import io.github.noxaur.dsmod.core.feature.general.animations.Animations
import io.github.noxaur.dsmod.core.feature.general.autoclick.AutoClick
import io.github.noxaur.dsmod.core.feature.general.etherwarpoutline.EtherwarpOutline
import io.github.noxaur.dsmod.core.feature.general.itemoverview.ItemOverview
import io.github.noxaur.dsmod.core.feature.general.mouse.mouse
import io.github.noxaur.dsmod.core.feature.general.playermodel.PlayerModel
import io.github.noxaur.dsmod.core.feature.general.profileviewer.ProfileViewer
import io.github.noxaur.dsmod.core.feature.general.server.server
import io.github.noxaur.dsmod.core.feature.general.settings.settings
import io.github.noxaur.dsmod.core.feature.general.waypoints.Waypoints
import io.github.noxaur.dsmod.core.feature.general.world.world
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.general() = section("General", Role.VALUE) {
    server()
    settings()
    mouse()
    world()
    group(EtherwarpOutline(c), "Etherwarp")
    group(AutoClick(c))
    group(AbilityCooldowns(c))
    group(Animations(c))
    group(PlayerModel(c))
    group(ProfileViewer(c))
    group(ItemOverview(c))
    group(Waypoints(c))
}
