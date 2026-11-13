package io.github.noxaur.dsmod.core.config

import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

class DsModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ds-mod")
}
