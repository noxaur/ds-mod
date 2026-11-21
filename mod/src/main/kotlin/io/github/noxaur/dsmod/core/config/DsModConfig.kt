@file:OptIn(ExperimentalSerializationApi::class)

package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Theme
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The settings that belong to no feature, as plain data, and a [Stored] per [Store] for the settings each feature
 * declares for itself. `SettingsTree` names each field here, describes it and gives it a row, and `ConfigFile` writes
 * it to `config/dsmod/config.json` by the field names, so a field's name is its key in the file: a renamed field keeps
 * its old name in `@JsonNames`, or players lose what they set. A field's initializer is its default. The edge reads
 * the fields from Java, so each has `@JvmField`.
 */
@Serializable
class DsModConfig {
    /** The ids of the rows the player starred, which the settings' Favorites lists. See `SettingsTree.favorites`. */
    @JvmField
    var favorites: List<String> = emptyList()

    @JvmField
    val general = General()

    @JvmField
    val hud = Hud()

    /** The features' settings, by the section of the file each is saved in. `ConfigFile` reads and writes them beside the fields. */
    @Transient
    private val stores = Store.entries.associateWith(::Stored)

    fun stored(store: Store): Stored = stores.getValue(store)

    @Serializable
    class General {
        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
        @JvmField
        var settingsKey = NO_KEY

        /** A key's GLFW code or a mouse button, as for [settingsKey]. */
        @JvmField
        var favoriteKey = KEY_F

        private companion object {
            /** GLFW's code for F, NotEnoughUpdates' key for a favorite. */
            const val KEY_F = 70
        }
    }

    /** Where a feature's HUD block has its plate's top-left corner, as fractions of the scaled screen, and how big it is. */
    @Serializable
    data class Position(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f)

    @Serializable
    class Hud {
        /** The feature id to its saved position. A block with none is stacked down from the top-left. */
        val positions = HashMap<String, Position>()

        @JvmField
        var theme = Theme.CHARCOAL

        @JvmField
        var editKey = NO_KEY

        @JvmField
        var scale = 1f
    }
}

/**
 * No key, which the settings screen shows as None. DragSim Utils and Odin open their settings on Right Shift, and a
 * player who runs either would open two screens with one press, so the Settings Key leaves the player to pick one.
 */
private const val NO_KEY = -1
