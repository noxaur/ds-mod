package io.github.noxaur.dsmod.core.container

// @keep The server writes "Next page" in storage and "Next Page" in the auction house, so both are here
enum class MenuButton {
    BACK,
    CLOSE,
    NEXT,
    PREVIOUS,
    ;

    companion object {
        private val ARROWS = mapOf(
            "Go Back" to BACK,
            "Return" to BACK,
            "Next page" to NEXT,
            "Next Page" to NEXT,
            "Previous page" to PREVIOUS,
            "Previous Page" to PREVIOUS,
        )

        fun of(id: String, name: String?): MenuButton? = when (id) {
            "minecraft:arrow" -> ARROWS[name]
            "minecraft:barrier" -> CLOSE.takeIf { name == "Close" }
            else -> null
        }
    }
}
