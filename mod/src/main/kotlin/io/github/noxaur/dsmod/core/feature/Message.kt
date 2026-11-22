package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.text.Text

sealed interface Message {
    val feature: String
    val detail: Text
}

data class Notice(override val feature: String, override val detail: Text) : Message

data class Alert(override val feature: String, val title: Text, override val detail: Text) : Message
