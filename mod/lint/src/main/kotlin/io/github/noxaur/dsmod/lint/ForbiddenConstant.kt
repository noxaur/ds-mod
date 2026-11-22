package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

class ForbiddenConstant(config: Config) : Rule(config, "A `const val` whose value is one of `values`, which the shared layer named in `reason` already names.") {
    private val values: List<String> by config(emptyList())
    private val reason: String by config("")

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.hasModifier(KtTokens.CONST_KEYWORD) && property.initializer?.text in values) {
            report(Finding(Entity.atName(property), reason))
        }
    }
}
