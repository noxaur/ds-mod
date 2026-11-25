package io.github.noxaur.dragen.edge

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class ModMenu : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory(Hooks::settingsScreen)
}
