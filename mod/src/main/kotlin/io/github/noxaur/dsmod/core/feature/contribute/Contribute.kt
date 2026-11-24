package io.github.noxaur.dsmod.core.feature.contribute

import io.github.noxaur.dsmod.core.config.Opens
import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.contribute.reportgamedata.ReportGameData
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.contribute() = section("Contribute", Role.RARE) {
    group(null) {
        action(Opens.REQUESTS, "Requests", "See what ds-mod's developers ask players to record.", id = "requests")
        feature(ReportGameData(c))
    }
}
