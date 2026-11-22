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
    Rule(config, "A feature's handler that calls into a part about an event the part already hears for itself.") {
    private val reason: String by config("")

    private class Part(val events: Set<String>, val forbidden: (KtCallExpression) -> Boolean)

    private val parts = mapOf(
        "ServerClock" to Part(setOf("ServerTime", "WorldChanged")) { it.calleeExpression?.text in setOf("clear", "record") },
        "ContainerReader" to Part(READER_EVENTS, ::invalidates),
        "SlotMarks" to Part(READER_EVENTS, ::invalidates),
    )

    private var owned: Map<String, Part> = emptyMap()

    override fun preVisit(root: KtFile) {
        owned = root.collectDescendantsOfType<KtProperty>().mapNotNull { property ->
            val part = (property.initializer as? KtCallExpression)?.calleeExpression?.text?.let(parts::get) ?: return@mapNotNull null
            property.name?.let { it to part }
        }.toMap()
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (owned.isEmpty()) return
        expression.lambdaBodies().flatMap { it.collectDescendantsOfType<KtDotQualifiedExpression>() }.forEach { access ->
            val receiver = access.receiverExpression
            val name = if (receiver is KtDotQualifiedExpression && receiver.receiverExpression is KtThisExpression) receiver.selectorExpression?.text else receiver.text
            val part = owned[name] ?: return@forEach
            val call = access.selectorExpression as? KtCallExpression ?: return@forEach
            if (expression.handles(part.events) && part.forbidden(call)) report(Finding(Entity.from(call), reason))
        }
    }

    private companion object {
        val READER_EVENTS = setOf("ContainerOpened", "SlotChanged", "WorldChanged")

        fun invalidates(call: KtCallExpression): Boolean {
            val name = call.calleeExpression?.text ?: return false
            return name.startsWith("invalidate") || name == "changed" && call.valueArgumentList?.arguments.orEmpty().isNotEmpty()
        }
    }
}
