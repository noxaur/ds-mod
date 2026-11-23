package io.github.noxaur.dsmod.core.player

import io.github.noxaur.dsmod.core.feature.Saved
import io.github.noxaur.dsmod.core.feature.Stored
import io.github.noxaur.dsmod.core.feature.savedJson
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SlotClicked
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.text.count
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Seen(val title: String, val items: List<Item?> = emptyList(), val at: Long = 0)

@Serializable
data class ProfileData(val containers: Map<String, Seen> = emptyMap(), val features: Map<String, JsonElement> = emptyMap())

@Serializable
data class Players(val profile: String = PlayerData.UNNAMED, val profiles: Map<String, ProfileData> = emptyMap())

class PlayerData internal constructor(private val container: ContainerState, private val metrics: Metrics) : Stored {
    private var saved = Players()
    private var changed = false
    private val slices = LinkedHashMap<String, Saved<*>>()

    private var inProfiles = false
    private var target: String? = null
    private var switching: String? = null
    private var unread = false

    var switchesAt = 0L
        private set

    val profile: String? get() = saved.profile.takeUnless { it == UNNAMED }

    private val here: ProfileData get() = saved.profiles[saved.profile] ?: ProfileData()

    val containers: Map<String, Seen> get() = here.containers

    var shelf: Shelf? = null
        private set

    internal fun slice(id: String, state: Saved<*>) {
        slices[id] = state
        state.restore(here.features[id])
    }

    /** @keep The server closes the Profile Management menu after Switch to Profile and then sends that profile's inventory; a refusal is a chat line before the close. The menu also marks the profile the player is on. */
    internal fun fold(time: Long, event: GameEvent): Boolean = when (event) {
        is ContainerOpened -> opened(time, event)
        is SlotChanged -> changed(time, event)
        is SlotClicked -> false.also { clicked(event) }
        is SystemMessage -> false.also { refused(time, event.text.plain) }
        LeftServer -> false.also {
            shelf = null
            inProfiles = false
            target = null
            switching = null
        }
        else -> false
    }

    internal fun wants(type: Class<out GameEvent>) = type in FOLDS || switching != null && type == SystemMessage::class.java

    private fun opened(time: Long, event: ContainerOpened): Boolean {
        val to = switching
        switching = null
        inProfiles = event.title?.plain == PROFILES
        shelf = Shelf.of(event.title?.plain)?.takeIf { it.slots.last < event.size - ContainerState.PLAYER_SLOTS }
        unread = shelf != null
        if (to == null || event.id != ContainerState.INVENTORY) return false
        switch(to, time)
        switchesAt = time + COOLDOWN_MILLIS
        return true
    }

    // @keep The refusal rounds to the second, so a switch the mod saw is kept when it agrees to within one
    private fun refused(time: Long, line: String) {
        if (!line.startsWith(REFUSED)) return
        switching = null
        val seconds = REFUSAL.matchEntire(line)?.groupValues?.get(1)?.let(::count) ?: return
        val heard = time + seconds * 1000L
        if (switchesAt !in heard - 1000..heard + 1000) switchesAt = heard
    }

    private fun changed(time: Long, event: SlotChanged): Boolean {
        if (container.id == ContainerState.INVENTORY) {
            if (event.slot in INVENTORY) inventory(time)
            return false
        }
        shelf?.let { shelf ->
            if (unread) put(shelf.id, Seen(shelf.title, List(shelf.slots.count()) { null }, time))
            unread = false
            val i = event.slot - shelf.slots.first
            val seen = containers[shelf.id]
            if (seen != null && i in seen.items.indices) put(shelf.id, seen.copy(items = seen.items.toMutableList().also { it[i] = event.item }, at = time))
        }
        if (!inProfiles) return false
        val item = event.item ?: return false
        if (item.cleanLore.firstOrNull() != SELECTED) return false
        val name = item.name?.plain ?: return false
        return when (saved.profile) {
            name -> false
            UNNAMED -> false.also { adopt(name) }
            else -> true.also { switch(name, time) }
        }
    }

    private fun clicked(event: SlotClicked) {
        if (!inProfiles) return
        val item = container.slots.getOrNull(event.slot) ?: return
        when {
            item.name?.plain == SWITCH -> switching = target
            item.cleanLore.firstOrNull() == IN_USE -> target = item.name?.plain
        }
    }

    fun heard(answer: Profile) {
        val name = answer.profileName.plain.takeIf { it.isNotEmpty() } ?: return
        if (saved.profile == UNNAMED) adopt(name)
    }

    private fun inventory(time: Long) {
        val slots = container.inventorySlots
        if (slots.size != ContainerState.OFFHAND_SLOT + 1) return
        put(INVENTORY_ID, Seen(INVENTORY_TITLE, INVENTORY.map { slots[it] }, time))
    }

    private fun put(id: String, seen: Seen) {
        saved = saved.copy(profiles = saved.profiles + (saved.profile to here.copy(containers = here.containers + (id to seen))))
        changed = true
    }

    private fun keepSlices() {
        saved = saved.copy(profiles = saved.profiles + (saved.profile to here.copy(features = here.features + slices.mapValues { it.value.json()() })))
    }

    // @keep The inventory on screen is still the old profile's until the server sends the new one's, slot by slot, so it is copied to the new profile first
    private fun switch(name: String, time: Long) {
        keepSlices()
        saved = saved.copy(profile = name)
        restoreSlices()
        inventory(time)
        target = null
        changed = true
    }

    private fun adopt(name: String) {
        keepSlices()
        val unnamed = here
        val named = saved.profiles[name] ?: ProfileData()
        val merged = ProfileData(
            unnamed.containers + named.containers.filter { (id, seen) -> seen.at >= (unnamed.containers[id]?.at ?: 0) },
            unnamed.features + named.features,
        )
        saved = Players(name, saved.profiles - UNNAMED + (name to merged))
        restoreSlices()
        changed = true
    }

    private fun restoreSlices() {
        for ((id, state) in slices) {
            try {
                state.restore(here.features[id])
            } catch (e: Exception) {
                metrics.fail("$id.load", e)
                state.restore(null)
            }
        }
    }

    override fun load(text: String) {
        saved = savedJson.decodeFromString(Players.serializer(), text)
        restoreSlices()
        changed = false
    }

    fun takeOld(id: String, text: String) {
        val state = slices[id] ?: return
        changed = true
        if (id !in here.features) state.load(text)
    }

    override fun takeChanges(): (() -> String)? {
        val sliced = slices.filterValues { it.takeChanges() != null }
        if (!changed && sliced.isEmpty()) return null
        changed = false
        val players = saved
        val live = slices.mapValues { it.value.json() }
        return {
            val here = players.profiles[players.profile] ?: ProfileData()
            val all = players.copy(profiles = players.profiles + (players.profile to here.copy(features = here.features + live.mapValues { it.value() })))
            savedJson.encodeToString(Players.serializer(), all)
        }
    }

    override fun unwritten() {
        changed = true
    }

    companion object {
        const val UNNAMED = ""
        const val INVENTORY_ID = "inventory"
        private const val INVENTORY_TITLE = "Inventory"
        private val INVENTORY = ContainerState.WORN_SLOTS.first..ContainerState.OFFHAND_SLOT
        const val PROFILES = "Profile Management"
        private const val SELECTED = "Selected"
        private const val IN_USE = "In use"
        private const val SWITCH = "Switch to Profile"
        private const val REFUSED = "You can switch profiles in "
        private val REFUSAL = Regex("$REFUSED(\\d+)s\\.")
        const val COOLDOWN_MILLIS = 30_000L

        private val FOLDS = setOf(ContainerOpened::class.java, SlotChanged::class.java, SlotClicked::class.java)
    }
}
