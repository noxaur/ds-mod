package io.github.noxaur.dragen.core.feature.menus.helpers.profilecooldown

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.player.PlayerData

class ProfileCooldown(config: DragenConfig) : HasFeature {
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
