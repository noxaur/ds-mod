package io.github.noxaur.dsmod.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class PartByItself(config: Config) :
    Rule(config, "A feature's handler of a container event that invalidates its `SlotMarks`, which hear that event for themselves.") {
    private val reason: String by config("")

    private var marks: Set<String> = emptySet()

    override fun preVisit(root: KtFile) {
        marks = root.collectDescendantsOfType<KtProperty>()
            .filter { (it.initializer as? KtCallExpression)?.calleeExpression?.text == "SlotMarks" }
            .mapNotNull { it.name }.toSet()
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (marks.isEmpty() || !expression.handles(CONTAINER_EVENTS)) return
        expression.lambdaBodies().flatMap { it.collectDescendantsOfType<KtDotQualifiedExpression>() }.forEach { access ->
            val receiver = access.receiverExpression
            val name = if (receiver is KtDotQualifiedExpression && receiver.receiverExpression is KtThisExpression) receiver.selectorExpression?.text else receiver.text
            val call = access.selectorExpression as? KtCallExpression ?: return@forEach
            if (name in marks && call.calleeExpression?.text?.startsWith("invalidate") == true) report(Finding(Entity.from(call), reason))
        }
    }

    private companion object {
        val CONTAINER_EVENTS = setOf("ContainerOpened", "SlotChanged", "WorldChanged")
    }
}
