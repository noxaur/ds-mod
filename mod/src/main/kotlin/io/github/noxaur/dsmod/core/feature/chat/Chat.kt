package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.chat.copychat.CopyChat
import io.github.noxaur.dsmod.core.feature.chat.hidecooldownmessages.HideCooldownMessages
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.chat() = section("Chat", Role.WARNING) {
    group(null) {
        feature(HideCooldownMessages(c))
        feature(CopyChat(c))
    }
}
