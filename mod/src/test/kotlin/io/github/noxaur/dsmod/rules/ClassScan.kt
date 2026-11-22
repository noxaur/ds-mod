package io.github.noxaur.dsmod.rules

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.assertEquals

fun placesWhere(classes: List<ClassNode>, matches: (AbstractInsnNode) -> Boolean): List<String> = classes.flatMap { node ->
    node.methods.flatMap { method ->
        var line = 0
        method.instructions.mapNotNull { insn ->
            if (insn is LineNumberNode) line = insn.line
            if (matches(insn)) "${node.name.substringBeforeLast('/')}/${node.sourceFile}:$line" else null
        }
    }
}.distinct()

/** @keep A method reference such as `Component::literal` is a handle among an invokedynamic's bootstrap arguments. */
fun handlesOf(insn: AbstractInsnNode): List<Handle> = (insn as? InvokeDynamicInsnNode)?.bsmArgs.orEmpty().filterIsInstance<Handle>()

fun assertNoPlaces(found: List<String>, fix: String) = assertEquals(emptyList(), found, fix + "\n" + found.joinToString("\n") + "\n")

fun edgeClass(name: String, line: Int, vararg insns: AbstractInsnNode) = ClassNode().apply {
    this.name = "io/github/noxaur/dsmod/edge/$name"
    sourceFile = "$name.kt"
    methods.add(
        MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
            instructions.add(LineNumberNode(line, LabelNode()))
            insns.forEach(instructions::add)
            instructions.add(InsnNode(Opcodes.RETURN))
        },
    )
}

fun lambdaReference(name: String, desc: String, erased: String, target: Handle, instantiated: String) = InvokeDynamicInsnNode(
    name,
    desc,
    Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;" +
            "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false,
    ),
    Type.getMethodType(erased),
    target,
    Type.getMethodType(instantiated),
)
