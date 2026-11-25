package io.github.noxaur.dragen.lint

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class DragenRules : RuleSetProvider {
    override val ruleSetId = RuleSetId("dragen")

    override fun instance() = RuleSet(
        ruleSetId,
        listOf(::ForbiddenText, ::ForbiddenReference, ::ForbiddenCallInside, ::ForbiddenConstant, ::ContainerByReader, ::PartByItself, ::ItemJsonByHand),
    )
}
