package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

class ItemJsonByHand(config: Config) : Rule(config, "A safe cast to `JsonObject`, or an item's `data` read by its key, which `Item` already reads.") {
    private val reason: String by config("")

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        super.visitBinaryWithTypeRHSExpression(expression)
        if (expression.operationReference.getReferencedNameElementType() == KtTokens.AS_SAFE && expression.right?.text == "JsonObject") {
            report(Finding(Entity.from(expression), reason))
        }
    }

    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
        val array = expression.arrayExpression ?: return
        val name = (array as? KtDotQualifiedExpression)?.selectorExpression?.text ?: array.text
        if (name == "data" && expression.indexExpressions.singleOrNull()?.text in setOf("\"item_data\"", "\"extra\"")) {
            report(Finding(Entity.from(expression), reason))
        }
    }
}
