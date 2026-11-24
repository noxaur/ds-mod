package io.github.noxaur.dsmod.core.feature.utilities.commandbinds

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds as BindKind

class CommandBinds(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "command-binds", Area.ANYWHERE, Store.GENERAL, "Send a command, such as /warp end, with a key or a few keys.")

    val sequenceGap = feature.number("sequenceGap", "Sequence Gap", "Set how many milliseconds a key sequence waits for its next key.", 250f, 50f..1000f, step = 50f, unit = " ms")

    val binds = feature.stored<List<CommandBind>>("binds", emptyList())

    val groups = feature.stored<List<String>>("bindGroups", emptyList())

    val collapsedGroups = feature.stored<List<String>>("collapsedGroups", emptyList())

    val offGroups = feature.stored<List<String>>("offGroups", emptyList())

    init {
        feature.rows {
            items(Grouped.bind(binds, groups, collapsedGroups, offGroups), "Binds", "Name each bind, pick its keys and choose where it works.", BindKind)
        }
    }

    fun live(): List<CommandBind> = if (offGroups.value.isEmpty()) binds.value else binds.value.filter { it.group !in offGroups.value }
}
