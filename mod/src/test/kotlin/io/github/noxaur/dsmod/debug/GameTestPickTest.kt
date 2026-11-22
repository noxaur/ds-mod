package io.github.noxaur.dsmod.debug

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTestPickTest {
    private fun path(name: String) = when {
        name.startsWith("gametest/") -> "src/gametest/kotlin/io/github/noxaur/dsmod/$name.kt"
        name.endsWith("Mixin") -> "src/main/java/io/github/noxaur/dsmod/$name.java"
        else -> "src/main/kotlin/io/github/noxaur/dsmod/$name.kt"
    }

    private fun test(name: String) = "io.github.noxaur.dsmod.$name".replace('/', '.')

    private fun sources(vararg named: Pair<String, List<String>>) = Sources(
        named.toMap().mapValues { it.value.toSet() },
        named.associate { (name, _) -> name to path(name.substringBefore('$')) },
        named.map { it.first }.filter { it.startsWith("gametest/") && it.endsWith("Test") }.associateWith(::test),
        setOf(path("debug/Tool")),
    )

    private val sources = sources(
        "edge/Chat" to listOf("edge/Lines"),
        "edge/Lines" to emptyList(),
        "edge/Lonely" to listOf("edge/Lines"),
        "edge/Feature" to listOf("core/Counter"),
        "edge/mixin/ChatMixin" to listOf("edge/Chat"),
        "edge/mixin/FeatureMixin" to listOf("edge/Feature"),
        "core/Counter" to emptyList(),
        "gametest/ChatTest" to listOf("edge/Chat"),
        "gametest/HelperTest" to listOf("gametest/Spy"),
        "gametest/CounterTest" to listOf("core/Counter"),
        "gametest/Spy" to listOf("edge/Lines"),
        "debug/Tool" to emptyList(),
    )

    private fun pick(vararg changed: String) = pickGameTests(sources, changed.toList())

    @Test
    fun `a file reaches the tests that name its classes, and the tests that name what names them, nearest first`() {
        assertEquals(Picked(setOf(test("gametest/ChatTest")), emptyList()), pick(path("edge/Chat")))
        assertEquals(Picked(setOf(test("gametest/HelperTest")), emptyList()), pick(path("gametest/Spy")))
        assertEquals(Picked(setOf(test("gametest/ChatTest"), test("gametest/HelperTest")), emptyList()), pick(path("edge/Lines")))
        assertEquals(Picked(setOf(test("gametest/ChatTest")), emptyList()), pick(path("gametest/ChatTest")))
    }

    @Test
    fun `a file no test reaches reaches the tests of the most particular class it names`() {
        assertEquals(Picked(setOf(test("gametest/ChatTest")), emptyList()), pick(path("edge/mixin/ChatMixin")))
        assertEquals(Picked(setOf(test("gametest/CounterTest")), emptyList()), pick(path("edge/Feature")))
        assertEquals(Picked(setOf(test("gametest/CounterTest")), emptyList()), pick(path("edge/mixin/FeatureMixin")))
    }

    @Test
    fun `the build and a deleted file run the smoke test, core, unit tests and tools run none, and a file no test reaches is unreached`() {
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("build.gradle.kts"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("src/main/resources/fabric.mod.json"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("src/gametest/fabric.mod.json"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick(path("edge/mixin/GoneMixin")))
        assertEquals(Picked(emptySet(), emptyList()), pick(path("core/Counter")))
        assertEquals(Picked(emptySet(), emptyList()), pick("src/test/kotlin/io/github/noxaur/dsmod/ChatTest.kt"))
        assertEquals(Picked(emptySet(), emptyList()), pick(path("debug/Tool")))
        val lonely = sources(*sources.names.map { it.key to it.value.toList() }.toTypedArray(), "edge/Alone" to emptyList())
        assertEquals(Picked(emptySet(), listOf(path("edge/Alone"))), pickGameTests(lonely, listOf(path("edge/Alone"))))
    }

    @Test
    fun `a class most tests name is a hub, which a change runs the smoke test for and a walk stops at`() {
        val many = (1..13).map { "gametest/T${it}Test" to listOf("edge/Hub") }
        val hubbed = sources(*many.toTypedArray(), "edge/Hub" to listOf("edge/Under"), "edge/Under" to emptyList(), "gametest/UnderTest" to listOf("edge/Under"))
        assertEquals(setOf(SMOKE_TEST), pickGameTests(hubbed, listOf(path("edge/Hub"))).tests)
        assertEquals(setOf(test("gametest/UnderTest")), pickGameTests(hubbed, listOf(path("edge/Under"))).tests)
    }

    @Test
    fun `a change reaching more tests than a pick runs takes the nearest that fit, or the smoke test`() {
        val wide = (1..MOST_PICKED + 1).map { "gametest/F${it}Test" to listOf("edge/Wide") } +
            ("edge/Wide" to emptyList()) + ("edge/Narrow" to emptyList()) + ("gametest/NarrowTest" to listOf("edge/Narrow")) +
            ("gametest/Far" to listOf("edge/Narrow")) + (1..MOST_PICKED).map { "gametest/G${it}Test" to listOf("gametest/Far") }
        val spread = sources(*wide.toTypedArray())
        assertEquals(setOf(SMOKE_TEST), pickGameTests(spread, listOf(path("edge/Wide"))).tests)
        assertEquals(setOf(test("gametest/NarrowTest")), pickGameTests(spread, listOf(path("edge/Narrow"))).tests)
        val each = (1..MOST_PICKED + 1).flatMap { listOf("edge/E$it" to emptyList(), "gametest/E${it}Test" to listOf("edge/E$it")) }
        val apart = sources(*each.toTypedArray())
        assertEquals(setOf(SMOKE_TEST), pickGameTests(apart, (1..MOST_PICKED + 1).map { path("edge/E$it") }).tests)
        assertEquals(MOST_PICKED, pickGameTests(apart, (1..MOST_PICKED).map { path("edge/E$it") }).tests.size)
    }

    private val real by lazy { readSources(File(".")) }

    @Test
    fun `the game tests are the concrete classes that run as one`() {
        assertTrue(test("gametest/ProfileTourTest") in real.tests.values)
        assertTrue(test("gametest/ProfileTour") !in real.tests.values)
        assertTrue(SMOKE_TEST in real.tests.values)
    }

    @Test
    fun `every file of edge and debug reaches a game test`() {
        val unreached = pickGameTests(real, real.files.values.toSet()).unreached
        assertEquals(
            emptyList(),
            unreached,
            unreached.joinToString("\n", postfix = "\n") {
                "$it: reaches no game test, so a commit that changes it runs none. Write a game test whose code names one of its classes, " +
                    "or one of the classes it names."
            },
        )
    }
}
