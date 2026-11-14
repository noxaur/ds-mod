package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.text.Text

/** Something a feature tells the player once. [Game] collects them, and the edge delivers them after the tick. */
sealed interface Message {
    /** The title of the feature that said it, so the player can tell which switch turns it off. */
    val feature: String
    val detail: Text
}

/** A line in chat: the feature's name, then [detail]. */
data class Notice(override val feature: String, override val detail: Text) : Message

/** [title] large in the middle of the screen with a sound, and [detail] in chat as a [Notice] has it. */
data class Alert(override val feature: String, val title: Text, override val detail: Text) : Message
