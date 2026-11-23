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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** @keep One container as the player last saw it, its items in the container's own order, and when, in the client's
 * milliseconds */
@Serializable
data class Seen(val title: String, val items: List<Item?> = emptyList(), val at: Long = 0)

@Serializable
data class ProfileData(val containers: Map<String, Seen> = emptyMap(), val features: Map<String, JsonElement> = emptyMap())

@Serializable
data class Players(val profile: String = PlayerData.UNNAMED, val profiles: Map<String, ProfileData> = emptyMap())

/** @keep The player's data, on the profile they are on now. Game folds every event into it before a feature sees one,
 * so a feature reads it without asking which profile that is, and a feature's `profiled` state follows the switch. */
class PlayerData internal constructor(private val container: ContainerState, private val metrics: Metrics) : Stored {
    private var saved = Players()
    private var changed = false
    private val slices = LinkedHashMap<String, Saved<*>>()

    private var inProfiles = false
    private var target: String? = null
    private var switching: String? = null

    /** @keep The name of the profile the player is on, or null until the mod has seen one. What the mod keeps before then
     * goes to the first name it learns */
    val profile: String? get() = saved.profile.takeUnless { it == UNNAMED }

    private val here: ProfileData get() = saved.profiles[saved.profile] ?: ProfileData()

    /** @keep Each container the player saw on this profile, by the backend's id: `inventory`, `ender_chest` and
     * `storage:<page - 1>`. The inventory holds the worn, carried and offhand slots */
    val containers: Map<String, Seen> get() = here.containers

    var shelf: Shelf? = null
        private set

    internal fun slice(id: String, state: Saved<*>) {
        slices[id] = state
        state.restore(here.features[id])
    }

    /** @keep A profile is switched in the Profile Management menu: the player clicks a profile, then Switch to Profile, and
     * the server closes the menu and sends that profile's inventory. A refusal comes as a chat line before the close.
     * The menu also marks the profile the player is on, which names it however the mod lost track. Returns whether the
     * profile changed */
    internal fun fold(time: Long, event: GameEvent): Boolean = when (event) {
        is ContainerOpened -> opened(time, event)
        is SlotChanged -> changed(time, event)
        is SlotClicked -> false.also { clicked(event) }
        is SystemMessage -> false.also { if (event.text.plain.startsWith(REFUSED)) switching = null }
        LeftServer -> false.also {
            shelf = null
            inProfiles = false
            target = null
            switching = null
        }
        else -> false
    }

    private fun opened(time: Long, event: ContainerOpened): Boolean {
        val to = switching
        switching = null
        inProfiles = event.title?.plain == PROFILES
        shelf = Shelf.of(event.title?.plain)?.takeIf { it.slots.last < event.size - ContainerState.PLAYER_SLOTS }
        shelf?.let { put(it.id, Seen(it.title, List(it.slots.count()) { null }, time)) }
        if (to == null || event.id != ContainerState.INVENTORY) return false
        switch(to, time)
        return true
    }

    private fun changed(time: Long, event: SlotChanged): Boolean {
        if (container.id == ContainerState.INVENTORY) {
            if (event.slot in INVENTORY) inventory(time)
            return false
        }
        shelf?.let { shelf ->
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

    // @keep The backend's answer for the account's own selected profile names it, when the player has not opened Profile Management
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

    /** @keep A per-profile state from before the layer was one file for every profile, so the profile the player is on now
     * takes it, unless that profile has its own already */
    fun takeOld(id: String, text: String): Boolean {
        val state = slices[id] ?: return false
        if (id in here.features) return false
        state.load(text)
        changed = true
        return true
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
        private const val PROFILES = "Profile Management"
        private const val SELECTED = "Selected"
        private const val IN_USE = "In use"
        private const val SWITCH = "Switch to Profile"
        private const val REFUSED = "You can switch profiles in "

        internal val FOLDS = setOf(ContainerOpened::class.java, SlotChanged::class.java, SlotClicked::class.java)
    }
}
