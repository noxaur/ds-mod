package io.github.noxaur.dsmod.lint

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression

internal fun KtCallExpression.lambdaBodies(): List<KtBlockExpression> =
    (lambdaArguments.mapNotNull { it.getLambdaExpression() } + valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression })
        .distinct().mapNotNull { it.bodyExpression }

internal fun KtCallExpression.handles(events: Set<String>) =
    calleeExpression?.text == "on" && typeArguments.singleOrNull()?.typeReference?.text?.trim() in events
