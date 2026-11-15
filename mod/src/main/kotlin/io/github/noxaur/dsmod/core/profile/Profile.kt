package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

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
data class Floor(val id: String, val completions: Long, val bestScore: Long? = null) {
    /** Master floors are listed apart from the rest, as the game lists them. */
    val master get() = id.startsWith("M")
}

/**
 * The player's Catacombs: the class they picked, and every class on the Catacombs table. The Catacombs level itself
 * is among the skills. An older backend sends none, which reads as no dungeons.
 */
@Serializable
data class Dungeons(
    val selectedClass: String? = null,
    val secrets: Long = 0,
    val completions: Long = 0,
    val classes: List<Progress> = emptyList(),
    val floors: List<Floor> = emptyList(),
) {
    /** The class picked, with its level, such as the one a run starts with. */
    val selected get() = classes.firstOrNull { it.name == selectedClass }
}

/** A grid the profile has, listed in the order its tabs go. [hidden] when the player hid their inventory. */
@Serializable
data class ContainerSummary(val id: String, val title: Line, val columns: Int, val slots: Int, val hidden: Boolean) {
    /** The kind of grid, such as `storage` for `storage:3`, which one tab holds every page of. */
    val kind get() = id.substringBefore(':')
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
    val equipment: Equipment = Equipment(),
    val dungeons: Dungeons = Dungeons(),
    val containers: List<ContainerSummary> = emptyList(),
) {
    /** The profile this answer is, as the picker lists it. */
    val choice get() = profiles.firstOrNull { it.id == profileId }

    /** Whether the player hid their inventory, which hides every container and what they wear. */
    val hidden get() = containers.isNotEmpty() && containers.all { it.hidden }

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
