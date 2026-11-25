package io.github.noxaur.dragen.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression

class ContainerByReader(config: Config) :
    Rule(config, "A handler of a container event that only sets a flag, which `SlotMarks` and `ContainerReader` already keep.") {
    private val reason: String by config("")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.handles(setOf("ContainerOpened", "SlotChanged", "WorldChanged"))) return
        val flag = expression.lambdaBodies().singleOrNull()?.statements?.singleOrNull() as? KtBinaryExpression ?: return
        if (flag.operationToken == KtTokens.EQ && flag.right?.text == "true") report(Finding(Entity.from(expression), reason))
    }
}
