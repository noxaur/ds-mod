package io.github.noxaur.dsmod.core.container

/** A ring around the slot, drawn over the fill and the floor. */
enum class Border(val label: String) {
    NONE("None"),
    FLAT("Flat"),
    SQUARE("Square"),
    ROUNDED("Rounded"),
    GILDED("Gilded"),
}

/** Color inside the slot, behind the item. */
enum class Fill(val label: String) {
    NONE("None"),
    GLOW("Glow"),
    WASH("Wash"),
    HALO("Halo"),
}

/** Something the item stands on, along the bottom of the slot. */
enum class Floor(val label: String) {
    NONE("None"),
    PEDESTAL("Pedestal"),
    SPARKS("Sparks"),
    RISE("Rise"),
}

/**
 * How a rarity mark looks, built from the rarity's one color. [gem] puts a small gem over the item's top-left
 * corner, where no stack count or durability bar sits. The defaults are the Gilded preset's.
 */
data class SlotStyle(
    val border: Border = Border.GILDED,
    val fill: Fill = Fill.GLOW,
    val floor: Floor = Floor.NONE,
    val gem: Boolean = false,
)

/**
 * A ready-made look the player picks in the settings, or [CUSTOM] for the one they built in the editor. They run
 * from loud to quiet: Showcase fills the slot, Gilded keeps its middle clear, and Minimal leaves the slot alone.
 */
enum class RarityPreset(private val label: String, val style: SlotStyle?) {
    SHOWCASE("Showcase", SlotStyle(Border.ROUNDED, Fill.HALO, Floor.PEDESTAL)),
    GILDED("Gilded", SlotStyle()),
    MINIMAL("Minimal", SlotStyle(Border.NONE, Fill.NONE, Floor.PEDESTAL, gem = true)),
    CUSTOM("Custom", null),
    ;

    override fun toString() = label
}

/** The entry [by] places on, wrapping at either end, for a stepper. */
fun <E : Enum<E>> E.step(by: Int, entries: List<E>): E = entries[Math.floorMod(ordinal + by, entries.size)]
