package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds as BindKind

/**
 * Sends a command the player picked when they press its key in the world. It reads nothing from the server, so
 * all of it is `CommandKeys` in the edge, with the binds in the settings and their rules in `core.input`.
 */
class CommandBinds(config: DsModConfig) :
    Feature(config, "command-binds", Area.ANYWHERE, Store.GENERAL, "Send a command, such as /warp end, with a key or a few keys.") {
    val sequenceGap = number("sequenceGap", "Sequence Gap", "Set how many milliseconds a key sequence waits for its next key.", 250f, 50f..1000f, step = 50f, unit = " ms")

    /** Every bind, in the order the player put them, each naming its group. */
    val binds = stored<List<CommandBind>>("binds", emptyList())

    /** The groups after the first, which is unnamed, in the order the player made them. */
    val groups = stored<List<String>>("bindGroups", emptyList())

    /** The groups whose binds are folded away in the list. */
    val collapsedGroups = stored<List<String>>("collapsedGroups", emptyList())

    /** The groups switched off, whose binds send nothing whatever their own switch says. */
    val offGroups = stored<List<String>>("offGroups", emptyList())

    init {
        rows {
            val list = Bind(
                binds.bind.field,
                { Grouped(binds.value, groups.value, collapsedGroups.value, offGroups.value) },
                {
                    binds.value = it.items
                    groups.value = it.groups
                    collapsedGroups.value = it.folded
                    offGroups.value = it.off
                },
            ).spanning(groups.bind.field, collapsedGroups.bind.field, offGroups.bind.field)
            items(list, "Binds", "Name each bind, pick its keys and choose where it works.", BindKind)
        }
    }

    /** The binds whose group is on. */
    fun live(): List<CommandBind> = if (offGroups.value.isEmpty()) binds.value else binds.value.filter { it.group !in offGroups.value }
}
