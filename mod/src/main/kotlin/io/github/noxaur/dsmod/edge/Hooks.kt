package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.general.CtrlClick
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.general.HideExplosions
import net.minecraft.client.gui.screens.Screen

object Hooks {
    private lateinit var fullbright: Fullbright
    private lateinit var ctrlClick: CtrlClick
    private lateinit var hideExplosions: HideExplosions
    private lateinit var keys: (InputConstants.Key) -> Unit
    private lateinit var settings: (Screen?) -> Screen

    lateinit var hud: DsModConfig.Hud
        private set

    fun bind(fullbright: Fullbright, ctrlClick: CtrlClick, hideExplosions: HideExplosions) {
        this.fullbright = fullbright
        this.ctrlClick = ctrlClick
        this.hideExplosions = hideExplosions
    }

    fun register(hud: DsModConfig.Hud, pressed: (InputConstants.Key) -> Unit, settingsScreen: (Screen?) -> Screen) {
        this.hud = hud
        keys = pressed
        settings = settingsScreen
    }

    @JvmStatic
    fun fullbright() = fullbright.feature.runs

    @JvmStatic
    fun ctrlClicks() = ctrlClick.feature.runs

    @JvmStatic
    fun hidesExplosions() = hideExplosions.feature.runs

    @JvmStatic
    fun pressed(key: InputConstants.Key) = keys(key)

    fun settingsScreen(parent: Screen?) = settings(parent)
}
