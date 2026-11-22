package io.github.noxaur.dsmod.lint

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class DsModRules : RuleSetProvider {
    override val ruleSetId = RuleSetId("dsmod")

    override fun instance() = RuleSet(
        ruleSetId,
        listOf(::ForbiddenText, ::ForbiddenReference, ::ForbiddenCallInside, ::ForbiddenConstant, ::ContainerByReader, ::PartByItself, ::ItemJsonByHand),
    )
}
