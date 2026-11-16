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
private fun calledOn(owner: String, name: String, opcode: Int): Sink? {
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
        owner == "net/minecraft/client/KeyMapping" && name == "consumeClick" -> Sink("takes the player's key presses through $what", acts = false)
        owner in setOf(
            "net/minecraft/client/player/LocalPlayer",
            "net/minecraft/client/player/AbstractClientPlayer",
            "net/minecraft/world/entity/player/Player",
            "net/minecraft/world/entity/LivingEntity",
            "net/minecraft/world/entity/Entity",
            "net/minecraft/world/entity/player/Inventory",
        ) && (name.startsWith("set") || name.startsWith("send") || name in setOf(
            "drop", "swing", "closeContainer", "startUsingItem", "stopUsingItem", "releaseUsingItem", "jumpFromGround",
            "moveTo", "absMoveTo", "snapTo", "turn", "lookAt", "startRiding", "stopRiding",
        )) -> Sink("changes the player through $what")
        owner.startsWith("net/minecraft/client/player/") && owner.endsWith("Input") -> Sink("changes movement input through $what")
        owner == "net/minecraft/client/gui/screens/Screen" && opcode != Opcodes.INVOKESPECIAL &&
            name in setOf("handleComponentClicked", "handleClickEvent", "mouseClicked", "keyPressed") ->
            Sink("plays a click or key on a screen through $what")
        // A container screen's close tells the server, and a plain screen's does not. A super call counts here, because
        // only a subclass of a container screen makes one, and forwarding to it is how that subclass would act.
        owner == "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen" &&
            name in setOf("slotClicked", "onClose", "mouseClicked", "keyPressed") ->
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
            owner == "java/lang/invoke/VarHandle" && Regex("^(set|compareAnd|weakCompare|getAnd)").containsMatchIn(name) ||
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

/**
 * Reads what instructions do to the player's input. A call is matched on its owner and on every class the owner
 * extends or implements, so a subclass such as `ToggleKeyMapping`, or one of the mod's own, is no way around it.
 */
private class Sinks(private val mod: Map<String, ClassNode>) {
    private val lineages = HashMap<String, List<String>>()

    fun lineage(owner: String): List<String> = lineages.getOrPut(owner) {
        val supers = mod[owner]?.let { listOfNotNull(it.superName) + it.interfaces } ?: Sinks::class.java.classLoader
            .getResourceAsStream("$owner.class")?.use { ClassReader(it) }?.let { listOfNotNull(it.superName) + it.interfaces }.orEmpty()
        listOf(owner) + supers.flatMap(::lineage)
    }

    /** The mod class that declares [name] with [desc] for a call on [owner], or null when only Minecraft or Java does. */
    fun declaring(owner: String, name: String, desc: String) =
        lineage(owner).firstOrNull { o -> mod[o]?.methods?.any { it.name == name && it.desc == desc } == true }

    // A call the mod's own code answers is scanned where that code is, and one it inherits is Minecraft's.
    fun called(owner: String, name: String, opcode: Int, desc: String? = null) =
        if (desc != null && declaring(owner, name, desc) != null) null
        else lineage(owner).firstNotNullOfOrNull { calledOn(it, name, opcode) }

    /** A method handle runs its method, and a constructor's handle builds its class. */
    private fun handle(handle: Handle) = if (handle.name == "<init>") built(handle.owner) else called(handle.owner, handle.name, 0, handle.desc)

    /** What [insn] does to the player's input, or null when it does nothing to it. */
    fun of(insn: AbstractInsnNode): Sink? = when (insn) {
        is MethodInsnNode -> called(insn.owner, insn.name, insn.opcode, insn.desc)
        is InvokeDynamicInsnNode -> insn.bsmArgs.filterIsInstance<Handle>().firstNotNullOfOrNull(::handle)
        is LdcInsnNode -> (insn.cst as? Handle)?.let(::handle)
        is TypeInsnNode -> if (insn.opcode == Opcodes.NEW) built(insn.desc) else null
        is FieldInsnNode -> when {
            // `this$0` and its kind are the compiler's own fields on a nested class.
            insn.opcode == Opcodes.PUTFIELD && !insn.name.contains('$') && called(insn.owner, "set", 0) != null -> Sink("writes ${insn.owner.substringAfterLast('/')}.${insn.name}")
            // A Fabric event such as `UseItemCallback.EVENT` is a field.
            insn.opcode == Opcodes.GETSTATIC && insn.owner.startsWith("net/fabricmc/") -> called(insn.owner, insn.name, insn.opcode)
            else -> null
        }
        else -> null
    }
}

/** A method by its owner, name and descriptor, so an overload is a method of its own. */
private data class MethodRef(val owner: String, val name: String, val desc: String)

/** The mod methods [method] calls or takes a reference to, constructors included. */
private fun targets(method: MethodNode) = method.instructions.asSequence().flatMap { insn ->
    when (insn) {
        is MethodInsnNode -> sequenceOf(MethodRef(insn.owner, insn.name, insn.desc))
        is InvokeDynamicInsnNode -> insn.bsmArgs.asSequence().filterIsInstance<Handle>().map { MethodRef(it.owner, it.name, it.desc) }
        is LdcInsnNode -> (insn.cst as? Handle)?.let { sequenceOf(MethodRef(it.owner, it.name, it.desc)) }.orEmpty()
        else -> emptySequence()
    }
}.filter { it.owner.startsWith(MOD) }

private fun display(internal: String) = internal.removePrefix(MOD).replace('/', '.').replace('$', '.')

/** The method a Kotlin or Java lambda was written in, so a lambda's calls count as its enclosing method's. */
private fun enclosing(method: String) = if (method.startsWith("lambda$")) method.split('$')[1] else method.substringBefore('$')

private fun hash(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }.take(12)

/** Every input site in [classes], the bytes of the compiled release classes, and in the access widener's [widener] lines. */
fun inputSites(classes: List<ByteArray>, widener: List<String> = emptyList()): List<InputSite> {
    val nodes = classes.associate { bytes -> ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_FRAMES) }.let { it.name to (it to bytes) } }
    val groups = nodes.values.groupBy { (node, _) -> node.name.substringBefore('$') }
    val sinks = Sinks(nodes.mapValues { it.value.first })

    val gates = mutableListOf<InputSite.Gate>()
    // The gates that act for the player themselves, not only refuse or wait on a click, so their callers are audited too.
    val acting = mutableSetOf<String>()
    for ((group, members) in groups) {
        val classNodes = members.map { it.first }
        val mixin = classNodes.any { node -> node.invisibleAnnotations.orEmpty().any { it.desc == MIXIN } }
        val does = (listOfNotNull("is a mixin, so it can change any of Minecraft's code".takeIf { mixin }) +
            classNodes.flatMap { node -> node.methods.flatMap { method -> method.instructions.mapNotNull(sinks::of).map { it.does } } }).distinct().sorted()
        if (does.isEmpty()) continue
        val digest = MessageDigest.getInstance("SHA-256")
        // Without debug attributes, so moving a line or renaming a local keeps the audit.
        members.sortedBy { it.first.name }.forEach { (_, bytes) -> digest.update(ClassWriter(0).also { ClassReader(bytes).accept(it, ClassReader.SKIP_DEBUG) }.toByteArray()) }
        gates += InputSite.Gate(display(group), hash(digest), does)

        // Any method of an acting gate may reach its sends, through a field, a callback or a handle, so every call
        // into one from outside counts. Following the paths inside the class would miss the next new way.
        val acts = classNodes.any { node ->
            node.methods.any { method ->
                method.instructions.any { sinks.of(it)?.acts == true } ||
                    // An invoker runs a private Minecraft method, and an accessor that returns nothing writes a private field.
                    method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Invoker;") } ||
                    method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Accessor;") } && Type.getReturnType(method.desc) == Type.VOID_TYPE
            }
        }
        if (acts) acting += group
    }
    val gated = gates.map { it.name }.toSet()

    val calls = groups.filterKeys { display(it) !in gated }.flatMap { (group, members) ->
        members.flatMap { (node, _) ->
            node.methods.flatMap { method ->
                val from = "${display(group)}.${enclosing(method.name)}"
                val called = targets(method).mapNotNull { ref ->
                    // A call through a subclass reaches the method where it is declared.
                    val declaring = sinks.declaring(ref.owner, ref.name, ref.desc) ?: return@mapNotNull null
                    val target = nodes.getValue(declaring).first.methods.first { it.name == ref.name && it.desc == ref.desc }
                    if (declaring.substringBefore('$') !in acting || target.access and Opcodes.ACC_PRIVATE != 0) return@mapNotNull null
                    InputSite.Call(from, "${display(declaring)}.${ref.name}")
                }.toList()
                // A field of an acting gate can hold a callback that sends, so reading one is a call too. An object's
                // `INSTANCE` counts only when the method calls nothing on it, as when it hands the object on.
                val read = method.instructions.filterIsInstance<FieldInsnNode>().mapNotNull { insn ->
                    val gate = insn.owner.substringBefore('$')
                    val field = nodes[insn.owner]?.first?.fields?.firstOrNull { it.name == insn.name }
                    if (gate !in acting || field == null || field.access and Opcodes.ACC_PRIVATE != 0) return@mapNotNull null
                    if (insn.name == "INSTANCE" && called.any { it.to.startsWith(display(gate) + ".") }) return@mapNotNull null
                    InputSite.Call(from, "${display(insn.owner)}.${insn.name}")
                }
                called + read
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
        class M(val name: String, vararg val insns: AbstractInsnNode, val desc: String = "()V", val access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC)
        fun example(name: String, vararg methods: M, mixin: Boolean = false, superName: String = "java/lang/Object", fields: List<String> = emptyList()): ByteArray {
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                this.name = "${MOD}example/$name"
                this.superName = superName
                if (mixin) visitAnnotation(MIXIN, false)
                fields.forEach { visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, it, "Ljava/lang/Runnable;", null, null) }
                methods.forEach { m ->
                    this.methods.add(MethodNode(m.access, m.name, m.desc, null, null).apply {
                        m.insns.forEach(instructions::add)
                        instructions.add(InsnNode(Opcodes.RETURN))
                    })
                }
            }
            return ClassWriter(ClassWriter.COMPUTE_MAXS).also(node::accept).toByteArray()
        }
        fun run(vararg insns: AbstractInsnNode) = M("run", *insns)
        fun call(owner: String, name: String, desc: String = "()V") = MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc)
        fun new(type: String) = arrayOf(TypeInsnNode(Opcodes.NEW, type), InsnNode(Opcodes.POP))
        fun reference(owner: String, name: String, tag: Int = Opcodes.H_INVOKESTATIC) = InvokeDynamicInsnNode(
            "get", "()Ljava/lang/Object;",
            Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Handle(tag, owner, name, "()V", false),
        )
        val sites = inputSites(
            listOf(
                example("Command", run(call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"))),
                example("Click", run(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "handleContainerInput"))),
                example("Aim", run(InsnNode(Opcodes.ACONST_NULL), InsnNode(Opcodes.FCONST_0), FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/player/LocalPlayer", "yRot", "F"))),
                example("Packet", run(*new("net/minecraft/network/protocol/game/ServerboundSwingPacket"))),
                example("Link", run(*new("net/minecraft/network/chat/ClickEvent\$RunCommand"))),
                example("LinkReference", run(reference("net/minecraft/network/chat/ClickEvent\$RunCommand", "<init>", Opcodes.H_NEWINVOKESPECIAL), InsnNode(Opcodes.POP))),
                example("Refuse", run(call("net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "allowMouseClick"))),
                example("Use", run(FieldInsnNode(Opcodes.GETSTATIC, "net/fabricmc/fabric/api/event/player/UseItemCallback", "EVENT", "Lnet/fabricmc/fabric/api/event/Event;"), InsnNode(Opcodes.POP))),
                example("Consume", run(call("net/minecraft/client/KeyMapping", "consumeClick"))),
                example("Inventory", run(call("net/minecraft/client/player/LocalPlayer", "sendOpenInventory"))),
                // ToggleKeyMapping extends KeyMapping, and the scan reads that from Minecraft's own class.
                example("Toggle", run(call("net/minecraft/client/ToggleKeyMapping", "setDown"))),
                example("Reflect", run(call("java/lang/reflect/Method", "invoke"))),
                example("Handle", run(call("java/lang/invoke/VarHandle", "set"))),
                example("Hook", mixin = true),
                example("Caller", run(call("${MOD}example/Command", "run"))),
                example("Clicker", run(call("${MOD}example/Link", "run"))),
                example("Reader", run(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "getPlayerMode"))),
                // A private overload of a sending method hides no caller of the public one.
                example(
                    "Overload",
                    M("send", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"), desc = "(Ljava/lang/String;)V"),
                    M("send", access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC),
                ),
                example("OverloadCaller", run(InsnNode(Opcodes.ACONST_NULL), call("${MOD}example/Overload", "send", "(Ljava/lang/String;)V"))),
                // A method that hands a callback of the gate's own to a scheduler sends when the callback does.
                example("Later", M("schedule", *new("${MOD}example/Later\$1"))),
                example("Later\$1", M("run", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"), access = Opcodes.ACC_PUBLIC)),
                example("LaterCaller", run(call("${MOD}example/Later", "schedule"))),
                // A call through a subclass of a gate, or a click on a screen the mod inherits from Minecraft.
                example("Derived", M("unused", access = Opcodes.ACC_PUBLIC), superName = "${MOD}example/Command"),
                example("DerivedCaller", run(call("${MOD}example/Derived", "run"))),
                example("Menu", M("unused", access = Opcodes.ACC_PUBLIC), superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen"),
                example("MenuClicker", run(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${MOD}example/Menu", "mouseClicked", "(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))),
                // A container screen of the mod's own that forwards its close to Minecraft's.
                example(
                    "Closing",
                    M("onClose", org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0),
                        MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/screens/inventory/InventoryScreen", "onClose", "()V"), access = Opcodes.ACC_PUBLIC),
                    superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen",
                ),
                example("CloseCaller", run(InsnNode(Opcodes.ACONST_NULL), MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${MOD}example/Closing", "onClose", "()V"))),
                // A callback kept in a gate's field, run by a class outside the gates.
                example("Callbacks", run(call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand")), fields = listOf("COMMAND")),
                example("CallbackCaller", run(FieldInsnNode(Opcodes.GETSTATIC, "${MOD}example/Callbacks", "COMMAND", "Ljava/lang/Runnable;"), InsnNode(Opcodes.POP))),
            ),
        )
        assertEquals(
            listOf(
                "call example.CallbackCaller.run -> example.Callbacks.COMMAND",
                "call example.Caller.run -> example.Command.run",
                "call example.CloseCaller.run -> example.Closing.onClose",
                "call example.DerivedCaller.run -> example.Command.run",
                "call example.LaterCaller.run -> example.Later.schedule",
                "call example.OverloadCaller.run -> example.Overload.send",
                "gate example.Aim", "gate example.Callbacks", "gate example.Click", "gate example.Closing", "gate example.Command", "gate example.Consume", "gate example.Handle",
                "gate example.Hook", "gate example.Inventory", "gate example.Later", "gate example.Link", "gate example.LinkReference", "gate example.MenuClicker",
                "gate example.Overload", "gate example.Packet", "gate example.Reflect", "gate example.Refuse", "gate example.Toggle",
                "gate example.Use",
            ),
            sites.map { it.key },
        )
    }
}
