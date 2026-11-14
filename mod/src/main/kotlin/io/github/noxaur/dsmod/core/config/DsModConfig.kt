package io.github.noxaur.dsmod.core.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.noxaur.dsmod.core.ui.Theme

/**
 * One category per area of the game. MoulConfig reads the fields directly, so every one it shows
 * needs `@JvmField`, and Gson saves only the ones with `@Expose`.
 */
class DsModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ds-mod")

    @Expose
    @Category(name = "The End", desc = "Dragons, eyes, the egg and the Protector.")
    @JvmField
    val end = End()

    @Expose
    @Category(name = "Inventory", desc = "What the slots of a menu show.")
    @JvmField
    val inventory = Inventory()

    @Expose
    @Category(name = "Slayers", desc = "Your slayer quests and bosses.")
    @JvmField
    val slayer = Slayer()

    @Expose
    @Category(name = "Interface", desc = "How ds-mod's HUD and screens look.")
    @JvmField
    val hud = Hud()

    class End {
        @Expose
        @ConfigOption(name = "Dragon Timer", desc = "Show when the egg returns, how many eyes are placed and how long the dragon has been alive.")
        @ConfigEditorBoolean
        @JvmField
        var dragonTimer = true

        @Expose
        @ConfigOption(name = "Golem Status", desc = "Show the Endstone Protector's stage and the kills left before it emerges.")
        @ConfigEditorBoolean
        @JvmField
        var golemStatus = true

        @Expose
        @ConfigOption(name = "Zealot Tracker", desc = "Count your Summoning Eyes and other rare drops this session, per hour in The End and all-time.")
        @ConfigEditorBoolean
        @JvmField
        var zealotTracker = true
    }

    class Inventory {
        @Expose
        @ConfigOption(name = "Rarity Outlines", desc = "Frame each item's slot in the color of its rarity.")
        @ConfigEditorBoolean
        @JvmField
        var rarityOutlines = true
    }

    class Slayer {
        @Expose
        @ConfigOption(name = "Slayer Tracker", desc = "Count the slayer bosses you slay and time each from spawn to slain.")
        @ConfigEditorBoolean
        @JvmField
        var slayerTracker = true
    }

    /** Where a feature's HUD block has its plate's top-left corner, as fractions of the scaled screen, and how big it is. */
    data class Position(@Expose val x: Float = 0f, @Expose val y: Float = 0f, @Expose val scale: Float = 1f)

    class Hud {
        /** The feature id to its saved position. A block with none is stacked down from the top-left. */
        @Expose
        val positions = HashMap<String, Position>()

        @Expose
        @ConfigOption(name = "Theme", desc = "Pick the colors of ds-mod's screens and controls.")
        @ConfigEditorDropdown
        @JvmField
        var theme = Theme.CHARCOAL

        @Expose
        @ConfigOption(name = "HUD Scale", desc = "Make every HUD block bigger or smaller.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 3f, minStep = 0.25f)
        @JvmField
        var scale = 1f

        @Expose
        @ConfigOption(name = "HUD Background", desc = "Show a dark plate behind the HUD, so it reads over a bright world.")
        @ConfigEditorBoolean
        @JvmField
        var background = true

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Move HUD", desc = "Drag a block to move it, and scroll over it to resize it.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var edit: Runnable = Runnable {}
    }
}
