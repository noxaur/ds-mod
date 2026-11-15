package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import kotlin.reflect.KMutableProperty0

/**
 * A run of settings under one heading. A group with no [name] sits straight under the section's title. [switch] is
 * the setting that turns the whole group on, shown beside the heading, when the group is one feature.
 */
class SettingGroup(val name: String?, val rows: List<Setting>, val switch: Switch? = null)

/** A page of the settings screen. [icon] colors its placeholder icon until sections get icons of their own. */
class SettingSection(val name: String, val icon: Role, val groups: List<SettingGroup>) {
    /** The groups the sidebar lists under the section, which are the ones with a heading. */
    val named: List<SettingGroup> = groups.filter { it.name != null }

    /** Every row of the section, nested ones and heading switches included. */
    fun walk(): Sequence<Setting> = groups.asSequence().flatMap { group -> listOfNotNull(group.switch).asSequence() + group.rows.asSequence().flatMap { it.walk() } }
}

/** Declares the sections of the tree, each with its groups. */
@SettingsDsl
class Sections {
    internal val sections = ArrayList<SettingSection>()

    fun section(name: String, icon: Role, groups: Groups.() -> Unit) {
        sections += SettingSection(name, icon, Groups().apply(groups).groups)
    }
}

@SettingsDsl
class Groups {
    internal val groups = ArrayList<SettingGroup>()

    fun group(name: String?, switch: Switch? = null, rows: Rows.() -> Unit) {
        groups += SettingGroup(name, Rows().apply(rows).rows, switch)
    }

    /** The switch beside a group's heading. */
    fun toggle(property: KMutableProperty0<Boolean>, name: String, description: String, id: String = property.name) =
        Switch(id, name, description, Bind.of(property))
}

/**
 * Every setting of ds-mod, in the order the settings screen shows them, bound to a config. The tree is the schema
 * of the settings: to add one, add its field to `DsModConfig` and one line here with an existing kind.
 * `SettingsTreeTest` fails on a saved field that no row reaches and that [STORED] does not name.
 */
object SettingsTree {
    /** Saved fields that no row edits, each with the reason. */
    val STORED = mapOf(
        "General.binds" to "Edited on the Command Binds screen.",
        "General.bindGroups" to "Edited on the Command Binds screen.",
        "General.collapsedGroups" to "Edited on the Command Binds screen.",
        "General.offGroups" to "Edited on the Command Binds screen.",
        "BetterUi.tabs" to "Edited on the tab editor.",
        "Hud.positions" to "Edited on the Move HUD screen.",
        "Inventory.hotbarStyle" to "Edited on the Rarity Style screen, as is whether it follows the inventory's.",
        "Inventory.hotbarMatches" to "Edited on the Rarity Style screen.",
    )

    private val fresh by lazy { declare(DsModConfig()) }

    /** The tree bound to [config], each row's default taken from the same row of a fresh config. */
    fun sections(config: DsModConfig): List<SettingSection> {
        val sections = declare(config)
        val defaults = fresh.asSequence().flatMap { it.walk() }.associateBy { it.id }
        for (setting in sections.asSequence().flatMap { it.walk() }) if (setting is Valued<*>) setting.adopt(defaults.getValue(setting.id))
        return sections
    }

    /** Sets each row's [Setting.gate]: the switch it sits under. */
    fun link(rows: List<Setting>, gate: Switch?) {
        for (row in rows) {
            row.gate = gate
            link(row.children, if (row is Switch) row else gate)
        }
    }

    private fun declare(c: DsModConfig): List<SettingSection> {
        val sections = Sections().apply {
            // What applies everywhere comes first, then each area of the game, and Contribute last, as in DsModConfig.
            section("General", Role.VALUE) {
                val g = c.general
                group("Server") {
                    switch(g::onlyOnDragSim, "DragSim Only", "Run ds-mod only on dragsim.co.")
                }
                group("World") {
                    switch(g::fullbright, "Fullbright", "Light caves and nights as brightly as day.")
                    switch(g::explosionParticleHider, "Explosion Particles", "Hide the clouds that explosions throw up.")
                }
                group("Animations", switch = toggle(g::animations, "Animations", "Shape how your held item swings and sits with the settings below.")) {
                    choice(g::swingStyle, "Swing Style", "Pick how your held item moves when you swing.")
                    number(g::swingSpeed, "Swing Speed", "Speed your swing up, or slow it down below 1.", 0.1f..3f, step = 0.1f, unit = "x")
                    switch(g::ignoreHaste, "Ignore Haste", "Swing at the same pace under Haste or Mining Fatigue.")
                    switch(g::noEquipReset, "No Equip Reset", "Keep your held item up instead of dipping after a swing or use.")
                    number(g::itemSize, "Item Size", "Grow or shrink your held item.", 0.1f..2f, step = 0.05f, unit = "x")
                    number(g::itemSide, "Item Side", "Move your held item out to the side, or in below zero.", -1f..1f, step = 0.05f)
                    number(g::itemHeight, "Item Height", "Raise your held item, or lower it below zero.", -1f..1f, step = 0.05f)
                    number(g::itemDepth, "Item Depth", "Push your held item away, or bring it nearer below zero.", -1f..1f, step = 0.05f)
                }
                group("Profile Viewer", switch = toggle(g::profileViewer, "Profile Viewer", "Look up any player's profile with /pv and their name.")) {
                    switch(g::profileNames, "Click Names", "Click a player's name in chat to open their profile.")
                }
            }
            section("Camera", Role.ACTION) {
                val g = c.general
                group("Third Person", switch = toggle(g::camera, "Camera", "Shape the third-person view with the settings below.")) {
                    number(g::cameraDistance, "Distance", "Set how many blocks the third-person camera sits from you.", 1f..50f, step = 0.5f)
                    number(g::cameraHeight, "Height", "Raise the third-person camera, or lower it below zero.", -2f..3f, step = 0.1f)
                    number(g::cameraTilt, "Tilt", "Angle the third-person camera down, or up below zero.", -45f..45f, step = 1f, unit = "°")
                    choice(g::cameraViews, "F5 Views", "Pick which views F5 goes through.")
                    number(g::cameraSmoothing, "Smoothing", "Slide the camera out when you leave first person.", 0f..100f, step = 5f)
                }
                group("Free Look", switch = toggle(g::freeLook, "Free Look", "Look around in third person while you keep facing ahead.")) {
                    keys(g::freeLookKey, "Key", "Pick the key or mouse button for Free Look.")
                    choice(g::freeLookMode, "Mode", "Pick Hold to look while the key is down, or Toggle.")
                }
            }
            section("Binds", Role.DIVINE) {
                val g = c.general
                group(null) {
                    val binds = switch(g::commandBinds, "Command Binds", "Send a command, such as /warp end, with a key or a few keys.")
                    action(g::editBinds, "Edit Binds", "Add your binds, pick their keys and choose where each works.", button = "Edit", dependsOn = binds)
                    number(g::sequenceGap, "Sequence Gap", "Set how many milliseconds a key sequence waits for its next key.", 50f..1000f, step = 50f, unit = " ms", dependsOn = binds)
                }
            }
            section("Interface", Role.MUTED) {
                val hud = c.hud
                val g = c.general
                group("Theme") {
                    pick(hud::theme, "Theme", "Pick the colors of ds-mod's screens and controls.") { Look.Swatch(it) }
                }
                group("HUD") {
                    number(hud::scale, "Scale", "Make every HUD block bigger or smaller.", 0.5f..3f, step = 0.25f, unit = "x", id = "hudScale")
                    switch(hud::background, "Background", "Show a dark plate behind the HUD, so it reads over a bright world.", id = "hudBackground")
                    action(hud::edit, "Move HUD", "Drag a block to move it, and scroll over it to resize it.", button = "Move", id = "moveHud")
                }
                group("Equipment Display", switch = toggle(g::equipmentDisplay, "Equipment Display", "Show your armor and pet on the HUD.")) {
                    order(g::equipmentPieces, "Pieces", "Pick the pieces to show, and drag them into order.")
                    choice(g::equipmentLayout, "Layout", "Line the pieces up in a row or a column.")
                    choice(g::equipmentLook, "Look", "Show the pieces on the plate, on nothing, or in hotbar slots.")
                    choice(g::equipmentPlacement, "Placement", "Keep the pieces where you put them, or beside your hotbar.")
                    choice(g::petLook, "Pet Look", "Show your pet as its head, or as its name and level.")
                    choice(g::petPosition, "Pet Position", "Put your pet among the armor, or on a row above or below it.")
                }
                group("Inventory Preview", switch = toggle(g::inventoryPreview, "Inventory Preview", "Show a grid of the items in your inventory on the HUD.")) {
                    order(g::inventoryRows, "Rows", "Pick the rows to show, and drag them into order.")
                    choice(g::inventoryLook, "Look", "Show the items on the plate, on nothing, or as your inventory.")
                }
            }
            section("Chat", Role.WARNING) {
                group(null) {
                    switch(c.chat::abilitySpam, "Ability Spam", "Hide the message that an ability is on cooldown.")
                    switch(c.chat::copyChat, "Copy Chat", "Right-click messages to copy them as text, format codes or JSON.")
                }
            }
            section("Inventory", Role.GOOD) {
                val inv = c.inventory
                group("Rarity Marks", switch = toggle(inv::rarityMarks, "Rarity Marks", "Mark each item's slot in the color of its rarity.")) {
                    pick(inv::rarityPreset, "Style", "Pick a look for the marks, or Custom for your own.") { Look.Slots(it.style ?: inv.inventoryStyle) }
                    fold("customLook", "Custom Look", "Build your own look from its parts, which switches Style to Custom.") {
                        // A part changes the look the player sees and makes it their own, as the Rarity Style screen does.
                        fun <T> part(name: String, get: (SlotStyle) -> T, with: SlotStyle.(T) -> SlotStyle) =
                            Bind("Inventory.inventoryStyle.$name", { get(inv.containerStyle()) }) {
                                inv.inventoryStyle = inv.containerStyle().with(it)
                                inv.rarityPreset = RarityPreset.CUSTOM
                            }
                        pick(part("border", { it.border }) { copy(border = it) }, "Border", "Pick the ring around the slot.", Border.entries, label = { it.label }) {
                            Look.Slots(inv.containerStyle().copy(border = it), 1)
                        }
                        pick(part("fill", { it.fill }) { copy(fill = it) }, "Fill", "Pick the color inside the slot, behind the item.", Fill.entries, label = { it.label }) {
                            Look.Slots(inv.containerStyle().copy(fill = it), 1)
                        }
                        pick(part("floor", { it.floor }) { copy(floor = it) }, "Floor", "Pick what the item stands on.", Floor.entries, label = { it.label }) {
                            Look.Slots(inv.containerStyle().copy(floor = it), 1)
                        }
                        switch(part("gem", { it.gem }) { copy(gem = it) }, "Gem", "Put a small gem over the item's top-left corner.")
                    }
                    switch(inv::markCommon, "Common Items", "Mark common items too.")
                    switch(inv::hotbarMarks, "Hotbar", "Mark your hotbar as well.")
                    action(inv::editRarityStyle, "Edit Style", "Build your own look for the marks, on your own items.", button = "Edit")
                }
                group("Protection") {
                    switch(inv::slotLock, "Slot Lock", "Lock inventory slots and items so a misclick cannot move them.") {
                        keys(inv::slotLockKey, "Slot Key", "Pick the key or mouse button that locks the slot under the mouse.")
                        keys(inv::itemLockKey, "Item Key", "Pick the key that locks an item that does not stack.")
                    }
                    switch(inv::armorSwapGuard, "Armor Swap Guard", "Stop a right click from swapping on armor, unless you sneak.")
                }
            }
            section("Menus", Role.LEGENDARY) {
                val inv = c.inventory
                group("Look") {
                    switch(inv::themedMenus, "Themed Menus", "Draw menus and plain tooltips in the Theme under Interface.") {
                        choice(inv::menuLook, "Menu Look", "Draw themed menus solid, or as glass that shows the world.")
                        number(inv::glassOpacity, "Glass Opacity", "Set how dark the glass is, from clear at 0 to solid at 100.", 0f..100f, step = 5f)
                    }
                    switch(inv::stableMenus, "Stable Menus", "Keep Go Back, Close and your inventory in place in every menu.")
                    switch(inv::hideFiller, "Hide Filler", "Hide the blank glass panes that pad out menus.")
                    switch(inv::keepCursor, "Keep Cursor", "Leave the cursor where it was when one menu opens another.")
                }
                group("Better UI", switch = toggle(c.betterUi::enabled, "Better UI", "Turn everything below on or off at once.", id = "betterUi")) {
                    switch(c.betterUi::backButtons, "Back Buttons", "Add Go Back to menus without one, by resending your last command.")
                    switch(c.betterUi::menuTabs, "Menu Tabs", "Add tabs that send your commands, and a search, to every menu.") {
                        action(c.betterUi::editTabs, "Edit Tabs", "Add, move and change your tabs.", button = "Edit")
                    }
                }
            }
            section("The End", Role.EPIC) {
                group("Dragon") {
                    switch(c.end::dragonTimer, "Dragon Timer", "Show the egg timer, the eyes placed and the dragon's time alive.")
                    switch(c.end::fireballAlert, "Fireball Alert", "Alert you when a dragon is about to throw fireballs.")
                    switch(c.end::protectPlacedEyes, "Protect Placed Eyes", "Ignore a quick second click that would take a placed eye back.")
                }
                group("Protector") {
                    switch(c.end::golemStatus, "Golem Status", "Show the Protector's stage and the kills left until it emerges.")
                    switch(c.end::bossBeacon, "Boss Beacon", "Show a beam where the Endstone Protector will emerge.") {
                        span(c.end::beamSections, "Beam Timing", "Pick when the beam shows, in zealots killed out of 200.", max = 200, ticks = listOf(50, 100, 150))
                        switch(c.end::beamInFight, "During the Fight", "Keep the beam while the Protector is up.")
                    }
                }
                group("Drops") {
                    switch(c.end::bossDrops, "Boss Drops", "Show your rank, damage and loot from the last boss fight.")
                    switch(c.end::zealotTracker, "Zealot Tracker", "Count your Summoning Eyes and zealot kills, per hour and all-time.")
                }
            }
            section("Combat", Role.BAD) {
                group("Slayers") {
                    switch(c.slayer::slayerTracker, "Slayer Tracker", "Count the slayer bosses you slay and time each from spawn to slain.")
                    switch(c.slayer::slayerBossAlert, "Slayer Boss Alert", "Alert you when your slayer boss spawns.")
                }
                group("Loot") {
                    switch(c.loot::lootAlerts, "Loot Alerts", "Alert you to drops of the chosen tier or better.") {
                        choice(c.loot::lootAlertTier, "Alert Tier", "Pick the lowest drop tier that alerts you.")
                    }
                }
            }
            section("Contribute", Role.RARE) {
                group(null) {
                    action(c.contribute::open, "Requests", "See what ds-mod's developers ask players to record.", id = "requests")
                    switch(c.contribute::reportGameData, "Report Game Data", "Send game data new to ds-mod to its developers, names removed.")
                }
            }
        }.sections
        for (section in sections) for (group in section.groups) link(group.rows, group.switch)
        return sections
    }
}
