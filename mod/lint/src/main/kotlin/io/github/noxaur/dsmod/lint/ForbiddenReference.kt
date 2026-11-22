package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ForbiddenReference(config: Config) :
    Rule(config, "A reference to a class or a member in `symbols`, which the shared layer named in `reason` owns."),
    RequiresAnalysisApi {
    private val symbols: List<String> by config(emptyList())
    private val reason: String by config("")

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        if (expression !is KtNameReferenceExpression || symbols.isEmpty()) return
        if (expression.getStrictParentOfType<KtImportDirective>() != null || expression.getStrictParentOfType<KtPackageDirective>() != null) return
        val name = analyze(expression) {
            when (val symbol = expression.mainReference.resolveToSymbol()) {
                is KaClassLikeSymbol -> symbol.classId?.asFqNameString()
                is KaCallableSymbol -> symbol.callableId?.asSingleFqName()?.asString()
                else -> null
            }
        } ?: return
        if (symbols.any { name == it || name.startsWith("$it.") }) report(Finding(Entity.from(expression), reason))
    }
}
