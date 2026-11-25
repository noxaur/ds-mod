package io.github.noxaur.dragen.core.feature.end.protector.protectorstatus

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ProtectorRow
import io.github.noxaur.dragen.core.game.ProtectorStage
import io.github.noxaur.dragen.core.game.ProtectorStage.RESTING
import io.github.noxaur.dragen.core.game.ProtectorStage.RISING
import io.github.noxaur.dragen.core.game.ProtectorStage.SPAWNED
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.text.clock

/** @keep The tab list's `Protector` row never says the golem was defeated and lags the chat by a second or two, so
 * the messages add when it emerged and when it fell */
class ProtectorStatus(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "protector-status", Area.END, Store.END, "Show the Protector's stage and the kills left until it emerges.", formerly = listOf("golemStatus"), hud = ::hud)

    private var row: String? by feature.world { null }
    private var stage: ProtectorStage? by feature.world { null }
    private var tier = ""
    private var toGo = ""
    private var spawnedAt by feature.world { 0L }
    private var defeatedAt by feature.world { 0L }

    init {
        feature.on<TabList> { _, _ ->
            val value = feature.tab.info("Protector")
            if (value == row) return@on
            row = value
            val previous = stage
            stage = null
            if (value == null) return@on
            val (name, tiers, left) = ProtectorRow.parse(value) ?: return@on feature.unparsed(value)
            if (name == RESTING || previous == SPAWNED && name != SPAWNED) {
                spawnedAt = 0
                defeatedAt = 0
            }
            stage = name
            tier = tiers
            toGo = left?.toString().orEmpty()
        }
        feature.line("☠ The Endstone Protector has emerged") { now ->
            spawnedAt = now
            defeatedAt = 0
        }
        feature.line("The Endstone Protector has been defeated!") { now ->
            if (stage == SPAWNED || spawnedAt > 0) defeatedAt = now
        }
    }

    fun hud(now: Long): HudBlock? {
        return when {
            defeatedAt > 0 -> feature.block {
                row("Stage") { value("Defeated") }
                if (spawnedAt > 0) row("Fight") { value(clock(defeatedAt - spawnedAt)) }
            }
            spawnedAt > 0 || stage == SPAWNED -> feature.block {
                row("Stage") { value(SPAWNED.label) }
                if (spawnedAt > 0) row("Alive") { value(clock(now - spawnedAt)) }
            }
            stage == RESTING -> killsLeft(RESTING.label)
            stage == RISING -> killsLeft("${RISING.label} $tier")
            else -> null
        }
    }

    private fun killsLeft(label: String) = feature.block {
        row("Stage") { value(label) }
        row("Kills Left") { value(toGo) }
    }
}
