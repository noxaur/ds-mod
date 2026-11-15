package io.github.noxaur.dsmod.core.container

/**
 * The items the server puts in its menus to move between them: an arrow or a barrier with one of a few names, and no
 * rarity, as its storage, collection, forge, crafting and auction menus show. It writes "Next page" in storage and
 * "Next Page" in the auction house, so both are here.
 */
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

        /** The button an item [id] with the custom [name] is, or null when it is anything else. */
        fun of(id: String, name: String?): MenuButton? = when (id) {
            "minecraft:arrow" -> ARROWS[name]
            "minecraft:barrier" -> CLOSE.takeIf { name == "Close" }
            else -> null
        }
    }
}
