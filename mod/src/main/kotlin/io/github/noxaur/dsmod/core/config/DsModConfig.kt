package io.github.noxaur.dsmod.core.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.ArmorLayout
import io.github.noxaur.dsmod.core.input.CommandBind
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

    @Expose
    @Category(name = "Contribute", desc = "Help ds-mod's developers see parts of the game they cannot reach.")
    @JvmField
    val contribute = Contribute()

    class Chat {
        @Expose
        @ConfigOption(name = "Ability Spam", desc = "Hide the message the server sends each time you use an ability that is on cooldown.")
        @ConfigEditorBoolean
        @JvmField
        var abilitySpam = true

        @Expose
        @ConfigOption(name = "Copy Chat", desc = "Select chat messages with the right mouse button and copy them as text, format codes or JSON.")
        @ConfigEditorBoolean
        @JvmField
        var copyChat = true
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

        @Expose
        @ConfigOption(name = "Fireball Alert", desc = "Show a title and play a sound when a dragon is about to throw fireballs.")
        @ConfigEditorBoolean
        @JvmField
        var fireballAlert = true
    }

    class General {
        @Expose
        @ConfigOption(name = "DragSim Only", desc = "Run ds-mod only while you play on dragsim.co. Turn it off to use the mod on any server.")
        @ConfigEditorBoolean
        @JvmField
        var onlyOnDragSim = true

        @Expose
        @ConfigOption(name = "Fullbright", desc = "Light the whole world, so caves and nights are as bright as day.")
        @ConfigEditorBoolean
        @JvmField
        var fullbright = false

        @Expose
        @ConfigOption(name = "Explosion Particle Hider", desc = "Hide the clouds that explosions throw up.")
        @ConfigEditorBoolean
        @JvmField
        var explosionParticleHider = false

        @Expose
        @ConfigOption(name = "Armor Display", desc = "Show the armor you wear on the HUD. Open the chat and point at a piece to read it.")
        @ConfigEditorBoolean
        @JvmField
        var armorDisplay = true

        @Expose
        @ConfigOption(name = "Armor Layout", desc = "Line the armor up in a row or a column.")
        @ConfigEditorDropdown
        @JvmField
        var armorLayout = ArmorLayout.ROW

        @Expose
        @ConfigOption(name = "Camera", desc = "Shape the third-person view with the settings below. At their defaults it is the game's own.")
        @ConfigEditorBoolean
        @JvmField
        var camera = true

        @Expose
        @ConfigOption(name = "Camera Distance", desc = "Set how many blocks the third-person camera sits from you.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 50f, minStep = 0.5f)
        @JvmField
        var cameraDistance = ThirdPerson.DISTANCE

        @Expose
        @ConfigOption(name = "Camera Height", desc = "Raise the third-person camera by this many blocks, or lower it below zero.")
        @ConfigEditorSlider(minValue = -2f, maxValue = 3f, minStep = 0.1f)
        @JvmField
        var cameraHeight = 0f

        @Expose
        @ConfigOption(name = "Camera Tilt", desc = "Angle the third-person camera down by this many degrees, or up below zero. Your aim stays where it is.")
        @ConfigEditorSlider(minValue = -45f, maxValue = 45f, minStep = 1f)
        @JvmField
        var cameraTilt = 0f

        @Expose
        @ConfigOption(name = "F5 Views", desc = "Pick which views F5 goes through.")
        @ConfigEditorDropdown
        @JvmField
        var cameraViews = Views.ALL

        @Expose
        @ConfigOption(name = "Camera Smoothing", desc = "Slide the camera out when you leave first person. Zero jumps there at once.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @JvmField
        var cameraSmoothing = 0f

        @Expose
        @ConfigOption(name = "Free Look", desc = "Look around in third person with the key below. You keep facing where you were, and other players see no turn.")
        @ConfigEditorBoolean
        @JvmField
        var freeLook = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as MoulConfig's editor writes it. */
        @Expose
        @ConfigOption(name = "Free Look Key", desc = "Pick the key or mouse button for Free Look.")
        @ConfigEditorKeybind(defaultKey = LEFT_ALT)
        @JvmField
        var freeLookKey = LEFT_ALT

        @Expose
        @ConfigOption(name = "Free Look Mode", desc = "Pick whether you hold the key to look around, or press it once to start and again to stop.")
        @ConfigEditorDropdown
        @JvmField
        var freeLookMode = KeyMode.HOLD

        @Expose
        @ConfigOption(name = "Command Binds", desc = "Send a command with a key, keys held together or keys in order, such as /warp end on Shift + F6. Set them up with Edit below.")
        @ConfigEditorBoolean
        @JvmField
        var commandBinds = true

        /** Every bind, in the order the player put them, each naming its group. */
        @Expose
        var binds: List<CommandBind> = emptyList()

        /** The groups after the first, which is unnamed, in the order the player made them. */
        @Expose
        var bindGroups: List<String> = emptyList()

        /** The groups whose binds are folded away in the list. */
        @Expose
        var collapsedGroups: List<String> = emptyList()

        /** The groups switched off, whose binds send nothing whatever their own switch says. */
        @Expose
        var offGroups: List<String> = emptyList()

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Command Binds", desc = "Name each bind, pick its keys, and choose where it works.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editBinds: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Key Sequence Gap", desc = "Set how many milliseconds a bind whose keys go in order waits for its next key.")
        @ConfigEditorSlider(minValue = 50f, maxValue = 1000f, minStep = 50f)
        @JvmField
        var sequenceGap = 250f

        private companion object {
            /** GLFW's code for the left Alt key. */
            const val LEFT_ALT = 342
        }
    }

    class Inventory {
        @Expose
        @ConfigOption(name = "Rarity Marks", desc = "Mark each item's slot in the color of its rarity.")
        @ConfigEditorBoolean
        @JvmField
        var rarityMarks = true

        @Expose
        @ConfigOption(name = "Rarity Style", desc = "Pick a look for the marks, or Custom for the one you build with Edit Style.")
        @ConfigEditorDropdown
        @JvmField
        var rarityPreset = RarityPreset.GILDED

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Style", desc = "Build your own look beside your inventory and hotbar. A change there switches Rarity Style to Custom.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editRarityStyle: Runnable = Runnable {}

        /** The player's own style for every container, their inventory included, shown while [rarityPreset] is Custom. */
        @Expose
        @JvmField
        var inventoryStyle = SlotStyle()

        @Expose
        @JvmField
        var hotbarStyle = SlotStyle()

        /** While true the hotbar is drawn in [inventoryStyle], and [hotbarStyle] waits for the player to unlink them. */
        @Expose
        @JvmField
        var hotbarMatches = true

        @Expose
        @JvmField
        var hotbarMarks = true

        @Expose
        @JvmField
        var markCommon = false

        /** The style containers show: the preset's, or the player's own under Custom. */
        fun containerStyle(): SlotStyle = rarityPreset.style ?: inventoryStyle

        @Expose
        @ConfigOption(name = "Armor Swap Guard", desc = "Stop a right click from swapping the armor in your hand onto you. Sneak to swap.")
        @ConfigEditorBoolean
        @JvmField
        var armorSwapGuard = false

        @Expose
        @ConfigOption(name = "Slot Lock", desc = "Lock slots and items of your inventory with the keys below, so no click or drop key moves them. Hold the slot key and move to another slot to link the two.")
        @ConfigEditorBoolean
        @JvmField
        var slotLock = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as MoulConfig's editor writes it. */
        @Expose
        @ConfigOption(name = "Slot Lock Key", desc = "Pick the key or mouse button that locks the slot under the mouse.")
        @ConfigEditorKeybind(defaultKey = KEY_L)
        @JvmField
        var slotLockKey = KEY_L

        @Expose
        @ConfigOption(name = "Item Lock Key", desc = "Pick the key or mouse button that locks the item under the mouse, wherever you move it. It works on an item that does not stack.")
        @ConfigEditorKeybind(defaultKey = KEY_K)
        @JvmField
        var itemLockKey = KEY_K

        private companion object {
            /** GLFW's codes for the K and L keys. */
            const val KEY_K = 75
            const val KEY_L = 76
        }
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

        @Expose
        @ConfigOption(name = "Slayer Boss Alert", desc = "Show a title and play a sound when your slayer boss spawns.")
        @ConfigEditorBoolean
        @JvmField
        var slayerBossAlert = true
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

    class Contribute {
        /** The edge sets this to open the requests, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Contribute", desc = "See what ds-mod's developers ask players to record, and record it.")
        @ConfigEditorButton(buttonText = "Open")
        @JvmField
        var open: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Report Unread Lines", desc = "Send lines the mod could not read to ds-mod's developers, with player names removed. Off until you turn it on.")
        @ConfigEditorBoolean
        @JvmField
        var reportUnknowns = false
    }
}
