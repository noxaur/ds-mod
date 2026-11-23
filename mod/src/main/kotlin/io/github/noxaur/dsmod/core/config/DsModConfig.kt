package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Theme
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class DsModConfig {
    @JvmField
    var favorites: List<String> = emptyList()

    @Transient
    private val stores = Store.entries.associateWith(::Stored)

    fun stored(store: Store): Stored = stores.getValue(store)

    @Transient
    val general = General(stored(Store.GENERAL))

    @Transient
    val hud = Hud(stored(Store.HUD))

    class General(stored: Stored) {
        val settingsKey = stored.slot("settingsKey", NO_KEY)

        val favoriteKey = stored.slot("favoriteKey", KEY_F)

        val recentColors = stored.slot("recentColors", emptyList<Int>())

        private companion object {
            const val KEY_F = 70
        }
    }

    @Serializable
    data class Position(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f)

    class Hud(stored: Stored) {
        val positions = stored.slot("positions", emptyMap<String, Position>())

        val theme = stored.slot("theme", Theme.CHARCOAL)

        val editKey = stored.slot("editKey", NO_KEY)

        val scale = stored.slot("scale", 1f)
    }
}

/** @keep DragSim Utils and Odin open their settings on Right Shift, so a default of it would open two screens with one press. */
private const val NO_KEY = -1
