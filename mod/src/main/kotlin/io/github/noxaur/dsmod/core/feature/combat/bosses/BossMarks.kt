package io.github.noxaur.dsmod.core.feature.combat.bosses

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.BossKind
import io.github.noxaur.dsmod.core.world.Mark

class BossMarks(private val feature: Feature, private val claim: SlayerClaim, key: String, verb: String, private val mark: (Boss) -> Mark) {
    val dragons = feature.switch("${key}Dragons", "Dragons", "$verb the End's dragons.", default = true)
    val protector = feature.switch("${key}Protector", "Endstone Protector", "$verb the Endstone Protector.", default = true)
    val yourSlayer = feature.switch("${key}YourSlayer", "Your Slayer Boss", "$verb the slayer boss you spawned.", default = true)
    val otherSlayers = feature.switch("${key}OtherSlayers", "Other Slayer Bosses", "$verb slayer bosses other players spawned.", default = false)

    init {
        feature.readEntities(*Boss.FILTERS.toTypedArray())
        claim.follow(feature)
    }

    fun marks(): List<Mark> = feature.entities.mapNotNull(Boss::parse).filter { boss ->
        when (boss.kind) {
            BossKind.DRAGON -> dragons.value
            BossKind.PROTECTOR -> protector.value
            BossKind.SLAYER -> if (claim.owns(feature, boss)) yourSlayer.value else otherSlayers.value
        }
    }.map(mark)
}
