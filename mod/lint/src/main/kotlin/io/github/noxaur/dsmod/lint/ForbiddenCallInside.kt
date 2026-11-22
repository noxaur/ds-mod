package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class ForbiddenCallInside(config: Config) :
    Rule(config, "A call whose source text matches `inner`, inside the lambda of a call to one of `outer`.") {
    private val outer: List<String> by config(emptyList())
    private val inner: Regex by config("") { Regex(it) }
    private val reason: String by config("")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (inner.pattern.isEmpty() || expression.calleeExpression?.text !in outer) return
        expression.lambdaBodies().flatMap { it.collectDescendantsOfType<KtCallExpression>() }
            .filter { inner.containsMatchIn(it.text) }
            .forEach { report(Finding(Entity.from(it), reason)) }
    }
}
