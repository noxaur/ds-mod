package io.github.noxaur.dsmod.debug

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/** The game test that a change to the build, or to a file most tests reach, runs. The beta pipeline runs the rest. */
const val SMOKE_TEST = "SettingsScreenTest"

/** More game tests than this reaching a file make it a hub, which a walk does not cross. */
private const val HUB = 12

private val BUILD = listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/", "src/main/resources/", "src/debug/resources/", "src/gametest/resources/")
private val SOURCE_ROOTS = listOf("src/main/", "src/debug/", "src/gametest/")
private val DECLARED = Regex(
    """^(?:(?:public|internal|abstract|open|sealed|data|enum|annotation|inline|const|value|fun)\s+)*""" +
        """(?:class|object|interface|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)""",
    RegexOption.MULTILINE,
)
private val WORD = Regex("""\w+""")

/** The game tests a change reaches, and each changed file under `edge` or `debug` that reaches none. */
data class Picked(val tests: Set<String>, val unreached: List<String>)

/**
 * Picks the game tests that the files in [changed] reach, each a path under `mod/`. [sources] holds every Kotlin
 * and Java file of `main`, `debug` and `gametest` by its path, and [tests] the name of every game test.
 *
 * A changed file reaches the game tests that name it, or, failing that, the tests that name the files that name it,
 * and so on up, through `core` too. A file nothing names, such as a mixin, reaches what the files it names reach. A
 * change to `core` is left to the unit tests, so it reaches nothing and is never unreached. A test that names a class
 * only in its KDoc reaches it too, which is how a test claims a file the walk misses.
 */
fun pickGameTests(sources: Map<String, String>, tests: Set<String>, changed: Collection<String>): Picked {
    val files = sources
    val names = files.mapValues { (path, text) -> DECLARED.findAll(text).map { it.groupValues[1] }.toSet() + stem(path) }
    val words = files.mapValues { (_, text) -> WORD.findAll(text).map { it.value }.toSet() }
    val testFiles = files.keys.filter { stem(it) in tests }
    val hubs = files.keys.filter { path -> testFiles.count { it != path && words.getValue(it).any(names.getValue(path)::contains) } > HUB }.toSet()
    fun namers(path: String) = files.keys.filter { it != path && words.getValue(it).any(names.getValue(path)::contains) }

    fun walkUp(start: String): Set<String> {
        var level = setOf(start)
        val seen = mutableSetOf(start)
        while (level.isNotEmpty()) {
            val found = level.flatMap(::namers).filter(seen::add)
            val reached = found.map(::stem).filter(tests::contains).toSet()
            if (reached.isNotEmpty()) return reached
            level = found.filterNot(hubs::contains).toSet()
        }
        return emptySet()
    }

    val picked = mutableSetOf<String>()
    val unreached = mutableListOf<String>()
    for (path in changed) {
        if (BUILD.any { path == it || it.endsWith('/') && path.startsWith(it) }) picked += SMOKE_TEST
        if ("/core/" in path) continue
        if (path !in files && SOURCE_ROOTS.any(path::startsWith) && (path.endsWith(".kt") || path.endsWith(".java"))) {
            // A deleted file breaks what named it at compile time, or a mixin list at boot, which any test boots through.
            picked += SMOKE_TEST
            continue
        }
        if (path !in files) {
            val name = path.substringAfterLast('/')
            picked += testFiles.filter { sources.getValue(it).contains(name) }.map(::stem)
            continue
        }
        if (stem(path) in tests) {
            picked += stem(path)
            continue
        }
        val reached = when {
            path in hubs -> setOf(SMOKE_TEST)
            namers(path).isEmpty() -> files.keys.filter { it != path && it !in hubs && words.getValue(path).any(names.getValue(it)::contains) }
                .flatMap { if (stem(it) in tests) setOf(stem(it)) else walkUp(it) }.toSet()
            else -> walkUp(path)
        }
        picked += if (reached.size > HUB) setOf(SMOKE_TEST) else reached
        if (reached.isEmpty() && SOURCE_ROOTS.any(path::startsWith) && !path.startsWith("src/gametest/")) unreached += path
    }
    return Picked(picked, unreached)
}

private fun stem(path: String) = path.substringAfterLast('/').substringBeforeLast('.')

/** Every Kotlin and Java file of `main`, `debug` and `gametest` under [mod], by its path under it. */
fun gameTestSources(mod: File): Map<String, String> = SOURCE_ROOTS.flatMap { root ->
    mod.resolve(root).walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.map { it.relativeTo(mod).invariantSeparatorsPath to it.readText() }
}.toMap()

/** The name of every game test the game test mod lists. */
fun gameTestNames(mod: File): Set<String> =
    Json.parseToJsonElement(mod.resolve("src/gametest/resources/fabric.mod.json").readText()).jsonObject
        .getValue("entrypoints").jsonObject.getValue("fabric-client-gametest").jsonArray
        .map { it.jsonObject.getValue("value").jsonPrimitive.content.substringAfterLast('.') }.toSet()

/**
 * `./gradlew pickGameTests`, which a game test run with no `-Pgametests` runs first. Writes to [args]`[1]` the game
 * tests that the change since the last green run reaches, and the tree it read beside them, which the run keeps as
 * `passed` when it ends green. With no green run yet, the change is the one since the branch left `main`.
 */
fun main(args: Array<String>) {
    val mod = File(args[0])
    val out = File(args[1]).apply { parentFile.mkdirs() }
    val top = File(git(mod, "rev-parse", "--show-toplevel"))
    // A tree of the working copy, untracked files included, built in a copy of the index so the real one is untouched.
    val index = out.resolveSibling("index")
    File(git(top, "rev-parse", "--path-format=absolute", "--git-path", "index")).takeIf { it.exists() }?.copyTo(index, overwrite = true)
    val env = mapOf("GIT_INDEX_FILE" to index.path)
    git(top, "add", "-A", env = env)
    val tree = git(top, "write-tree", env = env)
    val passed = out.resolveSibling("passed").takeIf { it.exists() }?.readText()?.trim()
    val base = passed?.takeIf { runCatching { git(top, "cat-file", "-e", "$it^{tree}") }.isSuccess }
        ?: listOf("origin/main", "main").firstNotNullOf { runCatching { git(top, "merge-base", "HEAD", it) }.getOrNull() }
    val prefix = mod.canonicalFile.relativeTo(top.canonicalFile).invariantSeparatorsPath + "/"
    val changed = git(top, "diff", "--name-only", base, tree, "--", prefix).lines().filter(String::isNotBlank).map { it.removePrefix(prefix) }
    val picked = pickGameTests(gameTestSources(mod), gameTestNames(mod), changed).tests.sorted()
    out.writeText(picked.joinToString("\n"))
    out.resolveSibling("tree").writeText(tree)
    if (picked.isEmpty()) out.resolveSibling("passed").writeText(tree)
    println(if (picked.isEmpty()) "No game test reaches the change." else "Game tests the change reaches: ${picked.joinToString()}")
}

private fun git(dir: File, vararg args: String, env: Map<String, String> = emptyMap()): String {
    val process = ProcessBuilder("git", *args).directory(dir).redirectErrorStream(true).apply {
        // The pre-commit hook runs with git's own variables set, which would make the hook's directory the repository's top.
        environment().keys.removeAll(listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE"))
        environment() += env
    }.start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0) { "git ${args.joinToString(" ")} failed: $output" }
    return output
}
