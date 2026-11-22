package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaFieldAccess
import com.tngtech.archunit.core.domain.JavaMember
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals

class Callers(
    private val classes: JavaClasses,
    private val checked: DescribedPredicate<JavaClass>,
    private val callers: DescribedPredicate<JavaClass>,
    private val sources: Map<String, String>,
    private val entrypoints: Set<String> = emptySet(),
) {
    private val constants = HashSet<String>()
    private val inline = HashSet<String>()
    private val inlined = HashSet<String>()
    private val words = Words(sources)
    private val groups = HashMap<JavaMember, List<JavaMember>>()
    private val inherited = HashMap<String, Set<String>?>()

    init {
        for (type in classes) {
            val bytes = type.source.orElse(null)?.uri?.toURL()?.readBytes() ?: continue
            val calls = callers.test(type)
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    if (value != null) constants += "${type.name}.$name"
                    return null
                }

                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?) =
                    object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitLocalVariable(local: String, d: String, s: String?, start: Label, end: Label, index: Int) {
                            val callee = local.removePrefix(INLINED).takeIf { it != local } ?: return
                            if (callee == name) inline += "${type.name}.$name/${Type.getArgumentTypes(descriptor).size}" else if (calls) inlined += callee
                        }
                    }
            }, ClassReader.SKIP_FRAMES)
        }
    }

    fun unused(): List<String> {
        val members = classes.that(checked)
            .filter { JavaModifier.SYNTHETIC !in it.modifiers && !it.isAnonymousClass && it.simpleName != "DefaultImpls" }
            .flatMap { it.fields + it.codeUnits }
            .filter { it.name != "<clinit>" && !it.isSynthetic() && (JavaModifier.PUBLIC in it.modifiers || JavaModifier.PROTECTED in it.modifiers) }
            .filter { member -> group(member).none(::rooted) }
        val candidates = members.toSet()
        val live = HashSet<JavaMember>()
        val waiting = HashMap<JavaMember, Set<JavaMember>>()
        for (member in members) {
            val reached = (group(member) + implementations(member)).flatMap { origins(it) }
            if (reached.any { it !in candidates } || named(member)) live += member else waiting[member] = reached.toSet()
        }
        do {
            val woke = waiting.filterValues { it.any(live::contains) }.keys
            live += woke
            waiting.keys -= woke
        } while (woke.isNotEmpty())
        return waiting.keys.map(::describe).distinct().sorted()
    }

    private fun describe(member: JavaMember): String {
        val file = member.owner.packageName.removePrefix("io.github.noxaur.dsmod.").replace('.', '/') + "/" +
            member.owner.source.flatMap { it.fileName }.orElse("?")
        val name = when (member) {
            is JavaField -> member.name.removeSuffix(DELEGATE)
            is JavaCodeUnit -> property(member) ?: "${member.name}(${member.rawParameterTypes.joinToString { it.simpleName }})"
            else -> member.name
        }
        return "$file: ${member.owner.simpleName}.${name.replace(MANGLED, "")}"
    }

    private fun named(member: JavaMember): Boolean = when {
        member is JavaField && "${member.owner.name}.${member.name}" in constants -> named(member)
        member is JavaCodeUnit && "${member.owner.name}.${member.name}/${member.rawParameterTypes.size}" in inline -> member.name in inlined
        else -> false
    }

    private fun named(constant: JavaField): Boolean {
        val name = constant.name
        if (constants.count { it.substringAfterLast('.') == name } == 1) return name in words.used
        val file = constant.owner.packageName.replace('.', '/') + "/" + constant.owner.source.flatMap { it.fileName }.orElse("")
        return "${constant.owner.home().simpleName}.$name" in words.qualified ||
            words.bare.any { (path, names) -> path.endsWith(file) && name in names }
    }

    private fun origins(member: JavaMember, target: JavaMember = member, seen: Set<JavaMember> = setOf(member)): List<JavaMember> =
        member.accessesToSelf.flatMap { access ->
            val origin = access.origin
            val inClass = origin.owner == member.owner
            when {
                access is JavaFieldAccess && access.accessType == JavaFieldAccess.AccessType.SET -> emptyList()
                !callers.test(origin.owner) || origin in seen || together(origin, target) -> emptyList()
                inClass && JavaModifier.BRIDGE in origin.modifiers -> listOf(origin)
                inClass && forwards(origin, member) -> origins(origin, target, seen + origin)
                inClass && origin.isSynthetic() -> origin.owner.codeUnits
                    .filter { it.name == origin.name.substringBefore('$') && !it.isSynthetic() }
                    .ifEmpty { listOf(origin) }
                    .filter { it != member }
                else -> listOf(origin)
            }
        }

    private fun forwards(origin: JavaCodeUnit, member: JavaMember) =
        origin.name == member.name + DEFAULT || origin.name.startsWith(ACCESS) || origin.isConstructor && origin.isSynthetic() ||
            GENERATED.matches(origin.name)

    private fun group(member: JavaMember): List<JavaMember> = groups.getOrPut(member) {
        setOf(member.owner, member.owner.home()).flatMap { it.fields + it.codeUnits }.filter { it == member || together(it, member) }
    }

    private fun together(a: JavaMember, b: JavaMember) = a.owner.home() == b.owner.home() &&
        (a.isConstructor() && b.isConstructor() && a.owner == b.owner && !secondary(a) && !secondary(b) ||
            property(a)?.let { it == property(b) } == true)

    private fun secondary(constructor: JavaMember) = constructor is JavaCodeUnit && !constructor.isSynthetic() &&
        constructor.rawParameterTypes.isNotEmpty() &&
        constructor.constructorCallsFromSelf.any { it.target.owner == constructor.owner && it.target.resolveMember().map { t -> !t.isSynthetic() }.orElse(false) }

    private fun implementations(member: JavaMember) =
        if (member !is JavaCodeUnit || JavaModifier.ABSTRACT !in member.modifiers) emptyList()
        else member.owner.allSubclasses.flatMap { it.codeUnits }.filter { it.name == member.name && it.rawParameterTypes == member.rawParameterTypes }

    private fun rooted(member: JavaMember): Boolean = when {
        member.owner.isAnnotation -> true
        member.annotations.any { MIXIN.matches(it.rawType.name) && it.rawType.simpleName !in setOf("Accessor", "Invoker") } -> true
        member is JavaField -> member.name in setOf("INSTANCE", "Companion") || member.owner.isEnum && member.rawType == member.owner
        member !is JavaCodeUnit -> true
        member.isConstructor -> member.owner.name in entrypoints
        member.name in OBJECT || member.name in setOf("main", "serializer") || GENERATED.matches(member.name) -> true
        member.owner.isEnum && member.name in setOf("values", "valueOf", "getEntries") -> true
        else -> overrides(member)
    }

    private fun overrides(method: JavaCodeUnit): Boolean {
        val signatures = inherited.getOrPut(method.owner.name) {
            try {
                val type = Class.forName(method.owner.name, false, Callers::class.java.classLoader)
                generateSequence(listOf(type)) { level -> level.flatMap { listOfNotNull(it.superclass) + it.interfaces }.ifEmpty { null } }
                    .drop(1).flatten().flatMap { it.declaredMethods.asSequence() }
                    .mapTo(HashSet()) { "${it.name}${it.parameterTypes.map { p -> p.name }}" }
            } catch (_: LinkageError) {
                null
            }
        } ?: return method.owner.name in entrypoints
        return "${method.name}${method.rawParameterTypes.map { it.name }}" in signatures
    }

    private class Words(sources: Map<String, String>) {
        val used = HashSet<String>()
        val qualified = HashSet<String>()
        val bare = HashMap<String, HashSet<String>>()

        init {
            for ((path, text) in sources) {
                val tokens = TOKEN.findAll(text).map { it.value }.toList()
                for ((i, token) in tokens.withIndex()) {
                    if (token == "." || tokens.getOrNull(i - 1) == "val") continue
                    used += token
                    if (tokens.getOrNull(i - 1) == ".") tokens.getOrNull(i - 2)?.let { qualified += "$it.$token" }
                    else bare.getOrPut(path, ::HashSet) += token
                }
            }
        }
    }

    private companion object {
        val TOKEN = Regex("""\w+|\.""")
        const val INLINED = "\$i\$f\$"
        const val DEFAULT = "\$default"
        const val ACCESS = "access\$"
        const val DELEGATE = "\$delegate"
        const val MANGLED = "\$io_github_noxaur_dsmod"
        val OBJECT = setOf("equals", "hashCode", "toString")
        val GENERATED = Regex("component\\d+|copy|copy" + Regex.escape(DEFAULT))
        val MIXIN = Regex("""(org\.spongepowered\.asm\.mixin|com\.llamalad7)\..*""")
        val IS = Regex("is[A-Z].*")

        fun JavaMember.isSynthetic() = JavaModifier.SYNTHETIC in modifiers || JavaModifier.BRIDGE in modifiers

        fun JavaMember.isConstructor() = this is JavaCodeUnit && isConstructor

        fun JavaClass.home(): JavaClass = if (simpleName == "Companion") enclosingClass.orElse(this) else this

        fun property(member: JavaMember): String? {
            val name = member.name.replace(MANGLED, "")
            val params = (member as? JavaCodeUnit)?.rawParameterTypes?.size
            val base = when {
                member is JavaField -> name.removeSuffix(DELEGATE)
                params == 0 && name.startsWith("get") && name.length > 3 -> name.removePrefix("get")
                params == 1 && name.startsWith("set") && name.length > 3 -> name.removePrefix("set")
                params == 0 && IS.matches(name) -> name
                else -> return null
            }
            val bare = if (IS.matches(base)) base.substring(2) else base
            return if (bare.length > 1 && bare[1].isUpperCase()) bare else bare.replaceFirstChar(Char::lowercase)
        }
    }
}

private fun sources(vararg sets: String) = sets.flatMap { listOf("src/$it/kotlin", "src/$it/java") }.map(Path::of).filter { it.exists() }
    .flatMap { dir -> Files.walk(dir).use { files -> files.asSequence().filter { it.isRegularFile() && it.extension in setOf("kt", "java") }.toList() } }
    .associate { it.invariantSeparatorsPathString to it.readText() }

private val entrypoints by lazy {
    Json.parseToJsonElement(Path.of("src/main/resources/fabric.mod.json").readText())
        .jsonObject.getValue("entrypoints").jsonObject.values
        .flatMap { it.jsonArray }.map { it.jsonObject.getValue("value").jsonPrimitive.content }.toSet()
}

class CallersTest {
    @Test
    fun `every release member has a caller in the release or debug code`() {
        val code = sourceSet("main").or(sourceSet("debug"))
        val unused = Callers(everyClass, sourceSet("main"), code, sources("main", "debug"), entrypoints).unused()
        assertEquals(
            emptyList(),
            unused,
            "Nothing in main or debug uses these release members, so the jar carries them for nothing. Delete each one. If a " +
                "game test needs it, write it in src/gametest/…/Probes.kt, which sees `internal`, and turn the private state " +
                "it reads `internal`. If a unit test needs it, write it in that test or in the harness",
        )
    }

    @Test
    fun `the rule catches a member only a game test or nothing uses, and passes the rest`() {
        val inFixture = describe<JavaClass>("in CallersFixture.kt") { it.source.flatMap { s -> s.fileName }.orElse("") == "CallersFixture.kt" }
        val fixtures = ClassFileImporter().withImportOption { it.contains("/rules/Callers") }.importPackagesOf(CallersFixture::class.java).that(inFixture)
        val checked = describe<JavaClass>("the fixture") { it.simpleName in setOf("CallersFixture", "CallersBuilt") || it.name.endsWith("CallersShared\$Companion") }
        val callers = inFixture.and(describe("outside the game test") { it.simpleName != "CallersGameTest" })
        assertEquals(
            listOf(
                "rules/CallersFixture.kt: CallersBuilt.<init>(String)",
                "rules/CallersFixture.kt: CallersFixture.GAP",
                "rules/CallersFixture.kt: CallersFixture.UNNAMED",
                "rules/CallersFixture.kt: CallersFixture.hook()",
                "rules/CallersFixture.kt: CallersFixture.ping()",
                "rules/CallersFixture.kt: CallersFixture.pong()",
                "rules/CallersFixture.kt: CallersFixture.uncalled()",
                "rules/CallersFixture.kt: CallersFixture.uninlined(Function0)",
                "rules/CallersFixture.kt: CallersFixture.unreachable()",
                "rules/CallersFixture.kt: CallersFixture.unread",
                "rules/CallersFixture.kt: Companion.UNSHARED",
            ),
            Callers(fixtures, checked, callers, mapOf(FIXTURE to Path.of(FIXTURE).readText())).unused(),
        )
    }

    private companion object {
        const val FIXTURE = "src/test/kotlin/io/github/noxaur/dsmod/rules/CallersFixture.kt"
    }
}
