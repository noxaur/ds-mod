package io.github.noxaur.dsmod.rules

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A place in the release build that sends input for the player, changes or refuses the player's own input, or calls a
 * class that sends. A gate is a class that does it itself, and its hash covers the whole class, so any edit to it needs
 * a new audit. A call comes from outside the gates into a gate's method that sends, so a new caller needs an audit too,
 * while an edit to an existing caller does not.
 */
sealed interface InputSite {
    val key: String
    val line: String

    data class Gate(val name: String, val hash: String, val does: List<String>) : InputSite {
        override val key get() = "gate $name"
        override val line get() = "gate $name $hash"
    }

    data class Call(val from: String, val to: String) : InputSite {
        override val key get() = "call $from -> $to"
        override val line get() = key
    }
}

/** What one instruction does to the player's input. [acts] is false for what waits on the player, such as a refused click. */
private class Sink(val does: String, val acts: Boolean = true)

private const val MOD = "io/github/noxaur/dsmod/"
private const val MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"

/** What a call to [owner].[name] does to the player's input, or null when it does nothing to it. */
private fun called(owner: String, name: String, opcode: Int): Sink? {
    val what = "${owner.substringAfterLast('/').replace('$', '.')}.$name"
    val getter = Regex("^(get|is|has|can)[A-Z]").containsMatchIn(name)
    return when {
        owner == "net/minecraft/client/multiplayer/MultiPlayerGameMode" && !getter -> Sink("acts through $what")
        owner in setOf(
            "net/minecraft/client/multiplayer/ClientPacketListener",
            "net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl",
            "net/minecraft/network/Connection",
        ) && name.startsWith("send") -> Sink("sends through $what")
        owner == "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking" && name.startsWith("send") -> Sink("sends through $what")
        owner == "net/minecraft/client/KeyMapping" && name in setOf("click", "set", "setDown", "releaseAll", "resetToggleKeys") ->
            Sink("presses a key through $what")
        owner in setOf(
            "net/minecraft/client/player/LocalPlayer",
            "net/minecraft/client/player/AbstractClientPlayer",
            "net/minecraft/world/entity/player/Player",
            "net/minecraft/world/entity/LivingEntity",
            "net/minecraft/world/entity/Entity",
            "net/minecraft/world/entity/player/Inventory",
        ) && (name.startsWith("set") || name in setOf(
            "drop", "swing", "closeContainer", "startUsingItem", "stopUsingItem", "releaseUsingItem", "jumpFromGround",
            "moveTo", "absMoveTo", "snapTo", "turn", "lookAt", "startRiding", "stopRiding",
        )) -> Sink("changes the player through $what")
        owner.startsWith("net/minecraft/client/player/") && owner.endsWith("Input") -> Sink("changes movement input through $what")
        owner in setOf("net/minecraft/client/gui/screens/Screen", "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen") &&
            opcode != Opcodes.INVOKESPECIAL && name in setOf("handleComponentClicked", "handleClickEvent", "slotClicked", "onClose", "mouseClicked", "keyPressed") ->
            Sink("plays a click or key on a screen through $what")
        owner == "net/minecraft/client/gui/screens/ChatScreen" && name == "handleChatInput" -> Sink("sends chat through $what")
        owner == "net/minecraft/client/Minecraft" && name in setOf("startAttack", "startUseItem", "continueAttack", "pickBlock") -> Sink("acts through $what")
        owner == "org/lwjgl/glfw/GLFW" && name.startsWith("glfwSet") -> Sink("drives the window's input through $what")
        owner.startsWith("net/fabricmc/fabric/api/client/screen/v1/Screen") && owner.endsWith("Events") && name.startsWith("allow") ->
            Sink("can refuse the player's input through $what", acts = false)
        owner.startsWith("net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents") -> Sink("can refuse or change sent chat through $what")
        owner.startsWith("net/fabricmc/fabric/api/event/player/") || owner == "net/fabricmc/fabric/api/event/client/player/ClientPreAttackCallback" ->
            Sink("can refuse the player's actions through $what", acts = false)
        // Reflection that runs or writes what it found, which the scan cannot follow. Looking a method up runs nothing.
        owner == "java/lang/reflect/Method" && name == "invoke" || owner == "java/lang/reflect/Field" && name.startsWith("set") ||
            owner == "java/lang/reflect/Constructor" && name == "newInstance" || owner == "java/lang/invoke/MethodHandle" && name.startsWith("invoke") ||
            owner == "java/lang/Class" && name == "forName" || owner == "java/util/ServiceLoader" || owner.endsWith("misc/Unsafe") ||
            owner.startsWith("kotlin/reflect/") && name.startsWith("call") -> Sink("runs code by reflection through $what")
        else -> null
    }
}

/** What constructing [type] does to the player's input, or null when it does nothing to it. */
private fun built(type: String): Sink? = when {
    type.startsWith("net/minecraft/network/protocol/") && type.substringAfterLast('/').startsWith("Serverbound") ->
        Sink("builds the packet ${type.substringAfterLast('/')}")
    type == "net/minecraft/network/chat/ClickEvent\$RunCommand" -> Sink("builds text that runs a command when the player clicks it", acts = false)
    else -> null
}

/** What [insn] does to the player's input, or null when it does nothing to it. */
private fun sink(insn: AbstractInsnNode): Sink? = when (insn) {
    is MethodInsnNode -> called(insn.owner, insn.name, insn.opcode)
    is InvokeDynamicInsnNode -> insn.bsmArgs.filterIsInstance<Handle>().firstNotNullOfOrNull { called(it.owner, it.name, 0) }
    is TypeInsnNode -> if (insn.opcode == Opcodes.NEW) built(insn.desc) else null
    is FieldInsnNode -> when {
        insn.opcode == Opcodes.PUTFIELD && called(insn.owner, "set", 0) != null -> Sink("writes ${insn.owner.substringAfterLast('/')}.${insn.name}")
        // A Fabric event such as `UseItemCallback.EVENT` is a field.
        insn.opcode == Opcodes.GETSTATIC && insn.owner.startsWith("net/fabricmc/") -> called(insn.owner, insn.name, insn.opcode)
        else -> null
    }
    else -> null
}

/** The mod methods [method] calls or takes a reference to, as owner and name. */
private fun targets(method: MethodNode) = method.instructions.asSequence().flatMap { insn ->
    when (insn) {
        is MethodInsnNode -> sequenceOf(insn.owner to insn.name)
        is InvokeDynamicInsnNode -> insn.bsmArgs.asSequence().filterIsInstance<Handle>().map { it.owner to it.name }
        is LdcInsnNode -> (insn.cst as? Handle)?.let { sequenceOf(it.owner to it.name) }.orEmpty()
        else -> emptySequence()
    }
}.filter { it.first.startsWith(MOD) }

private fun display(internal: String) = internal.removePrefix(MOD).replace('/', '.').replace('$', '.')

/** The method a Kotlin or Java lambda was written in, so a lambda's calls count as its enclosing method's. */
private fun enclosing(method: String) = if (method.startsWith("lambda$")) method.split('$')[1] else method.substringBefore('$')

private fun hash(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }.take(12)

/** Every input site in [classes], the bytes of the compiled release classes, and in the access widener's [widener] lines. */
fun inputSites(classes: List<ByteArray>, widener: List<String> = emptyList()): List<InputSite> {
    val nodes = classes.associate { bytes -> ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_FRAMES) }.let { it.name to (it to bytes) } }
    val groups = nodes.values.groupBy { (node, _) -> node.name.substringBefore('$') }

    val gates = mutableListOf<InputSite.Gate>()
    // The methods of each gate that send, directly or through the gate's own methods, as owner and name.
    val sending = mutableSetOf<Pair<String, String>>()
    for ((group, members) in groups) {
        val classNodes = members.map { it.first }
        val mixin = classNodes.any { node -> node.invisibleAnnotations.orEmpty().any { it.desc == MIXIN } }
        val does = (listOfNotNull("is a mixin, so it can change any of Minecraft's code".takeIf { mixin }) +
            classNodes.flatMap { node -> node.methods.flatMap { method -> method.instructions.mapNotNull(::sink).map { it.does } } }).distinct().sorted()
        if (does.isEmpty()) continue
        val digest = MessageDigest.getInstance("SHA-256")
        // Without debug attributes, so moving a line or renaming a local keeps the audit.
        members.sortedBy { it.first.name }.forEach { (_, bytes) -> digest.update(ClassWriter(0).also { ClassReader(bytes).accept(it, ClassReader.SKIP_DEBUG) }.toByteArray()) }
        gates += InputSite.Gate(display(group), hash(digest), does)

        val methods = classNodes.flatMap { node -> node.methods.map { node.name to it } }
        val own = methods.filter { (_, method) ->
            method.instructions.any { sink(it)?.acts == true } ||
                // An invoker runs a private Minecraft method, and an accessor that returns nothing writes a private field.
                method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Invoker;") } ||
                method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Accessor;") } && Type.getReturnType(method.desc) == Type.VOID_TYPE
        }.map { (owner, method) -> owner to method.name }.toMutableSet()
        do {
            val more = methods.filter { (owner, method) -> (owner to method.name) !in own && targets(method).any { it in own } }
            own += more.map { (owner, method) -> owner to method.name }
        } while (more.isNotEmpty())
        sending += own
    }
    val gated = gates.map { it.name }.toSet()

    val calls = groups.filterKeys { display(it) !in gated }.flatMap { (group, members) ->
        members.flatMap { (node, _) ->
            node.methods.flatMap { method ->
                targets(method).filter { it in sending }.filter { (owner, name) ->
                    nodes[owner]?.first?.methods?.none { it.name == name && it.access and Opcodes.ACC_PRIVATE != 0 } ?: true
                }.map { (owner, name) -> InputSite.Call("${display(group)}.${enclosing(method.name)}", "${display(owner)}.$name") }.toList()
            }
        }
    }

    val aw = widener.filter { it.isNotBlank() && !it.startsWith("accessWidener") && !it.startsWith("#") }
    val opened = aw.takeIf { it.isNotEmpty() }?.let { lines ->
        InputSite.Gate("aw/dsmod.accesswidener", hash(MessageDigest.getInstance("SHA-256").apply { update(lines.joinToString("\n").toByteArray()) }), listOf("opens Minecraft's private code"))
    }

    return (gates + listOfNotNull(opened) + calls).distinctBy { it.key }.sortedBy { it.key }
}

/** The audited sites in `input-sites.txt`, keyed like [InputSite.key], each as [InputSite.line] would print it. */
private fun audited(lines: List<String>) = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.associate { line ->
    val words = line.split(Regex("\\s+"))
    when (words[0]) {
        "gate" -> "gate ${words[1]}" to "gate ${words[1]} ${words.getOrNull(2)}"
        "call" -> "call ${words[1]} -> ${words[3]}".let { it to it }
        else -> error("input-sites.txt: a line starts with `gate` or `call`: $line")
    }
}

class InputSitesTest {
    private val root = Path.of("")

    @Test
    fun `the user audited every input site`() {
        val classes = listOf("build/classes/kotlin/main", "build/classes/java/main").map(root::resolve).filter { it.exists() }.flatMap { dir ->
            Files.walk(dir).asSequence().filter { it.extension == "class" }.map { it.readBytes() }.toList()
        }
        assertTrue(classes.size > 100, "InputSitesTest found ${classes.size} release classes. Build the main source set first.")
        val found = inputSites(classes, root.resolve("src/main/aw/dsmod.accesswidener").readLines())
        val audit = audited(root.resolve("input-sites.txt").readLines())
        val unaudited = found.filter { audit[it.key] != it.line }
        val gone = audit.keys - found.map { it.key }.toSet()
        assertEquals(
            emptyList(),
            unaudited.map { it.key } + gone,
            "Each site below sends or refuses input for the player, or calls a class that sends. Only the user audits " +
                "them and writes them into mod/input-sites.txt. Stop, show the user this list, and wait for them.\n\n" +
                unaudited.joinToString("\n") { site ->
                    val changed = if (site.key in audit) "  (changed since the user's audit)" else ""
                    site.line + changed + ((site as? InputSite.Gate)?.does?.joinToString("") { "\n    $it" } ?: "")
                } + gone.joinToString("") { "\n$it  (gone, so its line can go)" } + "\n",
        )
    }

    @Test
    fun `the scan catches each kind of input site`() {
        fun example(name: String, vararg insns: AbstractInsnNode, mixin: Boolean = false): ByteArray {
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                this.name = "${MOD}example/$name"
                superName = "java/lang/Object"
                if (mixin) visitAnnotation(MIXIN, false)
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
                    insns.forEach(instructions::add)
                    instructions.add(InsnNode(Opcodes.RETURN))
                })
            }
            return ClassWriter(ClassWriter.COMPUTE_MAXS).also(node::accept).toByteArray()
        }
        fun call(owner: String, name: String) = MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, "()V")
        val sites = inputSites(
            listOf(
                example("Command", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand")),
                example("Click", call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "handleContainerInput")),
                example("Aim", InsnNode(Opcodes.ACONST_NULL), InsnNode(Opcodes.FCONST_0), FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/player/LocalPlayer", "yRot", "F")),
                example("Packet", TypeInsnNode(Opcodes.NEW, "net/minecraft/network/protocol/game/ServerboundSwingPacket"), InsnNode(Opcodes.POP)),
                example("Link", TypeInsnNode(Opcodes.NEW, "net/minecraft/network/chat/ClickEvent\$RunCommand"), InsnNode(Opcodes.POP)),
                example("Refuse", call("net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "allowMouseClick")),
                example("Use", FieldInsnNode(Opcodes.GETSTATIC, "net/fabricmc/fabric/api/event/player/UseItemCallback", "EVENT", "Lnet/fabricmc/fabric/api/event/Event;"), InsnNode(Opcodes.POP)),
                example("Reflect", call("java/lang/reflect/Method", "invoke")),
                example("Hook", mixin = true),
                example("Caller", call("${MOD}example/Command", "run")),
                example("Clicker", call("${MOD}example/Link", "run")),
                example("Reader", call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "getPlayerMode")),
            ),
        )
        assertEquals(
            listOf(
                "call example.Caller.run -> example.Command.run",
                "gate example.Aim", "gate example.Click", "gate example.Command", "gate example.Hook",
                "gate example.Link", "gate example.Packet", "gate example.Reflect", "gate example.Refuse", "gate example.Use",
            ),
            sites.map { it.key },
        )
    }
}
