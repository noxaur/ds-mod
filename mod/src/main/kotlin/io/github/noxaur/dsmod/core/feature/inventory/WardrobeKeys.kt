package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.text.Role

class WardrobeKeys(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "wardrobe-keys", Area.ANYWHERE, Store.INVENTORY, "Press 1 to 9 in the wardrobe to click that slot.", slots = ::slots)

    val keys = (1..SLOTS).map { n -> feature.keys("wardrobeKey$n", "Slot $n", "Pick the key that clicks wardrobe slot $n.", KEY_1 + n - 1) }

    private val keyList = feature.fold("wardrobeKeys", "Keys", "Pick the key for each wardrobe slot.", *keys.toTypedArray())

    private var openedAt by feature.world { 0L }
    private var queued by feature.world<Int?> { null }
    private var marks by feature.world { emptyMap<Int, SlotMark>() }

    init {
        feature.on<ContainerOpened> { _, now ->
            openedAt = now
            hold(null)
        }
    }

    private val open: Boolean get() = feature.container.title?.plain == TITLE

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

    fun wait(now: Long): Long = if (open) (openedAt + WAIT_MILLIS - now).coerceAtLeast(0) else 0

    private fun hold(index: Int?) {
        queued = index
        marks = index?.let(::button)?.let { mapOf(it to SlotMark(Role.ACTION, outline = true)) } ?: emptyMap()
    }

    private fun button(index: Int): Int? = (BUTTON_ROW * ContainerState.ROW_SLOTS + index).takeIf { slot ->
        slot in feature.container.menuSlots && feature.container.slots[slot]?.name?.plain?.let(BUTTON::matches) == true
    }

    private fun slots(now: Long): Map<Int, SlotMark> = marks

    companion object {
        const val TITLE = "Wardrobe"
        const val SLOTS = 9
        private const val BUTTON_ROW = 4
        const val WAIT_MILLIS = 1000L
        private const val KEY_1 = 49
        private val BUTTON = Regex("Slot \\d+")
    }
}
