package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock

/**
 * Follows the Endstone Protector. The tab list's `Protector` row has its stage and the zealot kills the
 * lobby still needs before it emerges. The tab list never says the golem was defeated, and lags the chat by a
 * second or two, so the messages add when it emerged and when it fell.
 */
class ProtectorStatus(config: DsModConfig) :
    Feature(config, "protector-status", Area.END, Store.END, "Show the Protector's stage and the kills left until it emerges.", formerly = "golemStatus") {
    private var row: String? = null
    private var stage: String? = null
    private var tier = ""
    private var toGo = ""
    private var spawnedAt = 0L
    private var defeatedAt = 0L

    init {
        on<WorldChanged> { _, _ ->
            row = null
            stage = null
            spawnedAt = 0
            defeatedAt = 0
        }
        on<TabList> { _, _ ->
            val value = tab.info("Protector")
            if (value == row) return@on
            row = value
            val previous = stage
            stage = null
            if (value == null) return@on
            val (name, tiers, left) = ProtectorRow.parse(value) ?: return@on unparsed(value)
            // The tab list moving on from the fight ends it, whether or not the defeat reached chat.
            if (name == "Resting" || previous == "Spawned" && name != "Spawned") {
                spawnedAt = 0
                defeatedAt = 0
            }
            stage = name
            tier = tiers
            toGo = left?.toString().orEmpty()
        }
        line("☠ The Endstone Protector has emerged") { now ->
            spawnedAt = now
            defeatedAt = 0
        }
        line("The Endstone Protector has been defeated!") { now ->
            // The tab list ends the fight when the row leaves Spawned. A later defeat line is that fight, already over.
            if (stage == "Spawned" || spawnedAt > 0) defeatedAt = now
        }
    }

    override fun hud(now: Long): HudBlock? {
        // Chat is a second or two ahead of the tab list, so a fight that chat reported wins over the tab's stage.
        return when {
            defeatedAt > 0 -> block {
                row("Stage") { value("Defeated") }
                if (spawnedAt > 0) row("Fight") { value(clock(defeatedAt - spawnedAt)) }
            }
            spawnedAt > 0 || stage == "Spawned" -> block {
                row("Stage") { value("Spawned") }
                if (spawnedAt > 0) row("Alive") { value(clock(now - spawnedAt)) }
            }
            stage == "Resting" -> killsLeft("Resting")
            stage == "Rising" -> killsLeft("Rising $tier")
            else -> null
        }
    }

    private fun killsLeft(label: String) = block {
        row("Stage") { value(label) }
        row("Kills Left") { value(toGo) }
    }
}
