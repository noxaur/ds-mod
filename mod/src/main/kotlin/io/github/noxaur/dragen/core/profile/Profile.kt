package io.github.noxaur.dragen.core.profile

import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.ServerText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object LineSerializer : KSerializer<ServerText> {
    private val spans = ListSerializer(ServerSpan.serializer())
    override val descriptor = spans.descriptor

    override fun serialize(encoder: Encoder, value: ServerText) = encoder.encodeSerializableValue(spans, value.spans)

    override fun deserialize(decoder: Decoder) = ServerText(decoder.decodeSerializableValue(spans))
}

private typealias Line = @Serializable(with = LineSerializer::class) ServerText

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
    val facts: JsonObject? = null,
    val customData: JsonObject? = null,
)

@Serializable
data class Named(val id: String, val name: String)

@Serializable
data class StatValue(val id: String, val name: String, val color: String, val value: Double) {
    val rgb get() = color.removePrefix("#").toIntOrNull(16)
}

@Serializable
data class StatSheet(
    val base: List<StatValue> = emptyList(),
    val additive: List<StatValue> = emptyList(),
    val multiplicative: List<StatValue> = emptyList(),
    val increaseMax: List<StatValue> = emptyList(),
)

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

@Serializable
data class Floor(val id: String, val name: String = id, val completions: Long, val bestScore: Long? = null) {
    val master get() = id.startsWith("M")
}

@Serializable
data class Amount(val id: String, val name: String, val amount: Double)

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

    // @keep The server sends only the classes with XP, so a class never played is at level 0.
    val allClasses: List<Progress>
        get() {
            val cap = classes.firstOrNull()?.maxLevel ?: CLASS_CAP
            val known = CLASSES.map { id -> classes.firstOrNull { it.id.equals(id, true) } ?: Progress(id, id.lowercase().replaceFirstChar(Char::uppercase), 0, cap, 0.0, 0.0, null) }
            return known + classes.filter { c -> CLASSES.none { it.equals(c.id, true) } }
        }

    fun picked(progress: Progress) = selectedClass?.id.equals(progress.id, ignoreCase = true)

    private companion object {
        val CLASSES = listOf("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK")
        const val CLASS_CAP = 50
    }
}

/** @keep The Heart of the Dragon. `core`, `ender`, `void`, `resets` and `tokens` keep the server's names, as their meaning is a guess. */
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
    val xpIntoLevel: Double = 0.0,
    val xpForNextLevel: Double? = null,
    val category: String? = null,
    val perks: List<StatValue> = emptyList(),
    val description: List<Line> = emptyList(),
    val item: DrawnItem,
)

@Serializable
data class ForgeSlot(
    val slot: Int,
    val recipe: String,
    val name: String,
    val startedAt: Long,
    val durationMs: Long,
    val finishesAt: Long,
    val stage: String,
)

@Serializable
data class Meter(val id: String, val name: String, val progress: Double)

@Serializable
data class ContainerSummary(val id: String, val title: Line, val columns: Int, val slots: Int, val hidden: Boolean) {
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
    val profileName: Line = ServerText(emptyList()),
    val profileMode: String = "NORMAL",
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
    val choice get() = profiles.firstOrNull { it.id == profileId } ?: ProfileChoice(profileId, profileName, profileMode, true)

    val choices get() = if (profiles.any { it.id == profileId }) profiles else profiles + choice

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): Profile = json.decodeFromString(serializer(), body)

        fun parseContainer(body: String): Container = json.decodeFromString(Container.serializer(), body)

        fun parseContainers(body: String): List<Container> = json.decodeFromString(ListSerializer(Container.serializer()), body)

        // @keep dragsim.co looks players up by their Minecraft name.
        private val NAME = Regex("[A-Za-z0-9_]{1,16}")

        fun validName(name: String) = NAME.matches(name)

        // @keep `/pv <vault>` is the server's own command, which opens one of the player's vaults.
        fun vault(argument: String) = argument.length in 1..2 && argument.all { it in '0'..'9' }
    }
}
