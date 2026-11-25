package io.github.noxaur.dragen.core.container

import kotlinx.serialization.Serializable

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
