package io.github.noxaur.dsmod.core.heart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class Powder {
    @SerialName("ender")
    ENDER,

    @SerialName("void")
    VOID,
}

@Serializable
data class Perk(
    val name: String,
    val tier: Int,
    val column: Int,
    val powder: Powder,
    val max: Int,
    val ability: Boolean = false,
    val cost: List<Long> = listOf(0),
    val value: List<Double>,
) {
    fun cost(from: Int, to: Int): Long {
        var sum = 0L
        for (level in from + 1..to) sum += cost[level - 1]
        return sum
    }
}

/** @keep Every perk, from dragsim.co/wiki/reference/heart (Nov 11, 2026), which the wiki generates from the server's
 * source. Each perk's tier, column, maximum and level 1 match a capture of /hotd from Nov 23, 2026 */
object Perks {
    val all: List<Perk> by lazy {
        val stream = checkNotNull(Perks::class.java.getResourceAsStream(TABLE)) { "The jar has no $TABLE" }
        val text = stream.use { it.readBytes().decodeToString() }
        Json.decodeFromString(ListSerializer(Perk.serializer()), text).sortedWith(compareBy({ it.tier }, { it.column }))
    }

    val byName: Map<String, Perk> by lazy { all.associateBy { it.name } }

    private const val TABLE = "/assets/dsmod/heart/perks.json"
}
