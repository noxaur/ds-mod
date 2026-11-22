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
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** @keep A gate is a class that sends input for the player, or changes or refuses it, and its hash covers the whole class, so any edit to it needs a new approval. A mixin that only hooks drawing is approved by its hooks instead, and its hash is [DRAWS]. A call comes from outside the gates into a gate's method that sends, so a new caller needs approval too, while an edit to an existing caller does not. */
sealed interface InputSite {
    val key: String

    val block: String

    data class Gate(val name: String, val hash: String, val does: List<String>, val features: List<String> = emptyList()) : InputSite {
        override val key get() = "gate $name"
        override val block get() = (listOf("gate $name $hash" + features.joinToString(", ").let { if (it.isEmpty()) "" else "  $it" }) +
            does.map { "    $it" }).joinToString("\n")
    }

    data class Call(val from: String, val to: String) : InputSite {
        override val key get() = "call $from -> $to"
        override val block get() = key
    }
}

const val DRAWS = "draws"

private class Sink(val does: String, val acts: Boolean = true)

private const val MOD = "io/github/noxaur/dsmod/"
private const val MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
private const val INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;"
private const val AT = "Lorg/spongepowered/asm/mixin/injection/At;"
private const val ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;"

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
        // @keep A container screen's close tells the server and a plain screen's does not, so a super call counts: only a subclass of a container screen makes one.
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
    type == "net/minecraft/network/chat/ClickEvent\$RunCommand" -> Sink("builds text that runs a command when the player clicks it", acts = false)
    else -> null
}

/** @keep A call is matched on its owner and on every class the owner extends or implements, so a subclass such as `ToggleKeyMapping`, or one of the mod's own, is no way around it. */
private class Sinks(private val mod: Map<String, ClassNode>) {
    private val lineages = HashMap<String, List<String>>()

    fun lineage(owner: String): List<String> = lineages.getOrPut(owner) {
        val supers = mod[owner]?.let { listOfNotNull(it.superName) + it.interfaces } ?: Sinks::class.java.classLoader
            .getResourceAsStream("$owner.class")?.use { ClassReader(it) }?.let { listOfNotNull(it.superName) + it.interfaces }.orEmpty()
        listOf(owner) + supers.flatMap(::lineage)
    }

    fun declaring(owner: String, name: String, desc: String) =
        lineage(owner).firstOrNull { o -> mod[o]?.methods?.any { it.name == name && it.desc == desc } == true }

    fun called(owner: String, name: String, opcode: Int, desc: String? = null) =
        if (desc != null && declaring(owner, name, desc) != null) null
        else lineage(owner).firstNotNullOfOrNull { calledOn(it, name, opcode) }

    private fun handle(handle: Handle) = if (handle.name == "<init>") built(handle.owner) else called(handle.owner, handle.name, 0, handle.desc)

    fun of(insn: AbstractInsnNode): Sink? = when (insn) {
        is MethodInsnNode -> called(insn.owner, insn.name, insn.opcode, insn.desc)
        is InvokeDynamicInsnNode -> insn.bsmArgs.filterIsInstance<Handle>().firstNotNullOfOrNull(::handle)
        is LdcInsnNode -> (insn.cst as? Handle)?.let(::handle)
        is TypeInsnNode -> if (insn.opcode == Opcodes.NEW) built(insn.desc) else null
        is FieldInsnNode -> when {
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

private fun display(internal: String) = internal.removePrefix(MOD).replace('/', '.').replace('$', '.')

private fun enclosing(method: String) = method.removePrefix("lambda$").removePrefix("dsmod$").substringBefore('$')

private fun annotations(vararg lists: List<AnnotationNode>?) = lists.flatMap { it.orEmpty() }

private fun AnnotationNode.value(name: String): Any? = values.orEmpty().chunked(2).firstOrNull { it[0] == name }?.get(1)

private fun AnnotationNode.strings(name: String) = (value(name) as? List<*>).orEmpty().filterIsInstance<String>()

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
private val DEBUG_ANNOTATIONS = setOf("Lkotlin/jvm/internal/SourceDebugExtension;", "Lkotlin/coroutines/jvm/internal/DebugMetadata;")

private fun behavior(bytes: ByteArray) = ClassWriter(0).also { writer ->
    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
        override fun visitAnnotation(descriptor: String, visible: Boolean) =
            if (descriptor in DEBUG_ANNOTATIONS) null else super.visitAnnotation(descriptor, visible)
    }, ClassReader.SKIP_DEBUG)
}.toByteArray()

private fun hash(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }.take(12)

fun inputSites(classes: List<ByteArray>, widener: List<String> = emptyList()): List<InputSite> {
    val nodes = classes.associate { bytes -> ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_FRAMES) }.let { it.name to (it to bytes) } }
    val groups = nodes.values.groupBy { (node, _) -> node.name.substringBefore('$') }
    val sinks = Sinks(nodes.mapValues { it.value.first })

    val gates = mutableListOf<InputSite.Gate>()
    val acting = mutableSetOf<String>()
    for ((group, members) in groups) {
        val classNodes = members.map { it.first }
        val mixins = classNodes.mapNotNull { node -> annotations(node.visibleAnnotations, node.invisibleAnnotations).firstOrNull { it.desc == MIXIN }?.let { node to it } }
        val sunk = classNodes.flatMap { node -> node.methods.flatMap { method -> method.instructions.mapNotNull(sinks::of).map { it.does } } }
        if (mixins.isEmpty() && sunk.isEmpty()) continue
        val shape = mixins.map { (node, mixin) -> shape(node, mixin, sinks) }
        if (sunk.isEmpty() && shape.isNotEmpty() && shape.all { it.readOnly }) continue
        val draws = sunk.isEmpty() && shape.isNotEmpty() && shape.all { it.draws }
        val does = (shape.flatMap { it.does } + sunk).distinct().sorted()
        val features = classNodes.flatMap { node ->
            node.fields.map { Type.getType(it.desc) }.filter { it.sort == Type.OBJECT }.map { it.internalName } +
                node.methods.flatMap { m -> m.instructions.filterIsInstance<LdcInsnNode>().mapNotNull { (it.cst as? Type)?.internalName } }
        }.filter { "${MOD}core/feature/HasFeature" in sinks.lineage(it) && it != "${MOD}core/feature/HasFeature" }.map { it.substringAfterLast('/') }.distinct().sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        members.sortedBy { it.first.name }.forEach { (_, bytes) -> digest.update(behavior(bytes)) }
        gates += InputSite.Gate(display(group), if (draws) DRAWS else hash(digest), does, features)
        // @keep A drawing mixin's code is not approved line by line, so a call it makes into a gate that acts is listed.
        if (draws) continue

        /** @keep Any method of an acting gate may reach its sends, through a field, a callback or a handle, so every call into one from outside counts. Following the paths inside the class would miss the next new way. */
        val acts = classNodes.any { node ->
            node.methods.any { method ->
                method.instructions.any { sinks.of(it)?.acts == true } ||
                    // @keep An invoker runs a private Minecraft method, and an accessor that returns nothing writes a private field.
                    method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Invoker;") } ||
                    method.invisibleAnnotations.orEmpty().any { it.desc.endsWith("/Accessor;") } && Type.getReturnType(method.desc) == Type.VOID_TYPE
            }
        }
        if (acts) acting += group
    }
    val gated = gates.filter { it.hash != DRAWS }.map { it.name }.toSet()

    val calls = groups.filterKeys { display(it) !in gated }.flatMap { (group, members) ->
        members.flatMap { (node, _) ->
            node.methods.flatMap { method ->
                val from = "${display(group)}.${enclosing(method.name)}"
                val called = targets(method).mapNotNull { ref ->
                    val declaring = sinks.declaring(ref.owner, ref.name, ref.desc) ?: return@mapNotNull null
                    val target = nodes.getValue(declaring).first.methods.first { it.name == ref.name && it.desc == ref.desc }
                    /** @keep Kotlin `private` is file-private and compiles to a nestmate call, so a class in the same file can reach a private member. The caller is already outside the gate, so the member is listed either way. */
                    if (declaring.substringBefore('$') !in acting) return@mapNotNull null
                    InputSite.Call(from, "${display(declaring)}.${ref.name}")
                }.toList()
                /** @keep A field of an acting gate can hold a callback that sends, so reading one is a call too. An object's `INSTANCE` counts only when the method calls nothing on it, as when it hands the object on. */
                val read = method.instructions.filterIsInstance<FieldInsnNode>().mapNotNull { insn ->
                    val gate = insn.owner.substringBefore('$')
                    val field = nodes[insn.owner]?.first?.fields?.firstOrNull { it.name == insn.name }
                    if (gate !in acting || field == null) return@mapNotNull null
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

private val HEADER = """
    # Written by InputSitesTest, so edit the code, never this file. Each site sends input for the player, changes or
    # refuses the player's own input, or calls a class that sends. A change here was approved where it was made: the
    # agent keeps one that sends and refuses as before, and asks the user before it keeps one that does anything new.
    # On a merge conflict, keep both sides and run the test, which writes the file again.
    #
    # gate <class> <hash>  <features it asks>   the whole class, nested classes and lambdas included, and what it does.
    #                                           `draws` for a mixin that only hooks drawing, approved by its hooks.
    # call <method> -> <gate's member>          a method outside the gates that may reach one that sends.
    #     approved by github.com/<login> <time>  the user approved this site, as it is here, in the conversation. It
    #                                           goes when the site changes, and an agent's own approval has none.
""".trimIndent() + "\n\n"

private const val STAMP = "    approved by github.com/"

private fun String.unstamped() = lines().filterNot { it.startsWith(STAMP) }.joinToString("\n")

private fun String.stamp() = lines().firstOrNull { it.startsWith(STAMP) }

private val InputSite.id get() = key.substringAfter(' ').replace(" ", "")

private fun approved(text: String) = text.lines().filter { !it.startsWith("#") }.joinToString("\n")
    .split(Regex("\n(?=\\S)")).map { it.trim('\n') }.filter { it.isNotBlank() }
    .groupBy { block -> block.lineSequence().first().split(Regex("\\s+")).let { if (it[0] == "call") "call ${it[1]} -> ${it[3]}" else "${it[0]} ${it[1]}" } }

class InputSitesTest {
    private val root = Path.of("")

    @Test
    fun `every input site is approved`() {
        val classes = listOf("build/classes/kotlin/main", "build/classes/java/main").map(root::resolve).filter { it.exists() }.flatMap { dir ->
            Files.walk(dir).asSequence().filter { it.extension == "class" }.map { it.readBytes() }.toList()
        }
        assertTrue(classes.size > 100, "InputSitesTest found ${classes.size} release classes. Build the main source set first.")
        val found = inputSites(classes, root.resolve("src/main/resources/dsmod.accesswidener").readLines())
        val file = root.resolve("input-sites.txt")
        val text = file.takeIf { it.exists() }?.readText().orEmpty()
        val approved = approved(text)

        // @keep The user's yes, recorded by `-Papprove=<site>,<site> -PapprovedBy=<login>`, binds to the site as it is now.
        val approve = System.getProperty("inputSites.approve").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val approver = System.getProperty("inputSites.approvedBy").orEmpty()
        val unknown = approve - found.map { it.id }.toSet()
        assertTrue(unknown.isEmpty(), "No input site is named ${unknown.joinToString()}. Name a gate by its class, as edge.SlotGuards, and a call as edge.A.run->edge.B.send.")
        if (approve.isNotEmpty()) {
            assertTrue(Regex("^[A-Za-z0-9-]+$").matches(approver), "Name the user who said yes by their GitHub login: -PapprovedBy=\$(gh api user --jq .login)")
        }
        val stamp = "$STAMP$approver ${Instant.now().truncatedTo(ChronoUnit.MINUTES)}"
        val written = HEADER + found.joinToString("") { site ->
            val kept = approved[site.key].orEmpty().firstOrNull { it.unstamped() == site.block }?.stamp()
            listOfNotNull(site.block, if (site.id in approve) stamp else kept).joinToString("\n", postfix = "\n")
        }
        if (text == written) return

        file.writeText(written)
        val changed = found.filter { site -> site.id !in approve && approved[site.key].orEmpty().none { it.unstamped() == site.block } }
        val gone = approved.keys - found.map { it.key }.toSet()
        if (changed.isEmpty() && gone.isEmpty()) {
            if (approve.isNotEmpty()) return
            fail("mod/input-sites.txt was out of order or held a merge's two sides. It is written again, with nothing new to approve, so run the test again.")
        }
        fail(
            "Each site below sends or refuses input for the player, or calls a class that sends. mod/input-sites.txt " +
                "now lists them, and the next run passes. Read each one before you keep it.\n" +
                "Keep it yourself when the player's input is sent and refused exactly as before: a move, a rename or a " +
                "refactor, a mixin that only changes drawing, a site that is gone.\n" +
                "Otherwise ask the user who prompted you before you keep it: show them the site and what it now sends, " +
                "refuses or triggers. If they say no, take the change back. If they say yes, record it with their " +
                "GitHub login: ./gradlew test --tests '*InputSitesTest*' -Papprove=<site>,<site> " +
                "-PapprovedBy=\$(gh api user --jq .login), naming a gate by its class and a call as from->to.\n\n" +
                changed.joinToString("\n") { site -> site.block.lines().first() + "  " + compared(site, approved[site.key]) } +
                gone.joinToString("") { "\n$it  (gone)" } + "\n",
        )
    }

    private fun compared(site: InputSite, before: List<String>?): String {
        if (before == null) return "(new)" + site.block.lines().drop(1).joinToString("") { "\n$it" }
        val was = before.flatMap { it.unstamped().lines().drop(1) }.toSet()
        val now = site.block.lines().drop(1)
        val added = now.filter { it !in was }
        val dropped = was.filter { it !in now }
        if (added.isEmpty() && dropped.isEmpty()) return "(changed, and does the same as before)"
        return "(changed)" + added.joinToString("") { "\n  + ${it.trim()}" } + dropped.joinToString("") { "\n  - ${it.trim()}" }
    }

    @Test
    fun `a gate's hash follows its code and not its lines`() {
        fun gate(line: Int, inlined: Int, push: Int = Opcodes.ICONST_0, send: String = "sendCommand"): String {
            val smap = "SMAP\nGate.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Gate.kt\n${MOD}example/Gate\n+ 2 DsModClient.kt\n${MOD}edge/DsModClient\n*L\n" +
                "1#1,40:1\n$inlined#2:41\n*S KotlinDebug\n*F\n+ 1 Gate.kt\n${MOD}example/Gate\n*L\n$line#1:41\n*E\n"
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                name = "${MOD}example/Gate"
                superName = "java/lang/Object"
                sourceFile = "Gate.kt"
                sourceDebug = smap
                invisibleAnnotations = mutableListOf(AnnotationNode("Lkotlin/jvm/internal/SourceDebugExtension;").apply { values = mutableListOf("value", listOf(smap)) })
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
            return inputSites(listOf(ClassWriter(ClassWriter.COMPUTE_MAXS).also(node::accept).toByteArray())).filterIsInstance<InputSite.Gate>().single().hash
        }
        val base = gate(line = 16, inlined = 84)
        assertEquals(base, gate(line = 21, inlined = 84), "Moving the gate's code down changed its hash.")
        assertEquals(base, gate(line = 16, inlined = 86), "A blank line above DsModClient.runs changed the hash of the gate that inlines it.")
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
        fun example(
            name: String, vararg methods: M, mixin: String? = null, superName: String = "java/lang/Object",
            fields: List<String> = emptyList(), privateFields: List<String> = emptyList(),
        ): ByteArray {
            val node = ClassNode().apply {
                version = Opcodes.V21
                access = Opcodes.ACC_PUBLIC
                this.name = "${MOD}example/$name"
                this.superName = superName
                if (mixin != null) invisibleAnnotations = mutableListOf(annotation(MIXIN, "value", listOf(Type.getObjectType(mixin))))
                (fields.map { it to (Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) } + privateFields.map { it to (Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC) })
                    .forEach { (it, access) -> visitField(access, it, "Ljava/lang/Runnable;", null, null) }
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
                // @keep ToggleKeyMapping extends KeyMapping, and the scan reads that from Minecraft's own class.
                example("Toggle", run(call("net/minecraft/client/ToggleKeyMapping", "setDown"))),
                example("Reflect", run(call("java/lang/reflect/Method", "invoke"))),
                example("Handle", run(call("java/lang/invoke/VarHandle", "set"))),
                example("Hook", mixin = "net/minecraft/client/Minecraft"),
                example("Caller", run(call("${MOD}example/Command", "run"))),
                example("Clicker", run(call("${MOD}example/Link", "run"))),
                example("Reader", run(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "getPlayerMode"))),
                example(
                    "Overload",
                    M("send", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"), desc = "(Ljava/lang/String;)V"),
                    M("send", access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC),
                ),
                example("OverloadCaller", run(InsnNode(Opcodes.ACONST_NULL), call("${MOD}example/Overload", "send", "(Ljava/lang/String;)V"))),
                example("Sender", M("send", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"), access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC)),
                example("Trigger", M("go", call("${MOD}example/Sender", "send"), access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC)),
                example("Held", run(call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand")), privateFields = listOf("TASK")),
                example("HeldReader", run(FieldInsnNode(Opcodes.GETSTATIC, "${MOD}example/Held", "TASK", "Ljava/lang/Runnable;"), InsnNode(Opcodes.POP))),
                example("Later", M("schedule", *new("${MOD}example/Later\$1"))),
                example("Later\$1", M("run", call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand"), access = Opcodes.ACC_PUBLIC)),
                example("LaterCaller", run(call("${MOD}example/Later", "schedule"))),
                example("Derived", M("unused", access = Opcodes.ACC_PUBLIC), superName = "${MOD}example/Command"),
                example("DerivedCaller", run(call("${MOD}example/Derived", "run"))),
                example("Menu", M("unused", access = Opcodes.ACC_PUBLIC), superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen"),
                example("MenuClicker", run(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${MOD}example/Menu", "mouseClicked", "(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))),
                example(
                    "Closing",
                    M("onClose", VarInsnNode(Opcodes.ALOAD, 0),
                        MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/screens/inventory/InventoryScreen", "onClose", "()V"), access = Opcodes.ACC_PUBLIC),
                    superName = "net/minecraft/client/gui/screens/inventory/InventoryScreen",
                ),
                example("CloseCaller", run(InsnNode(Opcodes.ACONST_NULL), MethodInsnNode(Opcodes.INVOKEVIRTUAL, "${MOD}example/Closing", "onClose", "()V"))),
                example("Callbacks", run(call("net/minecraft/client/multiplayer/ClientPacketListener", "sendCommand")), fields = listOf("COMMAND")),
                example("CallbackCaller", run(FieldInsnNode(Opcodes.GETSTATIC, "${MOD}example/Callbacks", "COMMAND", "Ljava/lang/Runnable;"), InsnNode(Opcodes.POP))),
                example("Asks", run(LdcInsnNode(Type.getObjectType("${MOD}core/feature/general/Fullbright")), InsnNode(Opcodes.POP), call("net/minecraft/client/KeyMapping", "consumeClick"))),
                example("Draws", hook("dsmod\$hand", "extractHand", call("${MOD}example/Command", "run")), mixin = "net/minecraft/client/renderer/ItemInHandRenderer"),
                example("DrawsAtSend", hook("dsmod\$hand", "extractHand", at = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"), mixin = "net/minecraft/client/renderer/ItemInHandRenderer"),
                example("HooksKeys", hook("dsmod\$key", "keyPress"), mixin = "net/minecraft/client/KeyboardHandler"),
                example("HooksPlayer", hook("dsmod\$hand", "extractHand"), mixin = "net/minecraft/client/player/LocalPlayer"),
                example("Replaces", hook("dsmod\$hand", "extractHand"), M("mouseClicked", access = Opcodes.ACC_PUBLIC), mixin = "net/minecraft/client/gui/screens/Screen"),
                example("Reads", M("getMenu", InsnNode(Opcodes.ACONST_NULL), desc = "()Ljava/lang/Object;", access = Opcodes.ACC_PUBLIC, marks = listOf(annotation(ACCESSOR))), mixin = "net/minecraft/client/gui/screens/Screen"),
                example("Writes", M("setMenu", desc = "(Ljava/lang/Object;)V", access = Opcodes.ACC_PUBLIC, marks = listOf(annotation(ACCESSOR))), mixin = "net/minecraft/client/gui/screens/Screen"),
            ),
        )
        assertEquals(
            listOf(
                "call example.CallbackCaller.run -> example.Callbacks.COMMAND",
                "call example.Caller.run -> example.Command.run",
                "call example.CloseCaller.run -> example.Closing.onClose",
                "call example.DerivedCaller.run -> example.Command.run",
                "call example.Draws.hand -> example.Command.run",
                "call example.HeldReader.run -> example.Held.TASK",
                "call example.LaterCaller.run -> example.Later.schedule",
                "call example.OverloadCaller.run -> example.Overload.send",
                "call example.Trigger.go -> example.Sender.send",
                "gate example.Aim", "gate example.Asks", "gate example.Callbacks", "gate example.Click", "gate example.Closing", "gate example.Command", "gate example.Consume",
                "gate example.Draws", "gate example.DrawsAtSend", "gate example.Handle", "gate example.Held", "gate example.Hook", "gate example.HooksKeys", "gate example.HooksPlayer",
                "gate example.Inventory", "gate example.Later", "gate example.Link", "gate example.LinkReference", "gate example.MenuClicker",
                "gate example.Overload", "gate example.Packet", "gate example.Reflect", "gate example.Refuse", "gate example.Replaces", "gate example.Sender", "gate example.Toggle",
                "gate example.Use", "gate example.Writes",
            ),
            sites.map { it.key },
        )
        val gates = sites.filterIsInstance<InputSite.Gate>().associateBy { it.name.removePrefix("example.") }
        assertEquals(listOf("Draws"), gates.filterValues { it.hash == DRAWS }.keys.toList())
        assertEquals("gate example.Draws draws\n    hooks ItemInHandRenderer.extractHand", gates.getValue("Draws").block)
        assertEquals(listOf("Fullbright"), gates.getValue("Asks").features)
    }
}
