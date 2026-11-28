package io.github.noxaur.dragen.core.config

import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.feature.camel
import io.github.noxaur.dragen.core.feature.combat.combat
import io.github.noxaur.dragen.core.feature.contribute.contribute
import io.github.noxaur.dragen.core.feature.end.end
import io.github.noxaur.dragen.core.feature.general.general
import io.github.noxaur.dragen.core.feature.hud.hud
import io.github.noxaur.dragen.core.feature.inventory.inventory
import io.github.noxaur.dragen.core.feature.menus.menus
import io.github.noxaur.dragen.core.feature.mining.mining
import io.github.noxaur.dragen.core.feature.utilities.utilities
import io.github.noxaur.dragen.core.feature.visuals.visuals
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.text.Role

class SettingGroup(val name: String?, val rows: List<Setting>, val switch: Switch? = null)

class SettingSection(val name: String, val icon: Role, val groups: List<SettingGroup>) {
    val named: List<SettingGroup> = groups.filter { it.name != null }

    fun walk(): Sequence<Setting> = groups.asSequence().flatMap { group -> listOfNotNull(group.switch).asSequence() + group.rows.asSequence().flatMap { it.walk() } }
}

class Sections(val c: DragenConfig, val mac: Boolean) {
    internal val sections = ArrayList<SettingSection>()

    fun section(name: String, icon: Role, groups: Groups.() -> Unit) {
        sections += SettingSection(name, icon, Groups(c, mac).apply(groups).groups)
    }
}

class Groups(val c: DragenConfig, val mac: Boolean) {
    internal val groups = ArrayList<SettingGroup>()

    fun group(name: String?, rows: Rows.() -> Unit) {
        groups += SettingGroup(name, Rows().apply(rows).rows)
    }

    fun group(feature: HasFeature, name: String = feature.feature.title, rows: (Rows.() -> Unit)? = null) {
        val more = if (rows == null) emptyList() else Rows().apply(rows).rows
        groups += SettingGroup(name, feature.feature.rows + more, feature.feature.switch.also { it.feature = feature })
    }
}

interface DevTools {
    fun declare(sections: Sections)

    fun started(tree: SettingsTree, game: Game)
}

class SettingsTree(val config: DragenConfig = DragenConfig(), mac: Boolean = MAC, dev: List<DevTools> = emptyList()) {
    val sections: List<SettingSection> = declare(config, mac, dev)

    val owners: List<HasFeature> = sections.asSequence().flatMap { it.walk() }.filterIsInstance<Switch>().mapNotNull { it.feature }
        .sortedBy { it.feature.area == Area.ANYWHERE }.toList()

    val features: List<Feature> = owners.map { it.feature }

    init {
        val defaults = fresh(dev).asSequence().flatMap { it.walk() }.associateBy { it.id }
        for (setting in sections.asSequence().flatMap { it.walk() }) if (setting is Valued<*>) setting.adopt(defaults.getValue(setting.id))
        val repeated = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
        check(repeated.isEmpty()) { "A feature is placed twice in SettingsTree, or two features share an id: $repeated" }
    }

    fun <F : HasFeature> feature(type: Class<F>): F = type.cast(owners.first { it.javaClass == type })

    inline fun <reified F : HasFeature> feature(): F = feature(F::class.java)

    companion object {
        val MAC = "mac" in System.getProperty("os.name").lowercase()

        private val released by lazy { declare(DragenConfig(), mac = true, dev = emptyList()) }

        private fun fresh(dev: List<DevTools>) = if (dev.isEmpty()) released else declare(DragenConfig(), mac = true, dev)

        fun home(switch: Switch): SettingSection? = released.firstOrNull { section -> section.walk().any { it is Switch && it.field == switch.field } }

        /** @keep Versions up to 0.1.0 starred MoulConfig's options by path, as general.fullbright, and a feature's switch by its field's name. */
        fun favorites(saved: List<String>, dev: List<DevTools> = emptyList()): List<String> {
            val rows = fresh(dev).flatMap { it.walk() }
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

        private fun declare(c: DragenConfig, mac: Boolean, dev: List<DevTools>): List<SettingSection> {
            val sections = Sections(c, mac).apply {
                general()
                hud()
                end()
                combat()
                mining()
                inventory()
                menus()
                visuals()
                utilities()
                contribute()
                for (tools in dev) tools.declare(this)
            }.sections
            for (section in sections) for (group in section.groups) link(group.rows, group.switch)
            val plain = sections.flatMap { it.groups }.filter { it.switch == null }.flatMap { loose(it.rows) }.filterIsInstance<Switch>()
            check(plain.isEmpty()) {
                "Every on/off setting is a feature, so give each of these its own class in a folder of core/feature and place it as feature(Feature(c)) in the group file beside that folder: ${plain.map { it.id }}"
            }
            return sections
        }

        private fun loose(rows: List<Setting>): List<Setting> = rows.flatMap { if (it is Switch && it.feature != null) emptyList() else listOf(it) + loose(it.children) }
    }
}
