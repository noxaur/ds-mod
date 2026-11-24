package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.general.mouse.ctrlclick.CtrlClick
import io.github.noxaur.dsmod.core.feature.visuals.world.fullbright.Fullbright
import io.github.noxaur.dsmod.core.feature.visuals.world.hideexplosions.HideExplosions
import net.minecraft.client.gui.screens.Screen

object Hooks {
    private lateinit var fullbright: Fullbright
    private var ctrlClick: CtrlClick? = null
    private lateinit var hideExplosions: HideExplosions
    private lateinit var keys: (InputConstants.Key) -> Unit
    private lateinit var settings: (Screen?) -> Screen
    private lateinit var menu: () -> Unit

    lateinit var hud: DsModConfig.Hud
        private set

    fun bind(fullbright: Fullbright, ctrlClick: CtrlClick?, hideExplosions: HideExplosions) {
        this.fullbright = fullbright
        this.ctrlClick = ctrlClick
        this.hideExplosions = hideExplosions
    }

    fun register(hud: DsModConfig.Hud, pressed: (InputConstants.Key) -> Unit, menuChanged: () -> Unit, settingsScreen: (Screen?) -> Screen) {
        this.hud = hud
        keys = pressed
        menu = menuChanged
        settings = settingsScreen
    }

    @JvmStatic
    fun fullbright() = fullbright.feature.runs

    @JvmStatic
    fun ctrlClicks() = ctrlClick?.feature?.runs == true

    @JvmStatic
    fun hidesExplosions() = hideExplosions.feature.runs

    @JvmStatic
    fun pressed(key: InputConstants.Key) = keys(key)

    @JvmStatic
    fun menuChanged() = menu()

    fun settingsScreen(parent: Screen?) = settings(parent)
}
