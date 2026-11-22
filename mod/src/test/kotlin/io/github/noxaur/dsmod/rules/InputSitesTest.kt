package io.github.noxaur.dsmod.rules

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
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
import kotlin.test.fail

data class Approval(val by: String, val hash: String, val reaches: List<String>)

sealed interface InputSite {
    val name: String

    data class Gate(override val name: String, val hash: String, val does: List<String>, val approval: Approval?, val java: Boolean, val leaks: List<String> = emptyList()) : InputSite

    data class Stray(override val name: String, val does: List<String>) : InputSite

    data class Caller(override val name: String, val reaches: List<String>, val approval: Approval?, val java: Boolean) : InputSite

    data class Stale(override val name: String) : InputSite
}

private class Sink(val does: String)

private const val MOD = "io/github/noxaur/dsmod/"
private const val CORE = "${MOD}core/"
private const val LAYER = "${MOD}edge/input/"
private const val APPROVED = "L${LAYER}InputApproved;"
private const val WIDENER = "aw/dsmod.accesswidener"
private const val REMOTE = "net/minecraft/client/player/RemotePlayer"
private const val MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
private const val INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;"
private const val AT = "Lorg/spongepowered/asm/mixin/injection/At;"
private const val ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;"
private const val INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;"

private val GETTER = Regex("^(get|is|has|can)[A-Z]")
private val LOGIN = Regex("^[A-Za-z0-9-]+$")

private fun what(owner: String, name: String) = "${owner.substringAfterLast('/').replace('$', '.')}.$name"

private fun calledOn(owner: String, name: String, opcode: Int): Sink? {
    val what = what(owner, name)
    val getter = GETTER.containsMatchIn(name)
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
        owner == "net/minecraft/client/KeyMapping" && name == "consumeClick" -> Sink("takes the player's key presses through $what")
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
        // @keep A container screen's close tells the server and a plain screen's does not, so a super call counts: only a subclass of a container screen makes one.
        owner == "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen" &&
            name in setOf("slotClicked", "onClose", "mouseClicked", "keyPressed") ->
            Sink("plays a click or key on a screen through $what")
        owner == "net/minecraft/client/gui/screens/ChatScreen" && name == "handleChatInput" -> Sink("sends chat through $what")
        owner == "net/minecraft/client/Minecraft" && name in setOf("startAttack", "startUseItem", "continueAttack", "pickBlock") -> Sink("acts through $what")
        owner == "org/lwjgl/glfw/GLFW" && name.startsWith("glfwSet") -> Sink("drives the window's input through $what")
        owner.startsWith("net/fabricmc/fabric/api/client/screen/v1/Screen") && owner.endsWith("Events") && name.startsWith("allow") ->
            Sink("can refuse the player's input through $what")
        owner == "net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents" && (name.startsWith("ALLOW_") || name.startsWith("MODIFY_")) ->
            Sink("can refuse or change sent chat through $what")
        owner.startsWith("net/fabricmc/fabric/api/event/player/") || owner == "net/fabricmc/fabric/api/event/client/player/ClientPreAttackCallback" ->
            Sink("can refuse the player's actions through $what")
        owner == "java/lang/reflect/Method" && name == "invoke" || owner == "java/lang/reflect/Field" && name.startsWith("set") ||
            owner == "java/lang/reflect/Constructor" && name == "newInstance" || owner == "java/lang/invoke/MethodHandle" && name.startsWith("invoke") ||
            owner == "java/lang/invoke/VarHandle" && Regex("^(set|compareAnd|weakCompare|getAnd)").containsMatchIn(name) ||
            owner == "java/lang/Class" && name == "forName" || owner == "java/util/ServiceLoader" || owner.endsWith("misc/Unsafe") ||
            owner.startsWith("kotlin/reflect/") && name.startsWith("call") -> Sink("runs code by reflection through $what")
        else -> null
    }
}

private fun built(type: String): Sink? = when {
    type.startsWith("net/minecraft/network/protocol/") && type.substringAfterLast('/').startsWith("Serverbound") ->
        Sink("builds the packet ${type.substringAfterLast('/')}")
    type == "net/minecraft/network/chat/ClickEvent\$RunCommand" -> Sink("builds text that runs a command when the player clicks it")
    else -> null
}

private fun widened(lines: List<String>) = lines.map { it.trim().split(Regex("\\s+")) }
    .filter { it.size >= 4 && it[1] in setOf("method", "field") }.map { "${it[2]}.${it[3]}" }.toSet()

/** @keep A call is matched on its owner and on every class the owner extends or implements, so a subclass such as `ToggleKeyMapping`, or one of the mod's own, is no way around it. */
private class Sinks(private val mod: Map<String, ClassNode>, private val widened: Set<String>) {
    private val lineages = HashMap<String, List<String>>()

    fun lineage(owner: String): List<String> = lineages.getOrPut(owner) {
        val supers = mod[owner]?.let { listOfNotNull(it.superName) + it.interfaces } ?: Sinks::class.java.classLoader
            .getResourceAsStream("$owner.class")?.use { ClassReader(it) }?.let { listOfNotNull(it.superName) + it.interfaces }.orEmpty()
        listOf(owner) + supers.flatMap(::lineage)
    }

    fun declaring(owner: String, name: String, desc: String) =
        lineage(owner).firstOrNull { o -> mod[o]?.methods?.any { it.name == name && it.desc == desc } == true }

    fun called(owner: String, name: String, opcode: Int, desc: String? = null) = when {
        desc != null && declaring(owner, name, desc) != null -> null
        REMOTE in lineage(owner) -> null
        "$owner.$name" in widened && !GETTER.containsMatchIn(name) -> Sink("runs Minecraft's private ${what(owner, name)}")
        else -> lineage(owner).firstNotNullOfOrNull { calledOn(it, name, opcode) }
    }

    private fun handle(handle: Handle) = if (handle.name == "<init>") built(handle.owner) else called(handle.owner, handle.name, 0, handle.desc)

    fun of(insn: AbstractInsnNode): Sink? = when (insn) {
        is MethodInsnNode -> called(insn.owner, insn.name, insn.opcode, insn.desc)
        is InvokeDynamicInsnNode -> insn.bsmArgs.filterIsInstance<Handle>().firstNotNullOfOrNull(::handle)
        is LdcInsnNode -> (insn.cst as? Handle)?.let(::handle)
        is TypeInsnNode -> if (insn.opcode == Opcodes.NEW) built(insn.desc) else null
        is FieldInsnNode -> when {
            (insn.opcode == Opcodes.PUTFIELD || insn.opcode == Opcodes.PUTSTATIC) && "${insn.owner}.${insn.name}" in widened ->
                Sink("writes Minecraft's private ${what(insn.owner, insn.name)}")
            // @keep `this$0` and its kind are the compiler's own fields on a nested class.
            insn.opcode == Opcodes.PUTFIELD && !insn.name.contains('$') && called(insn.owner, "set", 0) != null -> Sink("writes ${insn.owner.substringAfterLast('/')}.${insn.name}")
            insn.opcode == Opcodes.GETSTATIC && insn.owner.startsWith("net/fabricmc/") -> called(insn.owner, insn.name, insn.opcode)
            else -> null
        }
        else -> null
    }
}

private data class MethodRef(val owner: String, val name: String, val desc: String)

private fun targets(method: MethodNode) = method.instructions.asSequence().flatMap { insn ->
    when (insn) {
        is MethodInsnNode -> sequenceOf(MethodRef(insn.owner, insn.name, insn.desc))
        is InvokeDynamicInsnNode -> insn.bsmArgs.asSequence().filterIsInstance<Handle>().map { MethodRef(it.owner, it.name, it.desc) }
        is LdcInsnNode -> (insn.cst as? Handle)?.let { sequenceOf(MethodRef(it.owner, it.name, it.desc)) }.orEmpty()
        else -> emptySequence()
    }
}.filter { it.owner.startsWith(MOD) }

private fun references(node: ClassNode) = (listOfNotNull(node.superName) + node.interfaces + node.methods.flatMap { method ->
    method.instructions.flatMap { insn ->
        when (insn) {
            is MethodInsnNode -> listOf(insn.owner)
            is FieldInsnNode -> listOf(insn.owner)
            is TypeInsnNode -> listOf(insn.desc)
            is InvokeDynamicInsnNode -> insn.bsmArgs.filterIsInstance<Handle>().map { it.owner }
            is LdcInsnNode -> listOfNotNull((insn.cst as? Type)?.takeIf { it.sort == Type.OBJECT }?.internalName, (insn.cst as? Handle)?.owner)
            else -> emptyList()
        }
    }
}).map { if (it.startsWith("[")) Type.getType(it).elementType.let { type -> if (type.sort == Type.OBJECT) type.internalName else "" } else it }

private fun display(internal: String) = internal.removePrefix(MOD).replace('/', '.').replace('$', '.')

private const val INTERNAL = "\$io_github_noxaur_dsmod"

private fun declared(method: String) = method.removeSuffix(INTERNAL)

private fun origin(method: String, kotlin: Boolean) = when {
    method.startsWith("lambda$") -> method.removePrefix("lambda$").substringBeforeLast('$')
    kotlin -> declared(method).removePrefix("access$").substringBefore('$')
    else -> method
}

private val ClassNode.kotlin get() = sourceFile.orEmpty().endsWith(".kt")

private fun annotations(vararg lists: List<AnnotationNode>?) = lists.flatMap { it.orEmpty() }

private fun AnnotationNode.value(name: String): Any? = values.orEmpty().chunked(2).firstOrNull { it[0] == name }?.get(1)

private fun AnnotationNode.strings(name: String) = (value(name) as? List<*>).orEmpty().filterIsInstance<String>()

private fun approval(marks: List<AnnotationNode>) = marks.firstOrNull { it.desc == APPROVED }?.let {
    Approval(it.value("by") as? String ?: "", it.value("hash") as? String ?: "", it.strings("reaches"))
}

private fun ClassNode.approval() = approval(annotations(visibleAnnotations, invisibleAnnotations))

private fun MethodNode.approval() = approval(annotations(visibleAnnotations, invisibleAnnotations))

/** @keep A mixin [draws] when every hook is in a method that draws, at no call or field the scan counts as input, and every other method it adds is a helper of its own, since an unmarked method of a target's name replaces the target's. Anything the scan cannot tell is hashed with its code, so a new kind of hook fails closed. */
private class Shape(val draws: Boolean, val readOnly: Boolean, val does: List<String>)

private val DRAWING = Regex("^(render|extract|submit|draw)[A-Z]|^blit(Sprite)?$")

private fun shape(node: ClassNode, mixin: AnnotationNode, sinks: Sinks): Shape {
    val targets = (mixin.value("value") as? List<*>).orEmpty().filterIsInstance<Type>().map { it.internalName } + mixin.strings("targets").map { it.replace('.', '/') }
    val named = targets.map { it.substringAfterLast('/').replace('$', '.') }
    // @keep A hook on the player, or anything the scan counts as input, can write its fields through a shadow.
    var draws = targets.none { sinks.called(it, "set", 0) != null }
    var hooked = false
    var readOnly = true
    var reads = false
    val does = mutableListOf<String>()
    for (method in node.methods) {
        val marks = annotations(method.visibleAnnotations, method.invisibleAnnotations)
        val injector = marks.firstOrNull { it.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || it.desc.startsWith("Lcom/llamalad7/mixinextras/injector/") }
        when {
            injector != null -> {
                hooked = true
                readOnly = false
                // @keep A hook names a method alone, as `name(desc)`, or as `Lowner;name(desc)`, and only the owner ends in a `;` before the `(`.
                val hooks = injector.strings("method").map { it.substringBefore('(').substringAfter(';').substringBefore(':') }
                val at = (injector.value("at") as? List<*>).orEmpty().filterIsInstance<AnnotationNode>().mapNotNull { it.value("target") as? String }
                // @keep An `at` names a call as `Lowner;name(desc)` or a field as `Lowner;name:type`.
                val atInput = at.any { target ->
                    val owner = target.removePrefix("L").substringBefore(';')
                    val name = target.substringAfter(';').substringBefore('(').substringBefore(':')
                    sinks.called(owner, name, 0) != null || name == "<init>" && built(owner) != null
                }
                if (atInput || hooks.any { !DRAWING.containsMatchIn(it) }) draws = false
                hooks.forEach { hook -> named.forEach { does += "hooks $it.$hook" } }
            }
            marks.any { it.desc.endsWith("/Accessor;") } && Type.getReturnType(method.desc) != Type.VOID_TYPE -> reads = true
            marks.any { it.desc.endsWith("/Shadow;") } -> readOnly = false
            marks.any { it.desc.endsWith("/Accessor;") || it.desc.endsWith("/Invoker;") } -> {
                draws = false
                readOnly = false
                named.forEach { does += "opens $it's private code" }
            }
            method.name in setOf("<init>", "<clinit>") || method.name.startsWith("dsmod$") || method.name.startsWith("lambda$") ||
                method.access and Opcodes.ACC_SYNTHETIC != 0 -> {}
            else -> {
                draws = false
                readOnly = false
                named.forEach { does += "adds ${method.name} to $it" }
            }
        }
    }
    if (does.isEmpty()) does += "is a mixin, so it can change any of Minecraft's code"
    return Shape(draws && hooked, readOnly && reads && !hooked, does.distinct())
}

/** @keep Kotlin copies its debug data, which [ClassReader.SKIP_DEBUG] drops as attributes, into annotations too: the source map, whose lines for an inlined call such as `DsModClient.runs` move with the file it is inlined from, and a coroutine's lines and local names. */
private const val KOTLIN_METADATA = "Lkotlin/Metadata;"

private val DEBUG_ANNOTATIONS = setOf("Lkotlin/jvm/internal/SourceDebugExtension;", "Lkotlin/coroutines/jvm/internal/DebugMetadata;")

private fun behavior(bytes: ByteArray) = ClassWriter(0).also { writer ->
    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
        override fun visitAnnotation(descriptor: String, visible: Boolean) =
            if (descriptor in DEBUG_ANNOTATIONS || descriptor == APPROVED || descriptor == KOTLIN_METADATA) null else super.visitAnnotation(descriptor, visible)
    }, ClassReader.SKIP_DEBUG)
}.toByteArray()

private fun hash(classes: List<Pair<String, ByteArray>>) = MessageDigest.getInstance("SHA-256").let { digest ->
    classes.sortedBy { it.first }.forEach { (_, bytes) -> digest.update(behavior(bytes)) }
    digest.digest().joinToString("") { "%02x".format(it) }.take(12)
}

fun inputSites(classes: List<ByteArray>, widener: List<String> = emptyList(), layer: String = LAYER): List<InputSite> {
    val nodes = classes.associate { bytes -> ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_FRAMES) }.let { it.name to (it to bytes) } }
    val groups = nodes.values.groupBy { (node, _) -> node.name.substringBefore('$') }
    val sinks = Sinks(nodes.mapValues { it.value.first }, widened(widener))
    val sites = mutableListOf<InputSite>()
    val hashed = mutableSetOf<String>()
    val acting = mutableSetOf<String>()

    for ((group, members) in groups) {
        val classNodes = members.map { it.first }
        val top = nodes[group]?.first ?: classNodes.first()
        if (top.access and Opcodes.ACC_ANNOTATION != 0) continue
        val java = top.sourceFile.orEmpty().endsWith(".java")
        val sunk = classNodes.flatMap { node -> node.methods.flatMap { method -> method.instructions.mapNotNull(sinks::of).map { "${display(node.name)}.${origin(method.name, node.kotlin)}" to it.does } } }
        if (group.startsWith(layer)) {
            val covered = sortedSetOf(group)
            val leaks = sortedSetOf<String>()
            val queue = ArrayDeque(listOf(group))
            while (queue.isNotEmpty()) {
                for (reached in groups.getValue(queue.removeFirst()).flatMap { references(it.first) }.map { it.substringBefore('$') }) {
                    when {
                        reached !in groups || reached in covered -> {}
                        reached.startsWith(layer) || reached.startsWith(CORE) -> covered += reached.also(queue::addLast)
                        else -> leaks += display(reached)
                    }
                }
            }
            val code = covered.flatMap { name -> groups.getValue(name).map { it.first.name to it.second } }
            sites += InputSite.Gate(display(group), hash(code), sunk.map { it.second }.distinct().sorted(), top.approval(), java, leaks.toList())
            hashed += group
            continue
        }
        val mixins = classNodes.mapNotNull { node -> annotations(node.visibleAnnotations, node.invisibleAnnotations).firstOrNull { it.desc == MIXIN }?.let { node to it } }
        if (mixins.isNotEmpty()) {
            val shapes = mixins.map { (node, mixin) -> shape(node, mixin, sinks) }
            if (sunk.isEmpty() && (shapes.all { it.readOnly } || shapes.all { it.draws })) continue
            val does = (shapes.flatMap { it.does } + sunk.map { it.second }).distinct().sorted()
            sites += InputSite.Gate(display(group), hash(members.map { it.first.name to it.second }), does, top.approval(), java)
            hashed += group
            // @keep An invoker runs a private Minecraft method, and an accessor that returns nothing writes a private field.
            val opens = classNodes.any { node ->
                node.methods.any { method ->
                    val marks = annotations(method.visibleAnnotations, method.invisibleAnnotations)
                    marks.any { it.desc.endsWith("/Invoker;") } || marks.any { it.desc.endsWith("/Accessor;") } && Type.getReturnType(method.desc) == Type.VOID_TYPE
                }
            }
            if (opens || sunk.isNotEmpty()) acting += group
            continue
        }
        sunk.groupBy({ it.first }, { it.second }).forEach { (from, does) -> sites += InputSite.Stray(from, does.distinct().sorted()) }
    }

    fun source(node: ClassNode, name: String, desc: String?): Triple<ClassNode, String, String?> {
        val outer = node.outerClass?.let { nodes[it]?.first }
        val outerMethod = node.outerMethod
        if (outer != null && outerMethod != null) return source(outer, outerMethod, node.outerMethodDesc)
        val real = origin(name, node.kotlin)
        return Triple(node, real, desc.takeIf { real == declared(name) })
    }

    fun short(owner: String) = owner.removePrefix(layer).replace('/', '.').replace('$', '.')

    val callers = LinkedHashMap<Triple<ClassNode, String, String?>, MutableSet<String>>()
    for ((group, members) in groups) {
        if (group in hashed) continue
        for ((node, _) in members) {
            for (method in node.methods) {
                val called = targets(method).mapNotNull { ref ->
                    val declaring = sinks.declaring(ref.owner, ref.name, ref.desc) ?: return@mapNotNull null
                    when {
                        declaring.startsWith(layer) -> "${short(declaring)}.${origin(ref.name, kotlin = true)}"
                        declaring.substringBefore('$') in acting -> "${display(declaring)}.${ref.name}"
                        else -> null
                    }
                }.toList()
                val read = method.instructions.filterIsInstance<FieldInsnNode>()
                    .filter { it.owner.startsWith(layer) && '$' !in it.name }
                    .filter { it.name != "INSTANCE" && it.name != "Companion" || called.none { call -> call.startsWith("${short(it.owner)}.") } }
                    .map { "${short(it.owner)}.${it.name}" }
                if (called.isNotEmpty() || read.isNotEmpty()) callers.getOrPut(source(node, method.name, method.desc)) { sortedSetOf() } += called + read
            }
        }
    }

    fun overloads(node: ClassNode, name: String) = node.methods.filter { declared(it.name) == name && it.access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) == 0 }

    fun label(node: ClassNode, method: MethodNode) = declared(method.name).let { name -> "${display(node.name)}.$name" + if (overloads(node, name).size > 1) method.desc else "" }

    for ((at, entries) in callers.entries.groupBy({ it.key.first to it.key.second }, { it.key.third to it.value })) {
        val (node, name) = at
        val java = node.sourceFile.orEmpty().endsWith(".java")
        val shared = entries.filter { it.first == null }.flatMap { it.second }
        val methods = overloads(node, name)
        if (methods.isEmpty()) sites += InputSite.Caller("${display(node.name)}.$name", entries.flatMap { it.second }.distinct().sorted(), null, java)
        for (method in methods) {
            val reached = (entries.filter { it.first == method.desc }.flatMap { it.second } + shared).distinct().sorted()
            if (reached.isNotEmpty()) sites += InputSite.Caller(label(node, method), reached, method.approval(), java)
        }
    }

    val aw = widener.filter { it.isNotBlank() && !it.startsWith("accessWidener") && !it.startsWith("#") }
    if (aw.isNotEmpty()) {
        val line = widener.firstOrNull { it.startsWith("# @InputApproved(") }.orEmpty()
        val approval = if (line.isEmpty()) null else Approval(
            Regex("by = \"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty(), Regex("hash = \"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty(), emptyList(),
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(aw.joinToString("\n").toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        sites += InputSite.Gate(WIDENER, digest, listOf("opens Minecraft's private code"), approval, java = false)
    }

    val sited = sites.map { it.name }.toSet()
    for ((node, _) in nodes.values) {
        if (node.approval() != null && display(node.name) !in sited) sites += InputSite.Stale(display(node.name))
        node.methods.filter { it.approval() != null }.map { label(node, it) }.filter { it !in sited }.distinct().forEach { sites += InputSite.Stale(it) }
    }
    return sites.sortedBy { it.name }
}

private fun quoted(values: List<String>, java: Boolean) =
    values.joinToString(", ", if (java) "{" else "[", if (java) "}" else "]") { "\"${if (java) it else it.replace("$", "\\$")}\"" }

fun InputSite.problem(): String? {
    val approval = (this as? InputSite.Gate)?.approval ?: (this as? InputSite.Caller)?.approval
    val by = when {
        approval == null -> "by = \"<login>\", "
        approval.by.isEmpty() -> ""
        else -> "by = \"${approval.by}\", "
    }
    if (approval != null && approval.by.isNotEmpty() && !LOGIN.matches(approval.by)) return "$name  (approved by \"${approval.by}\", which is no GitHub login)"
    return when (this) {
        is InputSite.Stray -> "$name  (outside edge/input) call the primitive in edge/input that does this, or add one there" + does.joinToString("") { "\n    $it" }
        is InputSite.Gate -> {
            val fix = (if (name == WIDENER) "the line # " else "") + "@InputApproved(${by}hash = \"$hash\")"
            when {
                leaks.isNotEmpty() -> "$name  (reaches ${leaks.joinToString()}) a primitive reaches only edge/input and core, so its hash covers all it runs"
                this.approval == null -> "$name  (new) add $fix" + does.joinToString("") { "\n    $it" }
                this.approval.hash != hash -> "$name  (changed) make it $fix" + does.joinToString("") { "\n    $it" }
                else -> null
            }
        }
        is InputSite.Caller -> {
            val approved = this.approval?.reaches.orEmpty()
            val fix = "@InputApproved(${by}reaches = ${quoted(reaches, java)})"
            when {
                name.endsWith(".<init>") || name.endsWith(".<clinit>") -> "$name  (an initializer) reaches ${reaches.joinToString()}. Make the call from a named function, which takes the approval."
                this.approval == null -> "$name  (new) add $fix" + reaches.joinToString("") { "\n  + $it" }
                approved.toSet() == reaches.toSet() -> null
                else -> "$name  (changed) make it $fix" + (reaches - approved.toSet()).joinToString("") { "\n  + $it" } + (approved - reaches.toSet()).joinToString("") { "\n  - $it" }
            }
        }
        is InputSite.Stale -> "$name  (reaches no input) remove its @InputApproved"
    }
}

private const val HOW = "Each site below sends or refuses input for the player, or reaches a primitive in edge/input that does, and " +
    "its @InputApproved says what was approved. Read each one before you change its approval.\n" +
    "Change it yourself when the player's input is sent and refused exactly as before: a refactor (take the new hash or reaches and " +
    "keep by), an approval that moves with the code it approves, a site that is gone.\n" +
    "Otherwise ask the user who prompted you first: show them the site and what it now sends, refuses or triggers. If they say no, " +
    "take the change back. If they say yes, set by to their GitHub login, from gh api user --jq .login.\n\n"

class InputSitesTest {
    private val root = Path.of("")

    @Test
    fun `every input site is approved`() {
        val classes = listOf("build/classes/kotlin/main", "build/classes/java/main").map(root::resolve).filter { it.exists() }.flatMap { dir ->
            Files.walk(dir).asSequence().filter { it.extension == "class" }.map { it.readBytes() }.toList()
        }
        assertTrue(classes.size > 100, "InputSitesTest found ${classes.size} release classes. Build the main source set first.")
        val problems = inputSites(classes, root.resolve("src/main/resources/dsmod.accesswidener").readLines()).mapNotNull { it.problem() }
        if (problems.isNotEmpty()) fail(HOW + problems.joinToString("\n\n") + "\n")
    }

    @Test
    fun `a gate's hash follows its code and not its lines or its approval`() {
        fun gate(line: Int, inlined: Int, push: Int = Opcodes.ICONST_0, send: String = "sendCommand", approved: String? = null): String {
            val smap = "SMAP\nGate.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Gate.kt\n${MOD}example/input/Gate\n+ 2 DsModClient.kt\n${MOD}edge/DsModClient\n*L\n" +
                "1#1,40:1\n$inlined#2:41\n*S KotlinDebug\n*F\n+ 1 Gate.kt\n${MOD}example/input/Gate\n*L\n$line#1:41\n*E\n"
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                name = "${MOD}example/input/Gate"
                superName = "java/lang/Object"
                sourceFile = "Gate.kt"
                sourceDebug = smap
                invisibleAnnotations = listOfNotNull(
                    AnnotationNode("Lkotlin/jvm/internal/SourceDebugExtension;").apply { values = mutableListOf("value", listOf(smap)) },
                    approved?.let { AnnotationNode(APPROVED).apply { values = mutableListOf("hash", it) } },
                ).toMutableList()
                visibleAnnotations = mutableListOf(AnnotationNode("Lkotlin/coroutines/jvm/internal/DebugMetadata;").apply {
                    values = mutableListOf("f", "Gate.kt", "l", listOf(line), "n", listOf("\$this\$run"))
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
                    val start = LabelNode()
                    val end = LabelNode()
                    instructions.add(start)
                    instructions.add(LineNumberNode(line, start))
                    instructions.add(InsnNode(push))
                    instructions.add(VarInsnNode(Opcodes.ISTORE, 0))
                    instructions.add(InsnNode(Opcodes.ACONST_NULL))
                    instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/multiplayer/ClientPacketListener", send, "(Ljava/lang/String;)V"))
                    instructions.add(end)
                    instructions.add(InsnNode(Opcodes.RETURN))
                    localVariables = mutableListOf(LocalVariableNode("\$i\$f\$runs", "I", null, start, end, 0))
                })
            }
            val bytes = ClassWriter(ClassWriter.COMPUTE_MAXS).also(node::accept).toByteArray()
            return inputSites(listOf(bytes), layer = "${MOD}example/input/").filterIsInstance<InputSite.Gate>().single().hash
        }
        val base = gate(line = 16, inlined = 84)
        assertEquals(base, gate(line = 21, inlined = 84), "Moving the gate's code down changed its hash.")
        assertEquals(base, gate(line = 16, inlined = 86), "A blank line above DsModClient.runs changed the hash of the gate that inlines it.")
        assertEquals(base, gate(line = 16, inlined = 84, approved = base), "Writing the approval changed the hash it approves.")
        assertTrue(base != gate(line = 16, inlined = 84, push = Opcodes.ICONST_1), "Changing an instruction kept the gate's hash.")
        assertTrue(base != gate(line = 16, inlined = 84, send = "sendChat"), "Changing what the gate calls kept its hash.")
    }

    @Test
    fun `the scan catches each kind of input site`() {
        class M(
            val name: String, vararg val insns: AbstractInsnNode, val desc: String = "()V",
            val access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, val marks: List<AnnotationNode> = emptyList(),
        )
        fun annotation(desc: String, vararg values: Any) = AnnotationNode(desc).apply { this.values = values.toMutableList() }
        fun approved(vararg values: Any) = annotation(APPROVED, *values)
        fun example(
            name: String, vararg methods: M, mixin: String? = null, superName: String = "java/lang/Object",
            fields: List<String> = emptyList(), outer: String? = null, marks: List<AnnotationNode> = emptyList(),
        ): ByteArray {
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                this.name = "${MOD}example/$name"
                this.superName = superName
                sourceFile = if (mixin != null) "${name.substringAfterLast('/')}.java" else "${name.substringAfterLast('/').substringBefore('$')}.kt"
                invisibleAnnotations = (marks + listOfNotNull(mixin?.let { annotation(MIXIN, "value", listOf(Type.getObjectType(it))) })).toMutableList()
                if (outer != null) {
                    outerClass = "${MOD}example/${outer.substringBefore('.')}"
                    outerMethod = outer.substringAfter('.')
                    outerMethodDesc = "()V"
                }
                fields.forEach { visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, it, "Ljava/lang/Runnable;", null, null) }
                methods.forEach { m ->
                    this.methods.add(MethodNode(m.access, m.name, m.desc, null, null).apply {
                        visibleAnnotations = m.marks.toMutableList()
                        m.insns.forEach(instructions::add)
                        instructions.add(InsnNode(Opcodes.RETURN))
                    })
                }
            }
            return ClassWriter(ClassWriter.COMPUTE_MAXS).also(node::accept).toByteArray()
        }
        fun run(vararg insns: AbstractInsnNode) = M("run", *insns)
        fun hook(name: String, into: String, vararg insns: AbstractInsnNode, at: String? = null) = M(
            name, *insns, access = Opcodes.ACC_PRIVATE,
            marks = listOf(annotation(INJECT, "method", listOf(into), "at", listOf(if (at == null) annotation(AT, "value", "HEAD") else annotation(AT, "value", "INVOKE", "target", at)))),
        )
        fun call(owner: String, name: String, desc: String = "()V") = MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc)
        fun send() = call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand")
        fun primitive() = call("${MOD}example/input/Send", "run")
        fun new(type: String) = arrayOf(TypeInsnNode(Opcodes.NEW, type), InsnNode(Opcodes.POP))
        fun reference(owner: String, name: String, tag: Int = Opcodes.H_INVOKESTATIC) = InvokeDynamicInsnNode(
            "get", "()Ljava/lang/Object;",
            Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Handle(tag, owner, name, "()V", false),
        )
        fun caller(name: String, reaches: String, by: String = "noxaur") =
            example(name, M("run", primitive(), marks = listOf(approved("by", by, "reaches", listOf(reaches)))))
        fun static(owner: String, field: String) = arrayOf(FieldInsnNode(Opcodes.GETSTATIC, owner, field, "Ljava/lang/Object;"), InsnNode(Opcodes.POP))
        fun put(owner: String, field: String) = arrayOf(InsnNode(Opcodes.ACONST_NULL), InsnNode(Opcodes.ICONST_0), FieldInsnNode(Opcodes.PUTFIELD, owner, field, "I"))
        val sites = inputSites(
            listOf(
                example("Command", run(send())),
                example("Click", run(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "handleContainerInput"))),
                example("Aim", run(InsnNode(Opcodes.ACONST_NULL), InsnNode(Opcodes.FCONST_0), FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/player/LocalPlayer", "yRot", "F"))),
                example("Packet", run(*new("net/minecraft/network/protocol/game/ServerboundSwingPacket"))),
                example("Link", run(*new("net/minecraft/network/chat/ClickEvent\$RunCommand"))),
                example("LinkReference", run(reference("net/minecraft/network/chat/ClickEvent\$RunCommand", "<init>", Opcodes.H_NEWINVOKESPECIAL), InsnNode(Opcodes.POP))),
                example("Refuse", run(call("net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "allowMouseClick"))),
                example("Use", run(*static("net/fabricmc/fabric/api/event/player/UseItemCallback", "EVENT"))),
                example("ChangesChat", run(*static("net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents", "ALLOW_COMMAND"))),
                example("HearsChat", run(*static("net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents", "COMMAND"))),
                example("Consume", run(call("net/minecraft/client/KeyMapping", "consumeClick"))),
                example("Inventory", run(call("net/minecraft/client/player/LocalPlayer", "sendOpenInventory"))),
                // @keep ToggleKeyMapping extends KeyMapping, and the scan reads that from Minecraft's own class.
                example("Toggle", run(call("net/minecraft/client/ToggleKeyMapping", "setDown"))),
                example("Preview", run(call(REMOTE, "setId", "(I)V"))),
                example("Reflect", run(call("java/lang/reflect/Method", "invoke"))),
                example("Handle", run(call("java/lang/invoke/VarHandle", "set"))),
                example("Widened", run(call("net/minecraft/client/Minecraft", "handleKeybinds"))),
                example("WidenedWrite", run(*put("net/minecraft/client/gui/Hud", "titleTime"))),
                example("WidenedRead", run(call("net/minecraft/client/gui/components/PlayerTabOverlay", "getPlayerInfos"))),
                example("Reader", run(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "getPlayerMode"))),
                example(
                    "Overload",
                    M("send", send(), desc = "(Ljava/lang/String;)V"),
                    M("send", access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC),
                ),
                example("Menu", M("unused", access = Opcodes.ACC_PUBLIC), superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen"),
                example("MenuClicker", run(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${MOD}example/Menu", "mouseClicked", "(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))),
                example(
                    "Closing",
                    M("onClose", VarInsnNode(Opcodes.ALOAD, 0),
                        MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/screens/inventory/InventoryScreen", "onClose", "()V"), access = Opcodes.ACC_PUBLIC),
                    superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen",
                ),
                example("input/Send", run(send())),
                example("input/Budgeted", run(call("${MOD}core/Budget", "spend"))),
                example("input/Leaky", run(call("${MOD}example/Plain", "run"))),
                example("Plain", run()),
                example("Caller", run(primitive())),
                example("Reference", run(reference("${MOD}example/input/Send", "run"), InsnNode(Opcodes.POP))),
                example("Lambda", M("register\$lambda\$0", primitive(), access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC)),
                example("Later", M("schedule", *new("${MOD}example/Later\$1"))),
                example("Later\$1", M("run", primitive(), access = Opcodes.ACC_PUBLIC), outer = "Later.schedule"),
                example("Derived", M("unused", access = Opcodes.ACC_PUBLIC), superName = "${MOD}example/input/Send"),
                example("DerivedCaller", run(call("${MOD}example/Derived", "run"))),
                caller("Approved", "Send.run"),
                caller("Unsure", "Send.other"),
                example("Stale", M("run", marks = listOf(approved("reaches", listOf("Send.run"))))),
                example("Hook", mixin = "net/minecraft/client/Minecraft"),
                example("Draws", hook("dsmod\$hand", "extractHand", primitive()), mixin = "net/minecraft/client/renderer/ItemInHandRenderer"),
                example("DrawsAtSend", hook("dsmod\$hand", "extractHand", at = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"), mixin = "net/minecraft/client/renderer/ItemInHandRenderer"),
                example("HooksKeys", hook("dsmod\$key", "keyPress", primitive()), mixin = "net/minecraft/client/KeyboardHandler"),
                example("HooksPlayer", hook("dsmod\$hand", "extractHand"), mixin = "net/minecraft/client/player/LocalPlayer"),
                example("Replaces", hook("dsmod\$hand", "extractHand"), M("mouseClicked", access = Opcodes.ACC_PUBLIC), mixin = "net/minecraft/client/gui/screens/Screen"),
                example("Reads", M("getMenu", InsnNode(Opcodes.ACONST_NULL), desc = "()Ljava/lang/Object;", access = Opcodes.ACC_PUBLIC, marks = listOf(annotation(ACCESSOR))), mixin = "net/minecraft/client/gui/screens/Screen"),
                example(
                    "Writes", M("setMenu", desc = "(Ljava/lang/Object;)V", access = Opcodes.ACC_PUBLIC, marks = listOf(annotation(ACCESSOR))),
                    mixin = "net/minecraft/client/gui/screens/Screen", marks = listOf(approved("by", "noxaur", "hash", "000000000000")),
                ),
                caller("Bad", "Send.run", by = "a friend"),
                example(
                    "Overloaded",
                    M("send", primitive(), marks = listOf(approved("by", "noxaur", "reaches", listOf("Send.run")))),
                    M("send", primitive(), desc = "(I)V"),
                ),
                example("input/Holder", run(), fields = listOf("TASK")),
                example("SingletonUser", run(*static("${MOD}example/input/Send", "INSTANCE"))),
                example(
                    "Internal",
                    M("send", primitive(), desc = "(I)V", marks = listOf(approved("by", "noxaur", "reaches", listOf("Send.run")))),
                    M("send$INTERNAL", primitive(), desc = "(Ljava/lang/String;)V"),
                ),
                example("HolderReader", run(*static("${MOD}example/input/Holder", "TASK"))),
                example("Invokes", M("invokeStartAttack", access = Opcodes.ACC_PUBLIC, marks = listOf(annotation(INVOKER))), mixin = "net/minecraft/client/Minecraft"),
                example("InvokerCaller", run(call("${MOD}example/Invokes", "invokeStartAttack"))),
            ),
            widener = listOf(
                "accessWidener v2 official",
                "accessible method net/minecraft/client/Minecraft handleKeybinds ()V",
                "accessible field net/minecraft/client/gui/Hud titleTime I",
                "accessible method net/minecraft/client/gui/components/PlayerTabOverlay getPlayerInfos ()Ljava/util/List;",
            ),
            layer = "${MOD}example/input/",
        )
        fun names(kind: Class<out InputSite>) = sites.filter { kind.isInstance(it) }.map { it.name.removePrefix("example.") }
        assertEquals(
            listOf(
                "Aim.run", "ChangesChat.run", "Click.run", "Closing.onClose", "Command.run", "Consume.run", "Handle.run", "Inventory.run", "Link.run",
                "LinkReference.run", "MenuClicker.run", "Overload.send", "Packet.run", "Reflect.run", "Refuse.run", "Toggle.run", "Use.run",
                "Widened.run", "WidenedWrite.run",
            ),
            names(InputSite.Stray::class.java),
        )
        assertEquals(
            listOf("aw/dsmod.accesswidener", "DrawsAtSend", "Hook", "HooksKeys", "HooksPlayer", "Invokes", "Replaces", "Writes", "input.Budgeted", "input.Holder", "input.Leaky", "input.Send"),
            names(InputSite.Gate::class.java),
        )
        assertEquals(
            listOf(
                "Approved.run", "Bad.run", "Caller.run", "DerivedCaller.run", "Draws.dsmod\$hand", "HolderReader.run", "Internal.send(I)V", "Internal.send(Ljava/lang/String;)V",
                "InvokerCaller.run", "Lambda.register", "Later.schedule", "Overloaded.send()V", "Overloaded.send(I)V", "Reference.run", "SingletonUser.run", "Unsure.run",
            ),
            names(InputSite.Caller::class.java),
        )
        assertEquals(listOf("Stale.run"), names(InputSite.Stale::class.java))
        val problems = sites.associate { it.name.removePrefix("example.") to it.problem() }
        assertEquals(null, problems.getValue("Approved.run"))
        assertEquals("example.Unsure.run  (changed) make it @InputApproved(by = \"noxaur\", reaches = [\"Send.run\"])\n  + Send.run\n  - Send.other", problems.getValue("Unsure.run"))
        assertTrue(problems.getValue("Writes").orEmpty().startsWith("example.Writes  (changed) make it @InputApproved(by = \"noxaur\", hash = \""))
        assertTrue(problems.getValue("Bad.run").orEmpty().contains("no GitHub login"))
        assertEquals(null, problems.getValue("Overloaded.send()V"))
        assertTrue(problems.getValue("Overloaded.send(I)V").orEmpty().contains("(new)"))
        assertEquals(null, problems.getValue("Internal.send(I)V"))
        assertTrue(problems.getValue("Internal.send(Ljava/lang/String;)V").orEmpty().contains("(new)"))
        assertEquals(listOf("Send.INSTANCE"), (sites.single { it.name == "example.SingletonUser.run" } as InputSite.Caller).reaches)
        assertEquals(listOf("Holder.TASK"), (sites.single { it.name == "example.HolderReader.run" } as InputSite.Caller).reaches)
        assertTrue(problems.getValue("aw/dsmod.accesswidener").orEmpty().contains("(new) add the line # @InputApproved("))
        assertEquals(listOf("example.Invokes.invokeStartAttack"), (sites.single { it.name == "example.InvokerCaller.run" } as InputSite.Caller).reaches)
        assertTrue(problems.getValue("input.Leaky").orEmpty().contains("reaches example.Plain"))
        assertEquals(null, (sites.single { it.name == "example.input.Budgeted" } as InputSite.Gate).leaks.ifEmpty { null })
    }
}
