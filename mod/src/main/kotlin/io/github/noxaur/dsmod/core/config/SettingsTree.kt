package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.camel
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
import io.github.noxaur.dsmod.core.feature.general.Waypoints
import io.github.noxaur.dsmod.core.feature.general.ActivePet
import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.AutoClick
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
import io.github.noxaur.dsmod.core.feature.general.ItemOverview
import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.feature.hud.ChatHudEditor
import io.github.noxaur.dsmod.core.feature.hud.HideInF3
import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.betterUi
import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.HideGlassPanes
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.ForgeCraft
import io.github.noxaur.dsmod.core.feature.inventory.HotdButton
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.ProfileCooldown
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.feature.inventory.SkyBlockMenuClick
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.inventory.StorageOverview
import io.github.noxaur.dsmod.core.feature.inventory.WardrobeKeys
import io.github.noxaur.dsmod.core.feature.loot.RareDropAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme

class SettingGroup(val name: String?, val rows: List<Setting>, val switch: Switch? = null)

class SettingSection(val name: String, val icon: Role, val groups: List<SettingGroup>) {
    val named: List<SettingGroup> = groups.filter { it.name != null }

    fun walk(): Sequence<Setting> = groups.asSequence().flatMap { group -> listOfNotNull(group.switch).asSequence() + group.rows.asSequence().flatMap { it.walk() } }
}

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

class SettingsTree(val config: DsModConfig = DsModConfig(), mac: Boolean = MAC) {
    val sections: List<SettingSection> = declare(config, mac)

    val owners: List<HasFeature> = sections.asSequence().flatMap { it.walk() }.filterIsInstance<Switch>().mapNotNull { it.feature }
        .sortedBy { it.feature.area == Area.ANYWHERE }.toList()

    val features: List<Feature> = owners.map { it.feature }

    init {
        val defaults = fresh.asSequence().flatMap { it.walk() }.associateBy { it.id }
        for (setting in sections.asSequence().flatMap { it.walk() }) if (setting is Valued<*>) setting.adopt(defaults.getValue(setting.id))
        val repeated = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
        check(repeated.isEmpty()) { "A feature is placed twice in SettingsTree, or two features share an id: $repeated" }
    }

    fun <F : HasFeature> feature(type: Class<F>): F = type.cast(owners.first { it.javaClass == type })

    inline fun <reified F : HasFeature> feature(): F = feature(F::class.java)

    companion object {
        val MAC = "mac" in System.getProperty("os.name").lowercase()

        private val fresh by lazy { declare(DsModConfig(), mac = true) }

        fun home(switch: Switch): SettingSection? = fresh.firstOrNull { section -> section.walk().any { it is Switch && it.field == switch.field } }

        /** @keep Versions up to 0.1.0 starred MoulConfig's options by path, as general.fullbright, and a feature's switch by
         * its field's name. A feature that became a switch of another's, as Auto Left Click, keeps its star through the
         * camel case of its old id. */
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
            return saved.mapNotNull { if (it in ids) it else byField[it] ?: byField[camel(it)] ?: BUTTONS[it] }.distinct()
        }

        private val BUTTONS = mapOf(
            "general.editBinds" to "binds",
            "general.resetAnimations" to "resetAnimations",
            "end.editBeam" to "beamSections",
            "betterUi.editTabs" to "tabs",
            "inventory.editRarityStyle" to "style",
            "hud.edit" to "moveHud",
            "contribute.open" to "requests",
        )

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
                section("General", Role.VALUE) {
                    group("Server") {
                        feature(DragSimOnly(c))
                    }
                    group("Settings") {
                        keys(g.settingsKey.bind, "Settings Key", "Pick a key that opens these settings, as /dsmod does.")
                        keys(g.favoriteKey.bind, "Favorite Key", "Pick a key that stars the setting under your mouse.", clicks = false)
                    }
                    if (mac) group("Mouse") {
                        feature(ctrlClick)
                    }
                    group("World") {
                        feature(Fullbright(c))
                        feature(HideExplosions(c))
                    }
                    group(EtherwarpOutline(c), "Etherwarp")
                    group(AutoClick(c))
                    group(AbilityCooldowns(c))
                    group(Animations(c))
                    group(PlayerModel(c))
                    group(ProfileViewer(c))
                    group(ItemOverview(c))
                    group(Waypoints(c))
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
                        pick(hud.theme.bind, "Theme", "Pick the colors of ds-mod's screens and controls.", Theme.entries) { Look.Swatch(it) }
                    }
                    group("HUD") {
                        number(hud.scale.bind, "Scale", "Make every HUD block bigger or smaller.", 0.5f..3f, step = 0.25f, unit = "x", id = "hudScale")
                        feature(HudBackground(c))
                        panel(hud.positions.bind, "Layout", "Drag a block to move it, and scroll over it to resize it.", Canvas.HUD_LAYOUT, id = "hudLayout") {
                            if (it.isEmpty()) "Stacked" else if (it.size == 1) "1 block moved" else "${it.size} blocks moved"
                        }
                        action(Opens.HUD_EDITOR, "Move HUD", "Move your HUD over the game, with the world behind it.", button = "Move", id = "moveHud")
                        feature(ChatHudEditor(c))
                        keys(hud.editKey.bind, "HUD Editor Key", "Pick a key that opens the HUD editor, or none.", id = "hudEditKey")
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
                    group(betterUi(c)) {
                        feature(BackButtons(c))
                        feature(MenuTabs(c))
                        feature(SkyBlockMenuClick(c))
                        feature(HotdButton(c))
                    }
                    group(StorageOverview(c))
                    group(ForgeCraft(c))
                    group(WardrobeKeys(c))
                    group(ProfileCooldown(c))
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
