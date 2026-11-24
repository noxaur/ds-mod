package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.menus.helpers.profilecooldown.ProfileCooldown
import io.github.noxaur.dsmod.core.text.Role
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.ContainerScreen

object ProfileCooldowns {
    private lateinit var feature: ProfileCooldown

    fun bind(profileCooldown: ProfileCooldown) {
        feature = profileCooldown
    }

    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            ScreenEvents.afterBackground(screen).register { _, g, _, _, _ ->
                if (!feature.feature.runs) return@register
                val left = feature.left(screen.menu.containerId, System.currentTimeMillis()) ?: return@register
                if (left > 0) MenuTimers.draw(g, screen, MenuTimers.seconds(left), Role.BAD) else MenuTimers.draw(g, screen, "Ready", Role.GOOD)
            }
        }
    }
}
