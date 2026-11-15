package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Tabs along the top and bottom of menus that send the player's commands, and a search tab that dims the items that
 * do not match. It reads only the player's setup and the open menu's items, so all of it is `TabBar` and
 * `TabEditorScreen` in the edge, with the rules in `TabLayout` and `ItemSearch`. This class puts it in the list that
 * the settings, the docs check and the tests go through.
 */
class MenuTabs(toggle: KMutableProperty0<Boolean>) : Feature("menu-tabs", toggle, Area.ANYWHERE)
