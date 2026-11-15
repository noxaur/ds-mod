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
            section("The End", Role.EPIC) {
                group("Dragon") {
                    switch(c.end::dragonTimer, "Dragon Timer", "Show when the egg returns, how many eyes are placed and how long the dragon has been alive.")
                    switch(c.end::fireballAlert, "Fireball Alert", "Show a title and play a sound when a dragon is about to throw fireballs.")
                    switch(c.end::protectPlacedEyes, "Protect Placed Eyes", "Ignore a second click on the altar frame you just clicked with an eye.")
                }
                group("Protector") {
                    switch(c.end::golemStatus, "Golem Status", "Show the Protector's stage and the kills left before it emerges.")
                    switch(c.end::bossBeacon, "Boss Beacon", "Show a beam where the Endstone Protector will emerge.") {
                        span(c.end::beamSections, "Beam Timing", "Pick when the beam shows, in zealots killed out of 200.", max = 200, ticks = listOf(50, 100, 150))
                        switch(c.end::beamInFight, "During the Fight", "Keep the beam while the Protector is up.")
                    }
                }
                group("Drops") {
                    switch(c.end::bossDrops, "Boss Drops", "Show your damage rank and loot from the last dragon or Protector.")
                    switch(c.end::zealotTracker, "Zealot Tracker", "Count your Summoning Eyes and other rare drops.")
                }
            }
            section("Combat", Role.BAD) {
                group("Slayers") {
                    switch(c.slayer::slayerTracker, "Slayer Tracker", "Count the slayer bosses you slay and time each.")
                    switch(c.slayer::slayerBossAlert, "Slayer Boss Alert", "Show a title and play a sound when your slayer boss spawns.")
                }
                group("Loot") {
                    switch(c.loot::lootAlerts, "Loot Alerts", "Show a title and play a sound when you get a drop of the chosen tier or better.") {
                        choice(c.loot::lootAlertTier, "Alert Tier", "Pick the lowest drop tier that alerts you.")
                    }
                }
            }
            section("Inventory", Role.GOOD) {
                val inv = c.inventory
                group("Rarity Marks", switch = toggle(inv::rarityMarks, "Rarity Marks", "Mark each item's slot in the color of its rarity.")) {
                    pick(inv::rarityPreset, "Style", "Pick a ready-made look, or Custom for the one you build below.") { Look.Slots(it.style ?: inv.inventoryStyle) }
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
                    action(inv::editRarityStyle, "Edit Style", "Build a look beside your inventory, and give the hotbar its own.", button = "Edit")
                }
                group("Protection") {
                    switch(inv::slotLock, "Slot Lock", "Lock slots and items of your inventory, so no click or drop key moves them.") {
                        keys(inv::slotLockKey, "Slot Key", "Lock the slot under the mouse, or hold it and move to another slot to link the two.")
                        keys(inv::itemLockKey, "Item Key", "Lock the item under the mouse, wherever you move it.")
                    }
                    switch(inv::armorSwapGuard, "Armor Swap Guard", "Stop a right click from swapping the armor in your hand onto you, unless you sneak.")
                }
            }
            section("Menus", Role.LEGENDARY) {
                group("Look") {
                    switch(c.inventory::themedMenus, "Themed Menus", "Draw menus, your inventory and plain tooltips in your theme.")
                    switch(c.inventory::stableMenus, "Stable Menus", "Keep the bottom row of every menu in one place.")
                    switch(c.inventory::hideFiller, "Hide Filler", "Hide the blank glass panes that fill out a menu.")
                    switch(c.inventory::keepCursor, "Keep Cursor", "Leave the cursor where it was when one menu opens another.")
                }
                group("Better UI", switch = toggle(c.betterUi::enabled, "Better UI", "Add ds-mod's tabs and buttons to the game's menus.", id = "betterUi")) {
                    switch(c.betterUi::backButtons, "Back Buttons", "Add Go Back to a menu that has none.")
                    switch(c.betterUi::menuTabs, "Menu Tabs", "Add tabs to menus that send your commands, and a search tab.") {
                        action(c.betterUi::editTabs, "Edit Tabs", "Place, add and change the tabs: what each sends, its item and its name.", button = "Edit")
                    }
                }
            }
            section("Chat", Role.WARNING) {
                group(null) {
                    switch(c.chat::abilitySpam, "Ability Spam", "Hide the message the server sends each time you use an ability that is on cooldown.")
                    switch(c.chat::copyChat, "Copy Chat", "Select chat messages with the right mouse button and copy them.")
                }
            }
            section("Camera", Role.ACTION) {
                val g = c.general
                group("Third Person", switch = toggle(g::camera, "Camera", "Shape the third-person view, which is the game's own at the defaults.")) {
                    number(g::cameraDistance, "Distance", "Set how many blocks the camera sits from you.", 1f..50f, step = 0.5f)
                    number(g::cameraHeight, "Height", "Raise the camera by this many blocks, or lower it below zero.", -2f..3f, step = 0.1f)
                    number(g::cameraTilt, "Tilt", "Angle the camera down, or up below zero, while your aim stays where it is.", -45f..45f, step = 1f, unit = "°")
                    choice(g::cameraViews, "F5 Views", "Pick which views F5 goes through.")
                    number(g::cameraSmoothing, "Smoothing", "Slide the camera out when you leave first person, where zero jumps at once.", 0f..100f, step = 5f)
                }
                group("Free Look", switch = toggle(g::freeLook, "Free Look", "Look around in third person while you keep facing where you were.")) {
                    keys(g::freeLookKey, "Key", "Pick the key or mouse button for Free Look.")
                    choice(g::freeLookMode, "Mode", "Hold the key to look around, or press it once to start and again to stop.")
                }
            }
            section("Binds", Role.DIVINE) {
                val g = c.general
                group(null) {
                    val binds = switch(g::commandBinds, "Command Binds", "Send a command with a key, keys held together or keys in order.")
                    action(g::editBinds, "Edit Binds", "Name each bind, pick its keys, and choose where it works.", button = "Edit", dependsOn = binds)
                    number(g::sequenceGap, "Sequence Gap", "Set how long a bind whose keys go in order waits for its next key.", 50f..1000f, step = 50f, unit = " ms", dependsOn = binds)
                }
            }
            section("Interface", Role.MUTED) {
                val hud = c.hud
                group("Theme") {
                    pick(hud::theme, "Theme", "Pick the colors of ds-mod's screens and controls.") { Look.Swatch(it) }
                }
                group("HUD") {
                    number(hud::scale, "Scale", "Make every HUD block bigger or smaller.", 0.5f..3f, step = 0.25f, unit = "x", id = "hudScale")
                    switch(hud::background, "Background", "Show a dark plate behind the HUD, so it reads over a bright world.", id = "hudBackground")
                    action(hud::edit, "Move HUD", "Drag a block over the game to move it, and scroll over it to resize it.", button = "Move", id = "moveHud")
                }
            }
            section("General", Role.VALUE) {
                val g = c.general
                group("Server") {
                    switch(g::onlyOnDragSim, "DragSim Only", "Run ds-mod only while you play on dragsim.co.")
                }
                group("World") {
                    switch(g::fullbright, "Fullbright", "Light the whole world, so caves and nights are as bright as day.")
                    switch(g::explosionParticleHider, "Explosion Particles", "Hide the clouds that explosions throw up.")
                }
                group("Contribute") {
                    action(c.contribute::open, "Requests", "See what ds-mod's developers ask players to record, and record it.", id = "requests")
                    switch(c.contribute::reportUnknowns, "Report Unread Lines", "Send lines the mod could not read to ds-mod's developers, with player names removed.")
                }
            }
        }.sections
        for (section in sections) for (group in section.groups) link(group.rows, group.switch)
        return sections
    }
}
