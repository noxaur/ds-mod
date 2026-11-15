package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import kotlin.reflect.KMutableProperty0

/**
 * Follows the Endstone Protector. The tab list's `Protector` row has its stage and the zealot kills the
 * lobby still needs before it emerges. The tab list never says the golem was defeated, and lags the chat by a
 * second or two, so the messages add when it emerged and when it fell.
 */
class GolemStatus(toggle: KMutableProperty0<Boolean>) : Feature("golem-status", toggle, Area.END) {
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
        on<SystemMessage> { message, now ->
            val line = message.text.plain
            when {
                line.startsWith("☠ The Endstone Protector has emerged") -> {
                    spawnedAt = now
                    defeatedAt = 0
                }
                line == "The Endstone Protector has been defeated!" -> defeatedAt = now
            }
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
            stage == "Resting" -> block {
                row("Stage") { value("Resting") }
                row("Kills Left") { value(toGo) }
            }
            stage == "Rising" -> block {
                row("Stage") { value("Rising $tier") }
                row("Kills Left") { value(toGo) }
            }
            else -> null
        }
    }
}
