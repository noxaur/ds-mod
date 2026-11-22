package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ForbiddenText(config: Config) : Rule(config, "A literal or a name whose source text matches `pattern`, which the shared layer named in `reason` owns.") {
    private val pattern: Regex by config("") { Regex(it) }
    private val reason: String by config("")

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        check(expression, expression.text)
    }

    override fun visitConstantExpression(expression: KtConstantExpression) {
        super.visitConstantExpression(expression)
        check(expression, expression.text)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        check(expression, expression.getReferencedName())
    }

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
        super.visitNamedDeclaration(declaration)
        declaration.nameIdentifier?.let { check(it, it.text) }
    }

    private fun check(element: PsiElement, text: String) {
        if (pattern.pattern.isNotEmpty() && pattern.containsMatchIn(text)) report(Finding(Entity.from(element), reason))
    }
}
