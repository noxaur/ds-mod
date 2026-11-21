package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.boss.SlayerClaim
import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.feature.chat.HideCooldownMessages
import io.github.noxaur.dsmod.core.feature.contribute.ReportGameData
import io.github.noxaur.dsmod.core.feature.end.BossFightTracker
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.FireballAlert
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.end.ProtectorStatus
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.general.AbilityCooldowns
import io.github.noxaur.dsmod.core.feature.general.ActivePet
import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.CtrlClick
import io.github.noxaur.dsmod.core.feature.general.DragSimOnly
import io.github.noxaur.dsmod.core.feature.general.DamageNumbers
import io.github.noxaur.dsmod.core.feature.general.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.general.HideExplosions
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.PerformanceStats
import io.github.noxaur.dsmod.core.feature.general.PlayerModel
import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.feature.hud.ChatHudEditor
import io.github.noxaur.dsmod.core.feature.hud.HideInF3
import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.HideGlassPanes
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.HotdButton
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.loot.RareDropAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look

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

    fun group(name: String?, rows: Rows.() -> Unit) {
        groups += SettingGroup(name, Rows().apply(rows).rows)
    }

    fun group(feature: HasFeature, name: String = feature.feature.title, rows: (Rows.() -> Unit)? = null) {
        val more = if (rows == null) emptyList() else Rows().apply(rows).rows
        groups += SettingGroup(name, feature.feature.rows + more, feature.feature.switch.also { it.feature = feature })
    }
}

/**
 * Every setting of ds-mod, in the order the settings screen shows them, bound to a [config]. The tree is the only
 * schema of the settings. A feature declares its own rows, and the tree places it once, as a group of its own or a
 * row among others, which is also what lists it in [features]: a feature placed nowhere never runs. A setting that
 * belongs to no feature is a field of `DsModConfig` and one line here with an existing kind. `SettingsTreeTest` fails
 * on a saved field that no row reaches and that [STORED] does not name. A row for macOS alone is in the tree only
 * when [mac] is true.
 */
class SettingsTree(val config: DsModConfig = DsModConfig(), mac: Boolean = MAC) {
    /** The sections, with each row's default taken from the same row of a tree on a fresh config. */
    val sections: List<SettingSection> = declare(config, mac)

    /**
     * Every feature, the class that holds its [Feature], in the order the HUD stacks their blocks in: the features of one area first, since
     * their blocks are what the player is there for, then the ones that show everywhere, each in the tree's order.
     */
    val owners: List<HasFeature> = sections.asSequence().flatMap { it.walk() }.filterIsInstance<Switch>().mapNotNull { it.feature }
        .sortedBy { it.feature.area == Area.ANYWHERE }.toList()

    /** The [Feature] each of [owners] holds, in the same order, which `Game` runs. */
    val features: List<Feature> = owners.map { it.feature }

    init {
        val defaults = fresh.asSequence().flatMap { it.walk() }.associateBy { it.id }
        for (setting in sections.asSequence().flatMap { it.walk() }) if (setting is Valued<*>) setting.adopt(defaults.getValue(setting.id))
        val repeated = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
        check(repeated.isEmpty()) { "A feature is placed twice in SettingsTree, or two features share an id: $repeated" }
    }

    /** The feature of [type]. */
    fun <F : HasFeature> feature(type: Class<F>): F = type.cast(owners.first { it.javaClass == type })

    inline fun <reified F : HasFeature> feature(): F = feature(F::class.java)

    companion object {
        /** Saved fields that no row edits, each with the reason. Every saved field has a row now. */
        val STORED = emptyMap<String, String>()

        /** Whether the game runs on macOS, as Minecraft's own check reads it. */
        val MAC = "mac" in System.getProperty("os.name").lowercase()

        /** Every row of every system, so a row for one system finds its default, its home and its favorite. */
        private val fresh by lazy { declare(DsModConfig(), mac = true) }

        /** The section that holds [switch], such as a feature's, or null for one the tree does not place, as a test's feature. */
        fun home(switch: Switch): SettingSection? = fresh.firstOrNull { section -> section.walk().any { it is Switch && it.field == switch.field } }

        /**
         * [saved] favorites as row ids. A feature's switch was starred under its field's name, as `fullbright`, before
         * the row took the feature's id, and versions up to 0.1.0 starred MoulConfig's options by path, as
         * `general.fullbright`. Either becomes the id of the row that edits that field, by its current or a former
         * key, or of the row that took over a button's job.
         */
        fun favorites(saved: List<String>): List<String> {
            val rows = fresh.flatMap { it.walk() }
            val ids = rows.mapTo(HashSet()) { it.id }
            val byField = HashMap<String, String>()
            for (row in rows.filterIsInstance<Valued<*>>()) {
                val section = row.field.substringBefore('.').replaceFirstChar(Char::lowercase)
                for (key in listOf(row.field.substringAfter('.')) + row.formerly) {
                    byField.putIfAbsent(key, row.id)
                    byField.putIfAbsent("$section.$key", row.id)
                }
            }
            return saved.mapNotNull { if (it in ids) it else byField[it] ?: BUTTONS[it] }.distinct()
        }

        /** MoulConfig's buttons, by path, to the row that does their job now. */
        private val BUTTONS = mapOf(
            "general.editBinds" to "binds",
            "general.resetAnimations" to "resetAnimations",
            "end.editBeam" to "beamSections",
            "betterUi.editTabs" to "tabs",
            "inventory.editRarityStyle" to "customLook",
            "hud.edit" to "moveHud",
            "contribute.open" to "requests",
        )

        /** Sets each row's [Setting.gate]: the switch it sits under. */
        fun link(rows: List<Setting>, gate: Switch?) {
            for (row in rows) {
                row.gate = gate
                link(row.children, if (row is Switch) row else gate)
            }
        }

        private fun declare(c: DsModConfig, mac: Boolean): List<SettingSection> {
            val g = c.general
            val claim = SlayerClaim()
            val ctrlClick = CtrlClick(c)
            val sections = Sections().apply {
                // What applies everywhere comes first, then each area of the game, and Contribute last.
                section("General", Role.VALUE) {
                    group("Server") {
                        feature(DragSimOnly(c))
                    }
                    group("Settings") {
                        keys(g::settingsKey, "Settings Key", "Pick a key that opens these settings, as /dsmod does.")
                        keys(g::favoriteKey, "Favorite Key", "Pick a key that stars the setting under your mouse.")
                    }
                    if (mac) group("Mouse") {
                        feature(ctrlClick)
                    }
                    group("World") {
                        feature(Fullbright(c))
                        feature(HideExplosions(c))
                    }
                    group(EtherwarpOutline(c), "Etherwarp")
                    group(AbilityCooldowns(c))
                    group(Animations(c))
                    group(PlayerModel(c))
                    group(ProfileViewer(c))
                }
                section("Camera", Role.ACTION) {
                    group(Camera(c), "Third Person")
                    group(FreeLook(c))
                }
                section("Binds", Role.DIVINE) {
                    group(CommandBinds(c))
                }
                section("Interface", Role.MUTED) {
                    val hud = c.hud
                    group("Theme") {
                        pick(hud::theme, "Theme", "Pick the colors of ds-mod's screens and controls.") { Look.Swatch(it) }
                    }
                    group("HUD") {
                        number(hud::scale, "Scale", "Make every HUD block bigger or smaller.", 0.5f..3f, step = 0.25f, unit = "x", id = "hudScale")
                        feature(HudBackground(c))
                        val positions = Bind("Hud.positions", { HashMap(hud.positions) }, {
                            hud.positions.clear()
                            hud.positions.putAll(it)
                        })
                        panel(positions, "Layout", "Drag a block to move it, and scroll over it to resize it.", Canvas.HUD_LAYOUT, id = "hudLayout") {
                            if (it.isEmpty()) "Stacked" else if (it.size == 1) "1 block moved" else "${it.size} blocks moved"
                        }
                        action(Opens.HUD_EDITOR, "Move HUD", "Move your HUD over the game, with the world behind it.", button = "Move", id = "moveHud")
                        feature(ChatHudEditor(c))
                        keys(hud::editKey, "HUD Editor Key", "Pick a key that opens the HUD editor, or none.", id = "hudEditKey")
                        feature(HideInF3(c))
                    }
                    group(EquipmentDisplay(c))
                    group(PerformanceStats(c))
                    group(ActivePet(c))
                    group(InventoryHud(c))
                }
                section("Chat", Role.WARNING) {
                    group(null) {
                        feature(HideCooldownMessages(c))
                        feature(CopyChat(c))
                    }
                }
                section("Inventory", Role.GOOD) {
                    group(ItemRarity(c))
                    group("Protection") {
                        feature(SlotLock(c))
                        feature(ArmorSwapGuard(c))
                    }
                }
                section("Menus", Role.LEGENDARY) {
                    group("Look") {
                        feature(MenuTheme(c))
                        feature(FixedMenuLayout(c))
                        feature(HideGlassPanes(c))
                        feature(KeepCursor(c))
                    }
                    group(BetterUi(c)) {
                        feature(BackButtons(c))
                        feature(MenuTabs(c))
                        feature(HotdButton(c))
                    }
                }
                section("The End", Role.EPIC) {
                    group("Dragon") {
                        feature(DragonTimer(c))
                        feature(FireballAlert(c))
                        feature(EyeGuard(c))
                    }
                    group("Protector") {
                        feature(ProtectorStatus(c))
                        feature(ProtectorSpawnBeam(c))
                    }
                    group("Drops") {
                        feature(BossFightTracker(c))
                        feature(ZealotTracker(c))
                    }
                }
                section("Combat", Role.BAD) {
                    group(DamageNumbers(c))
                    group("Slayers") {
                        feature(SlayerTracker(c))
                        feature(SlayerBossAlert(c))
                    }
                    group("Bosses") {
                        feature(BossBeacon(c, claim))
                        feature(BossTracer(c, claim))
                    }
                    group("Loot") {
                        feature(RareDropAlert(c))
                    }
                }
                section("Contribute", Role.RARE) {
                    group(null) {
                        action(Opens.REQUESTS, "Requests", "See what ds-mod's developers ask players to record.", id = "requests")
                        feature(ReportGameData(c))
                    }
                }
            }.sections
            for (section in sections) for (group in section.groups) link(group.rows, group.switch)
            val plain = sections.flatMap { it.groups }.filter { it.switch == null }.flatMap { loose(it.rows) }.filterIsInstance<Switch>()
            check(plain.isEmpty()) {
                "Every on/off setting is a feature, so place each of these in SettingsTree as feature(Feature(c)), with its own class in core/feature: ${plain.map { it.id }}"
            }
            return sections
        }

        private fun loose(rows: List<Setting>): List<Setting> = rows.flatMap { if (it is Switch && it.feature != null) emptyList() else listOf(it) + loose(it.children) }
    }
}
