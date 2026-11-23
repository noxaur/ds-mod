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
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.text.number
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** @keep One container as the player last saw it, its items in the container's own order, and when, in the client's
 * milliseconds */
@Serializable
data class Seen(val title: String, val items: List<Item?> = emptyList(), val at: Long = 0)

/** @keep One stat as the tab list or the Dragon Sim Menu showed it last, and when. A percent keeps its number, so `98%`
 * is 98 */
@Serializable
data class Reading(val value: Double, val at: Long)

/** @keep One item of the Heart of the Dragon menu, and the tier of the row it sits in. A locked perk still reads
 * `Level 1/50`, so its lore, not that line, tells whether it is unlocked */
@Serializable
data class HeartItem(val item: Item, val tier: Int? = null)

/** @keep The Heart of the Dragon as its menu showed it last, each item by its name: the perks, the abilities, the Core
 * of the Dragon, the tiers and the Heart itself, which holds the tokens and the powder. The menu shows five tiers at a
 * time, so what the player scrolled to is merged in. `layout` is the Heart profile the player applied last, while the
 * tree is still the one it saved, and `layouts` keeps each Heart profile's tree as the mod saw it while applied */
@Serializable
data class Heart(
    val items: Map<String, HeartItem> = emptyMap(),
    val at: Long = 0,
    val layout: Int? = null,
    val layouts: Map<Int, Map<String, HeartItem>> = emptyMap(),
)

/** @keep `answer` is the backend's whole answer for this profile, kept as it came. `stats` holds every stat the tab
 * list or the Dragon Sim Menu showed on it, by its name, such as `Magic Find`, and a stat the tab list stopped showing
 * keeps its last reading */
@Serializable
data class ProfileData(
    val containers: Map<String, Seen> = emptyMap(),
    val features: Map<String, JsonElement> = emptyMap(),
    val answer: Profile? = null,
    val stats: Map<String, Reading> = emptyMap(),
    val heart: Heart = Heart(),
)

@Serializable
data class Players(val profile: String = PlayerData.UNNAMED, val profiles: Map<String, ProfileData> = emptyMap())

/** @keep The player's data, on the profile they are on now. Game folds every event into it before a feature sees one,
 * so a feature reads it without asking which profile that is, and a feature's `profiled` state follows the switch. */
class PlayerData internal constructor(private val container: ContainerState, private val metrics: Metrics) : Stored {
    private var saved = Players()
    private var changed = false
    private val slices = LinkedHashMap<String, Saved<*>>()

    private val menu get() = container.title?.plain
    private var applying: Int? = null
    private var target: String? = null
    private var switching: String? = null
    private var unread = false

    /** @keep Asks the backend for one of the account's own profiles, by its id, or the selected one for null. The
     * answer comes back through `heard` */
    var ask: ((profile: String?) -> Unit)? = null
    private val asked = HashSet<String>()
    private var due = true

    var switchesAt = 0L
        private set

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
    internal fun fold(time: Long, event: GameEvent): Boolean {
        val switched = when (event) {
            is ContainerOpened -> opened(time, event)
            is SlotChanged -> changed(time, event)
            is SlotClicked -> false.also { clicked(event) }
            is SystemMessage -> false.also {
                refused(time, event.text.plain)
                applied(event.text.plain)
            }
            is TabList -> false.also { tabStats(time, event) }
            LeftServer -> false.also {
                shelf = null
                applying = null
                target = null
                switching = null
                asked.clear()
                due = true
            }
            else -> false
        }
        if (due && event != LeftServer) ask()
        return switched
    }

    /** @keep A profile without an answer, or with a stale one, is asked for once each time the player joins. The id is
     * the one an earlier answer listed for the profile's name, and the selected profile is asked for when none did */
    private fun ask() {
        due = false
        val ask = ask ?: return
        if (here.answer?.stale == false) return
        val name = saved.profile
        val id = if (name == UNNAMED) null else saved.profiles.values.firstNotNullOfOrNull { data -> data.answer?.choices?.firstOrNull { it.name.plain == name }?.id }
        if (asked.add(id ?: "selected:$name")) ask(id)
    }

    // @keep The Stats rows follow a `Stats:` row up to the first blank one, as ` ✯ Magic Find 58`
    private fun tabStats(time: Long, event: TabList) {
        val rows = event.rows
        val start = rows.indexOfFirst { it.plain.trim() == STATS }
        if (start < 0) return
        val end = (start + 1 until rows.size).firstOrNull { rows[it].plain.isBlank() } ?: rows.size
        readStats(time, (start + 1 until end).map { rows[it].plain })
    }

    // @keep A row is a stat, its number and an optional `%`, after an icon some stats lack, as `  Health 862`
    private fun readStats(time: Long, rows: List<String>) {
        var stats = here.stats
        for (row in rows) {
            val match = STAT.matchEntire(row) ?: continue
            val value = number(match.groupValues[2]) ?: continue
            val name = match.groupValues[1]
            if (stats[name]?.value != value) stats = stats + (name to Reading(value, time))
        }
        if (stats === here.stats) return
        saved = saved.copy(profiles = saved.profiles + (saved.profile to here.copy(stats = stats)))
        changed = true
    }

    internal fun wants(type: Class<out GameEvent>) = type in FOLDS || (switching != null || applying != null) && type == SystemMessage::class.java

    private fun opened(time: Long, event: ContainerOpened): Boolean {
        val to = switching
        switching = null
        applying = null
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
            // @keep A menu closed before its slots arrive keeps the copy the player saw last
            if (unread) put(shelf.id, Seen(shelf.title, List(shelf.slots.count()) { null }, time))
            unread = false
            val i = event.slot - shelf.slots.first
            val seen = containers[shelf.id]
            if (seen != null && i in seen.items.indices) put(shelf.id, seen.copy(items = seen.items.toMutableList().also { it[i] = event.item }, at = time))
        }
        if (menu == HEART && event.slot in container.menuSlots) heartItem(time, event)
        if (menu == MENU && event.item?.name?.plain == SIM_PROFILE) readStats(time, event.item.lore.map { it.plain })
        if (menu != PROFILES) return false
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
        val item = container.slots.getOrNull(event.slot) ?: return
        val name = item.name?.plain
        if (menu == HEART && changesTree(item) || menu == RESET && name == CONFIRM) keepHeart(heart.copy(layout = null))
        menu?.let(APPLY::matchEntire)?.let { if (name == CONFIRM) applying = count(it.groupValues[1]) }
        if (menu != PROFILES) return
        when {
            item.name?.plain == SWITCH -> switching = target
            item.cleanLore.firstOrNull() == IN_USE -> target = item.name?.plain
        }
    }

    /** @keep The backend's answer for one of the account's own profiles is kept under the name it gives, which need not
     * be the profile the player is on, as the backend can still name the one they left. A stale answer never replaces a
     * fresh one. The answer for the selected profile names it, when the player has not opened Profile Management */
    fun heard(answer: Profile) {
        val name = answer.profileName.plain.takeIf { it.isNotEmpty() } ?: return
        if (saved.profile == UNNAMED && answer.choice.selected) adopt(name)
        val kept = saved.profiles[name] ?: ProfileData()
        if (answer.stale && kept.answer?.stale == false) return
        saved = saved.copy(profiles = saved.profiles + (name to kept.copy(answer = answer)))
        changed = true
        due = true
    }

    private val heart get() = here.heart

    private fun keepHeart(heart: Heart) {
        if (heart == this.heart) return
        saved = saved.copy(profiles = saved.profiles + (saved.profile to here.copy(heart = heart)))
        changed = true
    }

    // @keep A filler pane has no lore, and an arrow is gone once the menu scrolled as far as it goes
    private fun changesTree(item: Item): Boolean {
        val name = item.name?.plain ?: return false
        return item.lore.isNotEmpty() && name !in SCROLLS && name !in HEART_MENUS && !TIER.matches(name)
    }

    // @keep Each row of the tree starts with its tier's pane, which the server sends before the row's perks
    private fun heartItem(time: Long, event: SlotChanged) {
        val item = event.item ?: return
        val name = item.name?.plain ?: return
        if (item.lore.isEmpty() || name in SCROLLS) return
        val pane = container.slots.getOrNull(event.slot - event.slot % ContainerState.ROW_SLOTS)?.name?.plain
        val placed = HeartItem(item, pane?.let(TIER::matchEntire)?.let { count(it.groupValues[1]) })
        val heart = heart
        if (heart.items[name] == placed) return
        val layouts = heart.layout?.let { heart.layouts + (it to (heart.layouts[it].orEmpty() + (name to placed))) } ?: heart.layouts
        keepHeart(heart.copy(items = heart.items + (name to placed), at = time, layouts = layouts))
    }

    /** @keep Applying a Heart profile changes the whole tree, so the tree becomes the one the mod saw while that profile
     * was applied, or nothing until the menu shows it. A click on a perk or a confirmed reset may change the tree, so it
     * stops being kept as that profile's */
    private fun applied(line: String) {
        val number = APPLIED.matchEntire(line)?.groupValues?.get(1)?.let(::count) ?: return
        if (number != applying) return
        applying = null
        keepHeart(heart.copy(items = heart.layouts[number].orEmpty(), layout = number))
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
        due = true
    }

    private fun adopt(name: String) {
        keepSlices()
        val unnamed = here
        val named = saved.profiles[name] ?: ProfileData()
        val merged = ProfileData(
            unnamed.containers + named.containers.filter { (id, seen) -> seen.at >= (unnamed.containers[id]?.at ?: 0) },
            unnamed.features + named.features,
            named.answer,
            unnamed.stats + named.stats.filter { (stat, reading) -> reading.at >= (unnamed.stats[stat]?.at ?: 0) },
            if (named.heart.at >= unnamed.heart.at) named.heart else unnamed.heart,
        )
        saved = Players(name, saved.profiles - UNNAMED + (name to merged))
        restoreSlices()
        changed = true
        due = true
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
        private const val STATS = "Stats:"
        private val STAT = Regex("\\s*(?:[^\\p{L}\\s]+\\s+)?(\\p{L}[\\p{L}' ]*?)\\s+([-+]?[\\d,.]+)%?\\s*")
        private const val MENU = "Dragon Sim Menu"
        private const val SIM_PROFILE = "Your Dragon Sim Profile"
        private const val HEART = "Heart of the Dragon"
        private val SCROLLS = setOf("Scroll Up", "Scroll Down")

        // @keep Each opens a menu that asks before it changes the tree
        private val HEART_MENUS = setOf(HEART, "Dragon Profiles", "Reset Heart of the Dragon")
        private const val RESET = "Reset Heart of the Dragon?"
        private val TIER = Regex("Tier (\\d+)")
        private val APPLY = Regex("Apply Dragon Profile #(\\d+)\\?")
        private const val CONFIRM = "Confirm"
        private val APPLIED = Regex("Applied Heart of the Dragon Profile #(\\d+)\\.")
        const val COOLDOWN_MILLIS = 30_000L

        private val FOLDS = setOf(ContainerOpened::class.java, SlotChanged::class.java, SlotClicked::class.java, TabList::class.java)
    }
}
