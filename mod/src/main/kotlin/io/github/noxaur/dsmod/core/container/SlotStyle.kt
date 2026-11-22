package io.github.noxaur.dsmod.core.container

import kotlinx.serialization.Serializable

/**
 * The shape a rarity's color takes in the slot, each in that one color. Frame is strongest along the item area's
 * edge, Aura is a disc whose edge pixels are partly see-through so it reads as round, and Underglow is light pooling
 * under the item.
 */
enum class MarkStyle(private val label: String) {
    FRAME("Frame"),
    AURA("Aura"),
    UNDERGLOW("Underglow"),
    ;

    override fun toString() = label
}

@Serializable
data class SlotStyle(
    val mark: MarkStyle = MarkStyle.FRAME,
    val opacity: Int = 100,
    val sparkles: Boolean = false,
)
