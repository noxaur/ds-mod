package io.github.noxaur.dsmod.core.feature.menus.profilecooldown

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.player.PlayerData

class ProfileCooldown(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "profile-cooldown", Area.ANYWHERE, Store.INVENTORY, "Show in Profile Management when you can switch profiles again.")

    fun left(menu: Int, now: Long): Long? {
        if (feature.container.title?.plain != PlayerData.PROFILES || feature.container.id != menu) return null
        val at = feature.player.switchesAt
        return (at - now).takeIf { at > 0 && it > -READY_MILLIS }
    }

    companion object {
        const val READY_MILLIS = 5000L
    }
}
