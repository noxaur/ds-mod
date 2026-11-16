package io.github.noxaur.dsmod.rules

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CHAT = "net/minecraft/network/chat/"
private const val MOD = "io/github/noxaur/dsmod/"

/**
 * The classes that make colored text. `Text` describes it, `Chat` prints it and `Theme` owns the colors. `PlayerNames`
 * and `Redactor` only read the `§` codes of an older capture's raw text to strip them.
 */
private val textLayer = listOf("core/text/", "core/ui/Theme", "edge/Chat", "core/capture/PlayerNames", "core/capture/Redactor").map { MOD + it }

private val styled = setOf("literal", "translatable", "translatableWithFallback", "keybind", "nbt", "score", "selector", "nullToEmpty")
private val looks = setOf(
    "withColor", "withShadowColor", "withBold", "withItalic", "withUnderlined", "withStrikethrough", "withObfuscated", "withFont",
    "applyFormat", "applyFormats", "applyLegacyFormat",
)
private val colors =setOf("fromRgb", "fromLegacyFormat", "parseColor")

/** Whether [insn] gives text a color, a style or a `§` code, which only the text layer may do. */
private fun styling(insn: AbstractInsnNode) = when (insn) {
    is LdcInsnNode -> (insn.cst as? String)?.contains('§') == true
    is FieldInsnNode -> insn.owner == "net/minecraft/ChatFormatting"
    is MethodInsnNode -> when (insn.owner) {
        "net/minecraft/ChatFormatting" -> true
        "${CHAT}Component" -> insn.opcode == Opcodes.INVOKESTATIC && insn.name in styled
        "${CHAT}MutableComponent" -> insn.name == "withColor"
        "${CHAT}Style" -> insn.name in looks
        "${CHAT}TextColor" -> insn.name in colors
        else -> false
    }
    else -> false
}

/** Every place in [classes] outside the text layer that colors or styles text, as `file:line`. */
fun stylings(classes: List<ClassNode>): List<String> = classes.filter { node -> textLayer.none(node.name::startsWith) }.flatMap { node ->
    node.methods.flatMap { method ->
        var line = 0
        method.instructions.mapNotNull { insn ->
            if (insn is LineNumberNode) line = insn.line
            if (styling(insn)) "${node.name.substringBeforeLast('/')}/${node.sourceFile}:$line" else null
        }
    }
}.distinct()

class TextLayerTest {
    @Test
    fun `only the text layer colors or styles text`() {
        val found = stylings(releaseClasses())
        assertEquals(
            emptyList(),
            found,
            "Say what the text means and let the text layer color it: build it with `text { body(..); value(..) }` " +
                "from core.text and print it with `Chat.send`. If no role fits, add one to `Role`.\n" + found.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the scan catches a color however the code spells it`() {
        fun calling(name: String, insn: AbstractInsnNode) = ClassNode().apply {
            this.name = "${MOD}edge/$name"
            sourceFile = "$name.kt"
            methods.add(
                MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                    instructions.add(LineNumberNode(7, org.objectweb.asm.tree.LabelNode()))
                    instructions.add(insn)
                    instructions.add(InsnNode(Opcodes.RETURN))
                },
            )
        }
        val found = stylings(
            listOf(
                calling("Literal", MethodInsnNode(Opcodes.INVOKESTATIC, "${CHAT}Component", "literal", "(Ljava/lang/String;)L${CHAT}MutableComponent;")),
                calling("Color", MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${CHAT}MutableComponent", "withColor", "(I)L${CHAT}MutableComponent;")),
                calling("Style", MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${CHAT}Style", "withBold", "(Ljava/lang/Boolean;)L${CHAT}Style;")),
                calling("Rgb", MethodInsnNode(Opcodes.INVOKESTATIC, "${CHAT}TextColor", "fromRgb", "(I)L${CHAT}TextColor;")),
                calling("Format", FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/ChatFormatting", "GOLD", "Lnet/minecraft/ChatFormatting;")),
                calling("Escape", LdcInsnNode("§eSold 5 scales!")),
                calling("Empty", MethodInsnNode(Opcodes.INVOKESTATIC, "${CHAT}Component", "empty", "()L${CHAT}MutableComponent;")),
                calling("Read", FieldInsnNode(Opcodes.GETSTATIC, "${CHAT}Style", "EMPTY", "L${CHAT}Style;")),
            ),
        )
        assertEquals(listOf("Literal", "Color", "Style", "Rgb", "Format", "Escape").map { "${MOD}edge/$it.kt:7" }, found)
    }
}
