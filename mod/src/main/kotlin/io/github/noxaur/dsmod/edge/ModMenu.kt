package io.github.noxaur.dsmod.edge

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/** Mod Menu's config button for ds-mod opens the settings over Mod Menu, which Done goes back to. */
class ModMenu : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory { parent -> SettingsScreen(parent) }
}
