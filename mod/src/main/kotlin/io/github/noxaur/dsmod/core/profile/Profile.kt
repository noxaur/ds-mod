package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * A player's profile as the backend's `profiles` routes answer it. The backend owns the contract in
 * `backend/src/profiles/model.ts`, and may add a field, so an unknown key is ignored.
 */

/** A line of styled text, which the contract sends as a bare list of spans. */
internal object LineSerializer : KSerializer<ServerText> {
    private val spans = ListSerializer(ServerSpan.serializer())
    override val descriptor = spans.descriptor

    override fun serialize(encoder: Encoder, value: ServerText) = encoder.encodeSerializableValue(spans, value.spans)

    override fun deserialize(decoder: Decoder) = ServerText(decoder.decodeSerializableValue(spans))
}

private typealias Line = @Serializable(with = LineSerializer::class) ServerText

/** An item as the game would draw it in a slot, with the tooltip the backend rebuilt for it. */
@Serializable
data class DrawnItem(
    val entry: String? = null,
    val material: String,
    val itemModel: String? = null,
    val count: Int = 1,
    val name: Line,
    val lore: List<Line> = emptyList(),
    val rarity: String? = null,
    val glint: Boolean = false,
    val skullTexture: String? = null,
    val color: Int? = null,
    /** The data behind the tooltip, such as the reforge, stars and stat sheet, for a panel or a search of the mod's own. */
    val facts: JsonObject? = null,
    /** The item's data as a real item of the game carries it in `custom_data`, for the stack, so other mods read it too. */
    val customData: JsonObject? = null,
)

/** Something the server names by an id, with the name the backend gives it. */
@Serializable
data class Named(val id: String, val name: String)

/** One stat number, named and colored as the game shows the stat. */
@Serializable
data class StatValue(val id: String, val name: String, val color: String, val value: Double) {
    /** [color] as an RGB number, or null when it is not one, which the screen draws muted. */
    val rgb get() = color.removePrefix("#").toIntOrNull(16)
}

/** The player's stats in the four groups the server keeps them in. */
@Serializable
data class StatSheet(
    val base: List<StatValue> = emptyList(),
    val additive: List<StatValue> = emptyList(),
    val multiplicative: List<StatValue> = emptyList(),
    val increaseMax: List<StatValue> = emptyList(),
)

/**
 * A skill or a slayer. [xpForNextLevel] is what the whole current level takes, and is null at the cap or when the
 * backend does not know the table.
 */
@Serializable
data class Progress(
    val id: String,
    val name: String,
    val level: Int,
    val maxLevel: Int,
    val totalXp: Double,
    val xpIntoLevel: Double,
    val xpForNextLevel: Double? = null,
) {
    val maxed get() = level >= maxLevel

    /** How full the level's bar is: full at the cap, empty when the next level's size is unknown. */
    val fraction: Float
        get() {
            val next = xpForNextLevel
            return when {
                maxed -> 1f
                next == null || next <= 0 -> 0f
                else -> (xpIntoLevel / next).toFloat().coerceIn(0f, 1f)
            }
        }
}

@Serializable
data class Stat(val id: String, val name: String, val value: Line)

@Serializable
data class Kills(val id: String, val name: String, val kills: Long)

@Serializable
data class Rank(val id: String, val prefix: Line)

@Serializable
data class Player(val uuid: String, val name: String, val rank: Rank)

@Serializable
data class ProfileChoice(val id: String, val name: Line, val mode: String, val selected: Boolean)

@Serializable
data class Equipment(
    val helmet: DrawnItem? = null,
    val chestplate: DrawnItem? = null,
    val leggings: DrawnItem? = null,
    val boots: DrawnItem? = null,
    val held: DrawnItem? = null,
)

/** One floor of the Catacombs, such as `F7` or `M3`: the runs finished, and the best score, null when none is known. */
@Serializable
data class Floor(val id: String, val name: String = id, val completions: Long, val bestScore: Long? = null) {
    /** Master floors are listed apart from the rest, as the game lists them. */
    val master get() = id.startsWith("M")
}

@Serializable
data class Amount(val id: String, val name: String, val amount: Double)

/**
 * The player's Catacombs: the class they picked, the classes they played on the Catacombs table, each floor's runs,
 * and their essence. The Catacombs level itself is among the skills.
 */
@Serializable
data class Dungeons(
    val selectedClass: Named? = null,
    val secrets: Double = 0.0,
    val zombieKills: Double = 0.0,
    val skeletorKills: Double = 0.0,
    val classes: List<Progress> = emptyList(),
    val floors: List<Floor> = emptyList(),
    val essence: List<Amount> = emptyList(),
) {
    val completions get() = floors.sumOf { it.completions }

    /**
     * All five classes in the game's order, at level 0 for one never played, since the server sends only the ones
     * with XP. A class the mod does not know comes after them.
     */
    val allClasses: List<Progress>
        get() {
            val cap = classes.firstOrNull()?.maxLevel ?: CLASS_CAP
            val known = CLASSES.map { id -> classes.firstOrNull { it.id.equals(id, true) } ?: Progress(id, id.lowercase().replaceFirstChar(Char::uppercase), 0, cap, 0.0, 0.0, null) }
            return known + classes.filter { c -> CLASSES.none { it.equals(c.id, true) } }
        }

    /** Whether [progress] is the class the player picked. */
    fun picked(progress: Progress) = selectedClass?.id.equals(progress.id, ignoreCase = true)

    private companion object {
        val CLASSES = listOf("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK")
        const val CLASS_CAP = 50
    }
}

/** The Heart of the Dragon. `core`, `ender`, `void`, `resets` and `tokens` keep the server's names, as their meaning is a guess. */
@Serializable
data class Hotd(
    val tier: Double = 0.0,
    val exp: Double = 0.0,
    val core: Double = 0.0,
    val ender: Double = 0.0,
    val void: Double = 0.0,
    val resets: Double = 0.0,
    val ability: Named? = null,
    val tokens: Double = 0.0,
    val nodes: List<Node> = emptyList(),
) {
    @Serializable
    data class Node(val id: String, val name: String, val level: Double)
}

/** A pet. [level] is null while no pet XP table is known, so [totalXp] is what to show. [item] draws it in a grid. */
@Serializable
data class Pet(
    val type: String,
    val name: String,
    val rarity: String,
    val totalXp: Double = 0.0,
    val xpCurve: Double = 0.0,
    val candiesUsed: Int = 0,
    val active: Boolean = false,
    val heldItem: Named? = null,
    val skinOverride: Named? = null,
    val level: Int? = null,
    val maxLevel: Int? = null,
    val category: String? = null,
    val perks: List<StatValue> = emptyList(),
    val description: List<Line> = emptyList(),
    val item: DrawnItem,
)

/** One slot of the forge. Times are epoch milliseconds. */
@Serializable
data class ForgeSlot(
    val slot: Int,
    val recipe: String,
    val name: String,
    val startedAt: Long,
    val durationMs: Long,
    val finishesAt: Long,
    val stage: String,
) {
    /** How far along it is at [now], from 0 to 1. */
    fun done(now: Long) = if (durationMs <= 0) 1f else ((now - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
}

@Serializable
data class Meter(val id: String, val name: String, val progress: Double)

/** A grid the profile has, listed in the order its tabs go. [hidden] when the player hid their inventory. */
@Serializable
data class ContainerSummary(val id: String, val title: Line, val columns: Int, val slots: Int, val hidden: Boolean) {
    /**
     * The kind of grid, such as `storage` for `storage:3`, which one tab holds every page of. Items the backend could
     * not place, `inventory:other`, are a last page of their kind's tab.
     */
    val kind get() = id.removeSuffix(":other").substringBefore(':')
}

@Serializable
data class Container(
    val id: String,
    val title: Line,
    val columns: Int,
    val slots: Int,
    val hidden: Boolean,
    val items: List<DrawnItem?> = emptyList(),
)

@Serializable
data class Profile(
    val snapshot: String,
    val fetchedAt: String,
    val stale: Boolean = false,
    val player: Player,
    val profiles: List<ProfileChoice> = emptyList(),
    val profileId: String,
    val skills: List<Progress> = emptyList(),
    val slayers: List<Progress> = emptyList(),
    val stats: List<Stat> = emptyList(),
    val topKills: List<Kills> = emptyList(),
    val savedAt: String? = null,
    val equippedWardrobeSlot: Int? = null,
    val playerStats: StatSheet = StatSheet(),
    val bestiary: List<Kills> = emptyList(),
    val collections: List<Amount> = emptyList(),
    val rngMeters: List<Meter> = emptyList(),
    val dungeons: Dungeons? = null,
    val hotd: Hotd? = null,
    val pets: List<Pet> = emptyList(),
    val forge: List<ForgeSlot> = emptyList(),
    val equipment: Equipment = Equipment(),
    val containers: List<ContainerSummary> = emptyList(),
) {
    /** The profile this answer is, as the picker lists it. */
    val choice get() = profiles.firstOrNull { it.id == profileId }

    /** Whether the player hid their inventory, which hides its containers and what they wear. Pets still show. */
    val hidden get() = containers.any { it.hidden }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): Profile = json.decodeFromString(serializer(), body)

        fun parseContainer(body: String): Container = json.decodeFromString(Container.serializer(), body)

        /** The names the backend takes: dragsim.co looks players up by their Minecraft name. */
        private val NAME = Regex("[A-Za-z0-9_]{1,16}")

        fun validName(name: String) = NAME.matches(name)

        /** Whether `/pv [argument]` is the server's own `/pv <vault>`, which opens one of the player's vaults. */
        fun vault(argument: String) = argument.length in 1..2 && argument.all { it in '0'..'9' }
    }
}
