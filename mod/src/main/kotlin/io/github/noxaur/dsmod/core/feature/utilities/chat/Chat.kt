package io.github.noxaur.dsmod.core.feature.utilities.chat

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.utilities.chat.copychat.CopyChat
import io.github.noxaur.dsmod.core.feature.utilities.chat.profileviewer.ProfileViewer

internal fun Groups.chat() = group("Chat") {
    feature(CopyChat(c))
    feature(ProfileViewer(c))
}
