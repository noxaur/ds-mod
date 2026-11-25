package io.github.noxaur.dragen.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CHAT = "net.minecraft.network.chat."

private val textLayer = listOf("core.text.", "core.ui.Theme", "edge.Chat", "core.capture.PlayerNames", "core.capture.Redactor")
    .map { "io.github.noxaur.dragen.$it" }

private val styled = setOf("literal", "translatable", "translatableWithFallback", "keybind", "nbt", "score", "selector", "nullToEmpty")
private val looks = setOf(
    "withColor", "withShadowColor", "withBold", "withItalic", "withUnderlined", "withStrikethrough", "withObfuscated", "withFont",
    "applyFormat", "applyFormats", "applyLegacyFormat",
)
private val colors = setOf("fromRgb", "fromLegacyFormat", "parseColor")

private val outsideTextLayer = describe<JavaClass>("outside the text layer") { type ->
    val top = type.name.substringBefore('$').removeSuffix("Kt")
    textLayer.none { if (it.endsWith('.')) top.startsWith(it) else top == it }
}

private val stylesText = describe<JavaAccess<*>>("gives text a color or a style") { access ->
    val target = access.target
    when (target.owner.name) {
        "net.minecraft.ChatFormatting" -> true
        "${CHAT}Component" -> target.name in styled
        "${CHAT}MutableComponent" -> target.name == "withColor"
        "${CHAT}Style" -> target.name in looks
        "${CHAT}TextColor" -> target.name in colors
        else -> false
    }
}

private fun textLayer(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().accessTargetWhere(stylesText).because(
    "the text layer colors text. Say what the text means: build it with `text { body(..); value(..) }` from core.text and " +
        "print it with `Chat.send`. If no role fits, add one to `Role`",
)

private class Literal { fun run() = Component.literal("Sold 5 scales!") }
private class Colored { fun run() = Component.empty().withColor(0) }
private class Bold { fun run() = Style.EMPTY.withBold(true) }
private class Rgb { fun run() = TextColor.fromRgb(0) }
private class Format { fun run() = ChatFormatting.GOLD }
private class Reference { fun run(): (String) -> Component = Component::literal }
private class NoText { fun run() = Component.empty() }
private class PlainStyle { fun run() = Style.EMPTY }

class TextLayerTest {
    @Test
    fun `only the text layer colors or styles text`() {
        textLayer(outsideTextLayer).check(releaseClasses)
    }

    @Test
    fun `the rule catches a color however the code spells it`() {
        val styling = flags(::textLayer, Literal::class.java, Colored::class.java, Bold::class.java, Rgb::class.java, Format::class.java, Reference::class.java, JavaLiteralReference::class.java, NoText::class.java, PlainStyle::class.java)
        assertEquals(listOf(Literal::class, Colored::class, Bold::class, Rgb::class, Format::class, Reference::class, JavaLiteralReference::class).map { it.java }, styling)
    }
}
