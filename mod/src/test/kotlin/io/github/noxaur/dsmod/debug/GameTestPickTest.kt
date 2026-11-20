package io.github.noxaur.dsmod.debug

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GameTestPickTest {
    private val edge = "src/main/kotlin/io/github/noxaur/dsmod/edge"
    private val gametest = "src/gametest/kotlin/io/github/noxaur/dsmod/gametest"
    private val sources = mapOf(
        "$edge/Chat.kt" to "object Chat { fun send() = Lines.of() }",
        "$edge/Lines.kt" to "object Lines { fun of() = 1 }",
        "$edge/Lonely.kt" to "object Lonely",
        "$edge/mixin/ChatMixin.java" to "class ChatMixin { void at() { Chat.INSTANCE.send(); } }",
        "$gametest/ChatTest.kt" to "class ChatTest { fun run() = Chat.send() }",
        "$gametest/ChatAgainTest.kt" to "class ChatAgainTest { // as ChatTest, twice }",
        "$edge/mixin/RunMixin.java" to "class RunMixin { String test = \"ChatTest\"; }",
        "$gametest/HelperTest.kt" to "class HelperTest { fun run() = Spy.watch() }",
        "$gametest/Spy.kt" to "object Spy { fun watch() = 1 }",
        "src/main/kotlin/io/github/noxaur/dsmod/core/Game.kt" to "class Game",
    )
    private val tests = setOf("ChatTest", "ChatAgainTest", "HelperTest")

    private fun pick(vararg changed: String) = pickGameTests(sources, tests, changed.toList())

    @Test
    fun `a file reaches the tests that name it, and the tests that name what names it`() {
        assertEquals(Picked(setOf("ChatTest"), emptyList()), pick("$edge/Chat.kt"))
        assertEquals(Picked(setOf("ChatTest"), emptyList()), pick("$edge/Lines.kt"))
        assertEquals(Picked(setOf("HelperTest"), emptyList()), pick("$gametest/Spy.kt"))
        assertEquals(Picked(setOf("ChatTest"), emptyList()), pick("$gametest/ChatTest.kt"))
    }

    @Test
    fun `a mixin nothing names reaches what it names`() {
        assertEquals(Picked(setOf("ChatTest"), emptyList()), pick("$edge/mixin/ChatMixin.java"))
        assertEquals(Picked(setOf("ChatTest"), emptyList()), pick("$edge/mixin/RunMixin.java"))
    }

    @Test
    fun `the build and a deleted file run the smoke test, core runs none, and a file no test reaches is unreached`() {
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("build.gradle.kts"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("src/main/resources/fabric.mod.json"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("$edge/mixin/GoneMixin.java"))
        assertEquals(Picked(setOf(SMOKE_TEST), emptyList()), pick("src/gametest/resources/dsmod_gametest.mixins.json"))
        assertEquals(Picked(emptySet(), emptyList()), pick("src/main/kotlin/io/github/noxaur/dsmod/core/Game.kt"))
        assertEquals(Picked(emptySet(), listOf("$edge/Lonely.kt")), pick("$edge/Lonely.kt"))
    }

    @Test
    fun `a file most tests name runs the smoke test`() {
        val many = (1..13).associate { "$gametest/T${it}Test.kt" to "class T${it}Test { val x = Hub }" }
        val hubbed = sources + many + ("$edge/Hub.kt" to "object Hub")
        assertEquals(setOf(SMOKE_TEST), pickGameTests(hubbed, tests + many.keys.map { it.substringAfterLast('/').removeSuffix(".kt") }, listOf("$edge/Hub.kt")).tests)
    }

    @Test
    fun `a change that reaches more tests than a pick runs runs the smoke test`() {
        val wide = (1..MOST_PICKED + 1).flatMap { listOf("$edge/F$it.kt" to "object F$it", "$gametest/F${it}Test.kt" to "class F${it}Test { val x = F$it }") }.toMap()
        val names = wide.keys.filter { it.endsWith("Test.kt") }.map { it.substringAfterLast('/').removeSuffix(".kt") }.toSet()
        val edges = wide.keys.filterNot { it.endsWith("Test.kt") }
        assertEquals(setOf(SMOKE_TEST), pickGameTests(sources + wide, tests + names, edges).tests)
        assertEquals(MOST_PICKED, pickGameTests(sources + wide, tests + names, edges.take(MOST_PICKED)).tests.size)
    }

    @Test
    fun `every file of edge and debug reaches a game test`() {
        val mod = File(".")
        val sources = gameTestSources(mod)
        val unreached = pickGameTests(sources, gameTestNames(mod), sources.keys).unreached
        assertEquals(
            emptyList(),
            unreached,
            unreached.joinToString("\n", postfix = "\n") {
                "$it: reaches no game test, so a commit that changes it runs none. Write a game test that covers it, or " +
                    "name its class in the KDoc of the test that does."
            },
        )
    }
}
