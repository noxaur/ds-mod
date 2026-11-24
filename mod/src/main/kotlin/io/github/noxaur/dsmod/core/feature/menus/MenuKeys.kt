package io.github.noxaur.dsmod.core.feature.menus

import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role

class MenuKeys(
    private val feature: Feature,
    private val title: String,
    val keys: List<KeyBind>,
    private val waitMillis: Long,
    private val button: (index: Int) -> Int?,
) {
    private var openedAt by feature.world { 0L }
    private var queued by feature.world<Int?> { null }

    var marks by feature.world { emptyMap<Int, SlotMark>() }
        private set

    init {
        feature.on<ContainerOpened> { _, now ->
            openedAt = now
            hold(null)
        }
        feature.on<SlotChanged> { _, _ -> if (queued != null) hold(queued) }
    }

    val runs: Boolean get() = feature.runs

    private val open: Boolean get() = feature.container.title?.plain == title

    fun open(menu: Int): Boolean = open && feature.container.id == menu

    fun index(bound: Int): Int? = if (open && bound >= 0) keys.indexOfFirst { it.value == bound }.takeIf { it >= 0 } else null

    fun press(index: Int, now: Long): Int? {
        hold(index)
        return due(now)
    }

    fun due(now: Long): Int? {
        val index = queued ?: return null
        if (wait(now) > 0) return null
        hold(null)
        return button(index)
    }

    fun wait(now: Long): Long = if (open) (openedAt + waitMillis - now).coerceAtLeast(0) else 0

    private fun hold(index: Int?) {
        queued = index
        val next = index?.let(button)?.let { mapOf(it to SlotMark(Role.ACTION, outline = true)) } ?: emptyMap()
        if (next == marks) return
        marks = next
        feature.redraw()
    }

    companion object {
        const val KEY_0 = 48
    }
}
