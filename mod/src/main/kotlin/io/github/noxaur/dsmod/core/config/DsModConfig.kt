@file:OptIn(ExperimentalSerializationApi::class)

package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Theme
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** @keep A field's name is its key in config.json: a renamed field keeps its old name in @JsonNames, or players lose what
 * they set. */
@Serializable
class DsModConfig {
    @JvmField
    var favorites: List<String> = emptyList()

    @JvmField
    val general = General()

    @JvmField
    val hud = Hud()

    @Transient
    private val stores = Store.entries.associateWith(::Stored)

    fun stored(store: Store): Stored = stores.getValue(store)

    @Serializable
    class General {
        @JvmField
        var settingsKey = NO_KEY

        @JvmField
        var favoriteKey = KEY_F

        private companion object {
            const val KEY_F = 70
        }
    }

    @Serializable
    data class Position(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f)

    @Serializable
    class Hud {
        val positions = HashMap<String, Position>()

        @JvmField
        var theme = Theme.CHARCOAL

        @JvmField
        var editKey = NO_KEY

        @JvmField
        var scale = 1f
    }
}

/** @keep DragSim Utils and Odin open their settings on Right Shift, so a default of it would open two screens with one press. */
private const val NO_KEY = -1
