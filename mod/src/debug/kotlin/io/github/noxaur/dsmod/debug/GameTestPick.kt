package io.github.noxaur.dsmod.debug

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile
import java.lang.classfile.constantpool.StringEntry
import java.lang.classfile.constantpool.Utf8Entry
import java.lang.reflect.AccessFlag
import java.util.concurrent.TimeUnit

const val SMOKE_TEST = "io.github.noxaur.dsmod.gametest.SettingsScreenTest"

private const val HUB = 12

/** @keep A test takes 8 seconds on a CI runner, and the boot leaves the job about 70 of its 180. */
const val MOST_PICKED = 8

private const val GAME_TEST = "net/fabricmc/fabric/api/client/gametest/v1/FabricClientGameTest"
private val BUILD = listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/", "src/gametest/fabric.mod.json")
private val RESOURCES = listOf("src/main/resources/", "src/debug/resources/", "src/gametest/resources/", "src/test/resources/profiles/")
private val SOURCE_SETS = listOf("main", "debug", "gametest")

/** @keep A class name in a descriptor, a signature or an inlined function's source map, so what an inline call or a type names counts too. */
private val OWN_CLASS = Regex("""io/github/noxaur/dsmod/[\w/$]+""")

/** @keep Each class with the classes it names, its file and its code's strings, where a lambda or an anonymous object counts as the class it is written in. */
data class Sources(
    val names: Map<String, Set<String>>,
    val files: Map<String, String>,
    val tests: Map<String, String>,
    val tools: Set<String>,
    val strings: Map<String, Set<String>> = emptyMap(),
)

data class Picked(val tests: Set<String>, val unreached: List<String>)

fun readSources(mod: File): Sources {
    val roots = mutableMapOf<String, String>()
    val named = mutableMapOf<String, Set<String>>()
    val said = mutableMapOf<String, Set<String>>()
    val anonymous = mutableSetOf<String>()
    val supers = mutableMapOf<String, List<String>>()
    val concrete = mutableSetOf<String>()
    val tools = mutableSetOf<String>()
    for (set in SOURCE_SETS) {
        for (language in listOf("kotlin", "java")) {
            mod.resolve("build/classes/$language/$set").walkTopDown().filter { it.extension == "class" }.forEach { file ->
                val model = ClassFile.of().parse(file.readBytes())
                val name = model.thisClass().asInternalName()
                val self = model.findAttribute(Attributes.innerClasses()).map { it.classes() }.orElse(emptyList()).find { it.innerClass().asInternalName() == name }
                if (self == null) {
                    val source = model.findAttribute(Attributes.sourceFile()).orElseThrow().sourceFile().stringValue()
                    val path = "src/$set/${if (source.endsWith(".java")) "java" else "kotlin"}/${name.substringBeforeLast('/')}/$source"
                    check(mod.resolve(path).exists()) { "$name compiles from $path, which does not exist. Keep each file in the folder of its package." }
                    roots[name] = path
                } else if (self.outerClass().isEmpty || self.innerName().isEmpty) {
                    anonymous += name
                }
                named[name] = model.constantPool().filterIsInstance<Utf8Entry>().flatMap { OWN_CLASS.findAll(it.stringValue()).map { match -> match.value } }.toSet()
                said[name] = model.constantPool().filterIsInstance<StringEntry>().map { it.stringValue() }.toSet()
                supers[name] = (model.superclass().map { listOf(it) }.orElse(emptyList()) + model.interfaces()).map { it.asInternalName() }
                if (!model.flags().has(AccessFlag.ABSTRACT)) concrete += name
                if (model.methods().any { it.methodName().equalsString("main") && it.methodType().equalsString("([Ljava/lang/String;)V") }) tools += name
            }
        }
    }
    fun isTest(name: String): Boolean = name == GAME_TEST || supers[name].orEmpty().any(::isTest)
    val tests = concrete.filter(::isTest)
    fun node(name: String): String = if ('$' in name && (name in anonymous || name !in named)) node(name.substringBeforeLast('$').trimEnd('$')) else name
    val files = named.keys.filterNot(anonymous::contains).associateWith { checkNotNull(roots[it.substringBefore('$')]) { "$it has no outer class" } }
    val names = mutableMapOf<String, MutableSet<String>>()
    for ((name, refs) in named) names.getOrPut(node(name), ::mutableSetOf) += refs.filter(named::containsKey).map(::node).toSet() - node(name)
    val strings = said.entries.groupBy({ node(it.key) }, { it.value }).mapValues { it.value.flatten().toSet() }
    return Sources(names, files, tests.associateWith { it.replace('/', '.') }, tools.map { files.getValue(node(it)) }.toSet(), strings)
}

fun pickGameTests(sources: Sources, changed: Collection<String>): Picked {
    val (names, files, tests, tools, strings) = sources
    val classes = files.keys.groupBy(files::getValue)
    val namers = names.keys.flatMap { name -> names.getValue(name).map { it to name } }.groupBy({ it.first }, { it.second })
    val hubs = names.keys.filter { name -> namers[name].orEmpty().count(tests::containsKey) > HUB }.toSet()

    // @keep Each test that reaches the classes, by how many names away it is. A hub names nearly everything, so a walk stops at one.
    fun reaching(start: Collection<String>): Map<String, Int> {
        val seen = start.toMutableSet()
        val found = mutableMapOf<String, Int>()
        var level = start.toSet()
        var distance = 0
        while (level.isNotEmpty()) {
            level.mapNotNull(tests::get).forEach { found.putIfAbsent(it, distance) }
            level = level.filterNot(hubs::contains).flatMap { namers[it].orEmpty() }.filter(seen::add).toSet()
            distance++
        }
        return found
    }

    val particular = mutableMapOf<String, Map<String, Int>>()

    // @keep The game or a registry calls a mixin, an entrypoint or a feature's edge, so classes no test reaches reach the tests of the most particular class they name: the one fewest tests stand nearest to.
    fun reach(own: Set<String>): Map<String, Int> = reaching(own).ifEmpty {
        val near = (own.flatMap(names::getValue).toSet() - own - hubs).map { name ->
            particular.getOrPut(name) { particular[name] = emptyMap(); reach(setOf(name)) }
        }.filter(Map<String, Int>::isNotEmpty)
        val rank = { found: Map<String, Int> -> found.values.min().let { nearest -> nearest to found.count { it.value == nearest } } }
        val best = near.minOfWithOrNull(compareBy({ it.first }, { it.second }), rank)
        near.filter { rank(it) == best }.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.min() }
    }

    // @keep The nearest tests, as many whole distances of them as fit in one pick, or the smoke test when even the nearest are too many.
    fun fit(found: Map<String, Int>) = found.entries.groupBy({ it.value }, { it.key }).toSortedMap().values
        .runningFold(emptySet<String>(), Set<String>::plus).last { it.size <= MOST_PICKED }.ifEmpty { if (found.isEmpty()) emptySet() else setOf(SMOKE_TEST) }

    val picked = mutableSetOf<String>()
    val unreached = mutableListOf<String>()
    for (path in changed) {
        if (BUILD.any { path == it || it.endsWith('/') && path.startsWith(it) }) picked += SMOKE_TEST
        if (RESOURCES.any(path::startsWith)) {
            // @keep A resource reaches the tests of the classes that load it by name, and one no class names, such as a mixin list, any test boots through.
            val name = path.substringAfterLast('/')
            val loaders = strings.filterValues { said -> said.any { it == name || it.endsWith("/$name") } }.keys - hubs
            picked += fit(reach(loaders)).ifEmpty { setOf(SMOKE_TEST) }
            continue
        }
        if ("/core/" in path || path in tools || SOURCE_SETS.none { path.startsWith("src/$it/") }) continue
        val own = classes[path]?.toSet()
        if (own == null) {
            // @keep A deleted file breaks what named it at compile time, which any test boots through.
            if (path.endsWith(".kt") || path.endsWith(".java")) picked += SMOKE_TEST
            continue
        }
        val reached = fit(reach(own))
        val throughHubs = (own.flatMap(names::getValue).toSet() - own).let { it.isNotEmpty() && it.all(hubs::contains) }
        when {
            own.any(hubs::contains) || reached.isEmpty() && throughHubs -> picked += SMOKE_TEST
            reached.isNotEmpty() -> picked += reached
            !path.startsWith("src/gametest/") -> unreached += path
        }
    }
    return Picked(if (picked.size > MOST_PICKED) setOf(SMOKE_TEST) else picked, unreached)
}

/** @keep Writes the game test manifest with the tests the third argument names: all, a comma list of them, or, with none, those the change reaches. */
fun main(args: Array<String>) {
    val (mod, out) = args.take(2).map(::File)
    val sources = readSources(mod)
    val tests = when (val only = args.getOrNull(2)) {
        "all" -> sources.tests.values.toSet()
        null -> pickGameTests(sources, changed(mod)).tests.also {
            println(if (it.isEmpty()) "No game test reaches the change." else "Game tests the change reaches: ${it.map { name -> name.substringAfterLast('.') }.sorted().joinToString()}")
        }
        else -> only.split(",").map { name ->
            sources.tests.values.singleOrNull { it.substringAfterLast('.') == name } ?: error("-Pgametests=$only names $name, which is no game test")
        }.toSet()
    }.sorted()
    val manifest = Json.parseToJsonElement(mod.resolve("src/gametest/fabric.mod.json").readText()).jsonObject
    val entrypoints = manifest.getValue("entrypoints").jsonObject +
        ("fabric-client-gametest" to JsonArray(tests.map { JsonObject(mapOf("adapter" to JsonPrimitive("kotlin"), "value" to JsonPrimitive(it))) }))
    out.mkdirs()
    out.resolve("fabric.mod.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(manifest + ("entrypoints" to JsonObject(entrypoints)))))
    out.resolve("tests").writeText(tests.joinToString("\n"))
}

private fun changed(mod: File): List<String> {
    val top = File(git(mod, "rev-parse", "--show-toplevel"))
    val index = File.createTempFile("gametests", "index").apply { deleteOnExit() }
    File(git(top, "rev-parse", "--path-format=absolute", "--git-path", "index")).takeIf { it.exists() }?.copyTo(index, overwrite = true)
    val env = mapOf("GIT_INDEX_FILE" to index.path)
    git(top, "add", "-A", env = env)
    val tree = git(top, "write-tree", env = env)
    val base = listOf("origin/main", "main").firstNotNullOf { runCatching { git(top, "merge-base", "HEAD", it) }.getOrNull() }
    val prefix = mod.canonicalFile.relativeTo(top.canonicalFile).invariantSeparatorsPath + "/"
    return git(top, "diff", "--name-only", base, tree, "--", prefix).lines().filter(String::isNotBlank).map { it.removePrefix(prefix) }
}

private fun git(dir: File, vararg args: String, env: Map<String, String> = emptyMap()): String {
    val process = ProcessBuilder("git", *args).directory(dir).redirectErrorStream(true).apply { environment() += env }.start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0) { "git ${args.joinToString(" ")} failed: $output" }
    return output
}
