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
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Theme

/**
 * One category per area of the game. MoulConfig reads the fields directly, so every one it shows
 * needs `@JvmField`, and Gson saves only the ones with `@Expose`.
 */
class DsModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ds-mod")

    @Expose
    @Category(name = "Chat", desc = "What reaches your chat.")
    @JvmField
    val chat = Chat()

    @Expose
    @Category(name = "The End", desc = "Dragons, eyes, the egg and the Protector.")
    @JvmField
    val end = End()

    @Expose
    @Category(name = "General", desc = "How the game looks, and what it warns you about.")
    @JvmField
    val general = General()

    @Expose
    @Category(name = "Inventory", desc = "What the slots of a menu show, and what protects your items.")
    @JvmField
    val inventory = Inventory()

    @Expose
    @Category(name = "Loot", desc = "Your drops.")
    @JvmField
    val loot = Loot()

    @Expose
    @Category(name = "Slayers", desc = "Your slayer quests and bosses.")
    @JvmField
    val slayer = Slayer()

    @Expose
    @Category(name = "Interface", desc = "How ds-mod's HUD and screens look.")
    @JvmField
    val hud = Hud()

    class Chat {
        @Expose
        @ConfigOption(name = "Ability Spam", desc = "Hide the message the server sends each time you use an ability that is on cooldown.")
        @ConfigEditorBoolean
        @JvmField
        var abilitySpam = true
    }

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

        @Expose
        @ConfigOption(name = "Boss Beacon", desc = "Show a beam where the Endstone Protector will emerge.")
        @ConfigEditorBoolean
        @JvmField
        var bossBeacon = true

        /** The parts of the Protector's cycle that show the beam, in zealots killed since the last defeat, out of 200. */
        @Expose
        var beamSections: List<Section> = listOf(Section(175, 200))

        @Expose
        var beamInFight = false

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Beam Timing", desc = "Pick when in the Protector's cycle the beam shows.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editBeam: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Boss Drops", desc = "Show your damage rank and loot from the last dragon or Protector, and keep a count of your fights.")
        @ConfigEditorBoolean
        @JvmField
        var bossDrops = true

        @Expose
        @ConfigOption(name = "Protect Placed Eyes", desc = "Ignore a second click on the altar frame you just clicked with an eye, so you do not take the eye back out.")
        @ConfigEditorBoolean
        @JvmField
        var protectPlacedEyes = true
    }

    class General {
        @Expose
        @ConfigOption(name = "Fullbright", desc = "Light the whole world, so caves and nights are as bright as day.")
        @ConfigEditorBoolean
        @JvmField
        var fullbright = false

        @Expose
        @ConfigOption(name = "Alerts", desc = "Show a title and play a sound for the moments chosen below.")
        @ConfigEditorBoolean
        @JvmField
        var alerts = true

        @Expose
        @ConfigOption(name = "Fireball Alert", desc = "Alert you when a dragon is about to throw fireballs.")
        @ConfigEditorBoolean
        @JvmField
        var fireballAlert = true

        @Expose
        @ConfigOption(name = "Slayer Boss Alert", desc = "Alert you when your slayer boss spawns.")
        @ConfigEditorBoolean
        @JvmField
        var slayerBossAlert = true

        @Expose
        @ConfigOption(name = "Explosion Particle Hider", desc = "Hide the clouds that explosions throw up.")
        @ConfigEditorBoolean
        @JvmField
        var explosionParticleHider = false
    }

    class Inventory {
        @Expose
        @ConfigOption(name = "Rarity Outlines", desc = "Frame each item's slot in the color of its rarity.")
        @ConfigEditorBoolean
        @JvmField
        var rarityOutlines = true

        @Expose
        @ConfigOption(name = "Hotbar Outlines", desc = "Show the rarity frames on your hotbar too.")
        @ConfigEditorBoolean
        @JvmField
        var hotbarOutlines = true

        @Expose
        @ConfigOption(name = "Armor Swap Guard", desc = "Stop a right click from swapping the armor in your hand onto you. Sneak to swap.")
        @ConfigEditorBoolean
        @JvmField
        var armorSwapGuard = false
    }

    /** A drop's tier, lowest first. [word] is what the server writes before `DROP!`. */
    enum class LootTier(private val label: String, val word: String) {
        RARE("Rare", "RARE"),
        EPIC("Epic", "EPIC"),
        RNGESUS("RNGesus", "RNGESUS"),
        ;

        override fun toString() = label
    }

    class Loot {
        @Expose
        @ConfigOption(name = "Loot Alerts", desc = "Show a title and play a sound when you get a drop of the chosen tier or better.")
        @ConfigEditorBoolean
        @JvmField
        var lootAlerts = true

        @Expose
        @ConfigOption(name = "Alert Tier", desc = "Pick the lowest drop tier that alerts you.")
        @ConfigEditorDropdown
        @JvmField
        var lootAlertTier = LootTier.EPIC
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
