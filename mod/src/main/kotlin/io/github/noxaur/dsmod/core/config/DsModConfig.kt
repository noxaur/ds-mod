package io.github.noxaur.dsmod.core.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.feature.general.PetLook
import io.github.noxaur.dsmod.core.feature.general.PetPosition
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.Theme

/**
 * The settings show categories and options in the order they are declared: what applies everywhere first, then each area
 * of the game, and Contribute last. MoulConfig reads the fields directly, so every one it shows needs `@JvmField`, and
 * Gson saves only the ones with `@Expose`.
 */
class DsModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ds-mod")

    @Expose
    @Category(name = "General", desc = "The camera, your binds and the world.")
    @JvmField
    val general = General()

    @Expose
    @Category(name = "Interface", desc = "How ds-mod's HUD and screens look.")
    @JvmField
    val hud = Hud()

    @Expose
    @Category(name = "Chat", desc = "What reaches your chat.")
    @JvmField
    val chat = Chat()

    @Expose
    @Category(name = "Inventory", desc = "How menus look, and what keeps items safe.")
    @JvmField
    val inventory = Inventory()

    @Expose
    @Category(name = "Better UI", desc = "Tabs, a search and buttons for menus.")
    @JvmField
    val betterUi = BetterUi()

    @Expose
    @Category(name = "Loot", desc = "Your drops.")
    @JvmField
    val loot = Loot()

    @Expose
    @Category(name = "The End", desc = "Zealots, dragons and the Protector.")
    @JvmField
    val end = End()

    @Expose
    @Category(name = "Slayers", desc = "Your slayer quests and bosses.")
    @JvmField
    val slayer = Slayer()

    @Expose
    @Category(name = "Contribute", desc = "Record the game for ds-mod's developers.")
    @JvmField
    val contribute = Contribute()

    class Chat {
        @Expose
        @ConfigOption(name = "Ability Spam", desc = "Hide the message that an ability is on cooldown.")
        @ConfigEditorBoolean
        @JvmField
        var abilitySpam = true

        @Expose
        @ConfigOption(name = "Copy Chat", desc = "Right-click messages to copy them as text, format codes or JSON.")
        @ConfigEditorBoolean
        @JvmField
        var copyChat = true
    }

    class End {
        @Expose
        @ConfigOption(name = "Zealot Tracker", desc = "Count your Summoning Eyes and zealot kills, per hour and all-time.")
        @ConfigEditorBoolean
        @JvmField
        var zealotTracker = true

        @Expose
        @ConfigOption(name = "Protect Placed Eyes", desc = "Ignore a quick second click that would take a placed eye back.")
        @ConfigEditorBoolean
        @JvmField
        var protectPlacedEyes = true

        @Expose
        @ConfigOption(name = "Dragon Timer", desc = "Show the egg timer, the eyes placed and the dragon's time alive.")
        @ConfigEditorBoolean
        @JvmField
        var dragonTimer = true

        @Expose
        @ConfigOption(name = "Fireball Alert", desc = "Alert you when a dragon is about to throw fireballs.")
        @ConfigEditorBoolean
        @JvmField
        var fireballAlert = true
    

        @Expose
        @ConfigOption(name = "Golem Status", desc = "Show the Protector's stage and the kills left until it emerges.")
        @ConfigEditorBoolean
        @JvmField
        var golemStatus = true

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
        @ConfigOption(name = "Boss Drops", desc = "Show your rank, damage and loot from the last boss fight.")
        @ConfigEditorBoolean
        @JvmField
        var bossDrops = true
    }

    class General {
        @Expose
        @ConfigOption(name = "DragSim Only", desc = "Run ds-mod only on dragsim.co. Turn it off to use it on any server.")
        @ConfigEditorBoolean
        @JvmField
        var onlyOnDragSim = true

        @Expose
        @ConfigOption(name = "Fullbright", desc = "Light caves and nights as brightly as day.")
        @ConfigEditorBoolean
        @JvmField
        var fullbright = false

        @Expose
        @ConfigOption(name = "Explosion Particle Hider", desc = "Hide the clouds that explosions throw up.")
        @ConfigEditorBoolean
        @JvmField
        var explosionParticleHider = false

        @Expose
        @ConfigOption(name = "Equipment Display", desc = "Show your armor and pet on the HUD.")
        @ConfigEditorBoolean
        @JvmField
        var equipmentDisplay = true

        @Expose
        @ConfigOption(name = "Equipment Pieces", desc = "Pick the pieces to show, and drag them into order.")
        @ConfigEditorDraggableList(requireNonEmpty = true)
        @JvmField
        var equipmentPieces: MutableList<EquipmentPiece> = EquipmentPiece.entries.toMutableList()

        @Expose
        @ConfigOption(name = "Equipment Layout", desc = "Line the pieces up in a row or a column.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentLayout = EquipmentLayout.ROW

        @Expose
        @ConfigOption(name = "Pet Look", desc = "Show your pet as its head, or as its name and level.")
        @ConfigEditorDropdown
        @JvmField
        var petLook = PetLook.ICON

        @Expose
        @ConfigOption(name = "Pet Position", desc = "Put your pet among the armor, or on a row above or below it.")
        @ConfigEditorDropdown
        @JvmField
        var petPosition = PetPosition.WITH_ARMOR

        @Expose
        @ConfigOption(name = "Equipment Look", desc = "Show the pieces on the plate, on nothing, or in hotbar slots.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentLook = HudLook.PLATE

        @Expose
        @ConfigOption(name = "Equipment Placement", desc = "Keep the pieces where you put them, or beside your hotbar.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentPlacement = HudDock.FREE

        @Expose
        @ConfigOption(name = "Camera", desc = "Shape the third-person view with the settings below.")
        @ConfigEditorBoolean
        @JvmField
        var camera = true

        @Expose
        @ConfigOption(name = "Camera Distance", desc = "Set how many blocks the third-person camera sits from you.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 50f, minStep = 0.5f)
        @JvmField
        var cameraDistance = ThirdPerson.DISTANCE

        @Expose
        @ConfigOption(name = "Camera Height", desc = "Raise the third-person camera, or lower it below zero.")
        @ConfigEditorSlider(minValue = -2f, maxValue = 3f, minStep = 0.1f)
        @JvmField
        var cameraHeight = 0f

        @Expose
        @ConfigOption(name = "Camera Tilt", desc = "Angle the third-person camera down, or up below zero.")
        @ConfigEditorSlider(minValue = -45f, maxValue = 45f, minStep = 1f)
        @JvmField
        var cameraTilt = 0f

        @Expose
        @ConfigOption(name = "F5 Views", desc = "Pick which views F5 goes through.")
        @ConfigEditorDropdown
        @JvmField
        var cameraViews = Views.ALL

        @Expose
        @ConfigOption(name = "Camera Smoothing", desc = "Slide the camera out when you leave first person.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @JvmField
        var cameraSmoothing = 0f

        @Expose
        @ConfigOption(name = "Free Look", desc = "Look around in third person while you keep facing ahead.")
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
        @ConfigOption(name = "Free Look Mode", desc = "Pick Hold to look while the key is down, or Toggle.")
        @ConfigEditorDropdown
        @JvmField
        var freeLookMode = KeyMode.HOLD

        @Expose
        @ConfigOption(name = "Command Binds", desc = "Send a command, such as /warp end, with a key or a few keys.")
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
        @ConfigOption(name = "Edit Command Binds", desc = "Add your binds, pick their keys and choose where each works.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editBinds: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Key Sequence Gap", desc = "Set how many milliseconds a key sequence waits for its next key.")
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
        @ConfigOption(name = "Rarity Style", desc = "Pick a look for the marks, or Custom for your own.")
        @ConfigEditorDropdown
        @JvmField
        var rarityPreset = RarityPreset.GILDED

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Style", desc = "Build your own look for the marks, on your own items.")
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
        @ConfigOption(name = "Themed Menus", desc = "Draw menus and plain tooltips in the Theme under Interface.")
        @ConfigEditorBoolean
        @JvmField
        var themedMenus = true

        @Expose
        @ConfigOption(name = "Menu Look", desc = "Draw themed menus solid, or as glass that shows the world.")
        @ConfigEditorDropdown
        @JvmField
        var menuLook = MenuLook.SOLID

        @Expose
        @ConfigOption(name = "Glass Opacity", desc = "Set how dark the glass is, from clear at 0 to solid at 100.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @JvmField
        var glassOpacity = 60f

        @Expose
        @ConfigOption(name = "Stable Menus", desc = "Keep Go Back, Close and your inventory in place in every menu.")
        @ConfigEditorBoolean
        @JvmField
        var stableMenus = true

        @Expose
        @ConfigOption(name = "Hide Filler", desc = "Hide the blank glass panes that pad out menus.")
        @ConfigEditorBoolean
        @JvmField
        var hideFiller = true

        @Expose
        @ConfigOption(name = "Keep Cursor", desc = "Leave the cursor where it was when one menu opens another.")
        @ConfigEditorBoolean
        @JvmField
        var keepCursor = true

        @Expose
        @ConfigOption(name = "Slot Lock", desc = "Lock inventory slots and items so a misclick cannot move them.")
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
        @ConfigOption(name = "Item Lock Key", desc = "Pick the key that locks an item that does not stack.")
        @ConfigEditorKeybind(defaultKey = KEY_K)
        @JvmField
        var itemLockKey = KEY_K

        @Expose
        @ConfigOption(name = "Armor Swap Guard", desc = "Stop a right click from swapping on armor. Sneak to swap.")
        @ConfigEditorBoolean
        @JvmField
        var armorSwapGuard = false

        private companion object {
            /** GLFW's codes for the K and L keys. */
            const val KEY_K = 75
            const val KEY_L = 76
        }
    }

    class BetterUi {
        @Expose
        @ConfigOption(name = "Better UI", desc = "Turn everything below on or off at once.")
        @ConfigEditorBoolean
        @JvmField
        var enabled = true

        @Expose
        @ConfigOption(name = "Back Buttons", desc = "Add Go Back to menus without one, by resending your last command.")
        @ConfigEditorBoolean
        @JvmField
        var backButtons = true

        @Expose
        @ConfigOption(name = "Menu Tabs", desc = "Add tabs that send your commands, and a search, to every menu.")
        @ConfigEditorBoolean
        @JvmField
        var menuTabs = true

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Tabs", desc = "Add, move and change your tabs.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editTabs: Runnable = Runnable {}

        /** The tabs the player set up, in the editor. */
        @Expose
        @JvmField
        var tabs: List<MenuTab> = TabLayout.DEFAULT
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
        @ConfigOption(name = "Loot Alerts", desc = "Alert you to drops of the chosen tier or better.")
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
        @ConfigOption(name = "Slayer Boss Alert", desc = "Alert you when your slayer boss spawns.")
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

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Move HUD", desc = "Drag a block to move it, and scroll over it to resize it.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var edit: Runnable = Runnable {}
    

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
    }

    class Contribute {
        /** The edge sets this to open the requests, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Contribute", desc = "See what ds-mod's developers ask players to record.")
        @ConfigEditorButton(buttonText = "Open")
        @JvmField
        var open: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Report Game Data", desc = "Send game data new to ds-mod to its developers, names removed.")
        @ConfigEditorBoolean
        @JvmField
        var reportGameData = false
    }
}
