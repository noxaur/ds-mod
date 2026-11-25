package io.github.noxaur.dragen.rules

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object Clones {
    const val RUN = 40
    const val SHORT_RUN = 25
    const val LINE = 30
    const val CHANGED = 0.15

    const val FIX = "This repeats code with only a value or two changed. Pull the shared part into one function or " +
        "variable that takes what differs, loop over the values, or call the helper that already does it. A `when` " +
        "or an `if` whose branches make the same call picks only what differs, then makes the call once."

    private val token = Regex(
        """//[^\n]*|/\*.*?\*/|"{3}.*?"{3}|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\])'|0x[0-9A-Fa-f_]+|""" +
            """\d[\d_]*(?:\.\d+)?[fFLuU]?|[A-Za-z_]\w*|->|\?\.|\?:|::|\.\.|[=!<>]=|&&|\|\||\S""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val header = Regex("""(?m)^(package|import) .*$""")
    private val exception = Regex("""rule:clone \S""")

    class Token(val text: String, val line: Int)

    data class At(val file: String, val line: Int) {
        override fun toString() = "$file:$line"
    }

    fun tokens(source: String): List<Token> {
        val text = header.replace(source, "")
        val raw = ArrayList<Token>()
        var line = 1
        var from = 0
        for (match in token.findAll(text)) {
            for (i in from until match.range.first) if (text[i] == '\n') line++
            from = match.range.first
            if (!match.value.startsWith("//") && !match.value.startsWith("/*")) raw += Token(match.value, line)
        }
        val out = ArrayList<Token>(raw.size)
        var i = 0
        while (i < raw.size) {
            out += raw[i]
            if (raw[i].text != "fun" && raw[i].text != "class" && raw[i].text != "constructor") {
                i++
                continue
            }
            var j = i + 1
            while (j < raw.size && raw[j].text != "(" && raw[j].text != "{" && raw[j].text != "=") out += raw[j++]
            if (j < raw.size && raw[j].text == "(") {
                var depth = 0
                while (j < raw.size) {
                    if (raw[j].text == "(") depth++
                    if (raw[j].text == ")" && --depth == 0) break
                    j++
                }
                out += Token("()", raw[i].line)
                j++
            }
            i = j
        }
        return out
    }

    fun find(sources: Map<String, String>): List<List<At>> {
        val files = sources.mapValues { tokens(it.value) }
        val lines = sources.mapValues { it.value.lines() }
        val copies = runs(files, RUN, 2) + runs(files, SHORT_RUN, 3) + nearLines(files)
        return copies.filter { at -> at.none { exception.containsMatchIn(lines.getValue(it.file).getOrElse(it.line - 1) { "" }) } }
    }

    private fun runs(files: Map<String, List<Token>>, size: Int, times: Int): List<List<At>> {
        val windows = LinkedHashMap<List<String>, MutableList<Pair<String, Int>>>()
        for ((file, tokens) in files) {
            val texts = tokens.map { it.text }
            for (i in 0..texts.size - size) windows.getOrPut(texts.subList(i, i + size)) { ArrayList(2) } += file to i
        }
        val found = ArrayList<List<At>>()
        val continued = HashSet<Pair<String, Int>>()
        for (places in windows.values) {
            if (places.size < times) continue
            val apart = ArrayList<Pair<String, Int>>()
            for (place in places) if (apart.none { it.first == place.first && place.second - it.second < size }) apart += place
            if (apart.size < times) continue
            val known = apart.any { it in continued }
            apart.forEach { continued += it.first to it.second + 1 }
            if (!known) found += apart.map { (file, i) -> At(file, files.getValue(file)[i].line) }
        }
        return found
    }

    private fun nearLines(files: Map<String, List<Token>>): List<List<At>> {
        val found = ArrayList<List<At>>()
        for ((file, tokens) in files) {
            val long = tokens.groupBy { it.line }.mapValues { (_, line) -> line.map { it.text } }.filterValues { it.size >= LINE }.toList()
            for (a in long.indices) for (b in a + 1 until long.size) {
                val (lineA, textA) = long[a]
                val (lineB, textB) = long[b]
                val cap = (maxOf(textA.size, textB.size) * CHANGED).toInt()
                if (distance(textA, textB, cap) <= cap) found += listOf(At(file, lineA), At(file, lineB))
            }
        }
        return found
    }

    private fun distance(a: List<String>, b: List<String>, cap: Int): Int {
        if (kotlin.math.abs(a.size - b.size) > cap) return cap + 1
        var previous = IntArray(b.size + 1) { it }
        var current = IntArray(b.size + 1)
        for (i in 1..a.size) {
            current[0] = i
            var best = i
            for (j in 1..b.size) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                best = minOf(best, current[j])
            }
            if (best > cap) return cap + 1
            previous = current.also { current = previous }
        }
        return previous[b.size]
    }
}

class ClonesTest {
    @Test
    fun `no code is copied with a value changed`() {
        val root = Path.of("").toAbsolutePath()
        val sources = listOf("src/main", "src/debug", "src/test", "src/gametest").flatMap { dir ->
            Files.walk(root.resolve(dir)).asSequence().filter { it.isRegularFile() && it.extension == "kt" }.toList()
        }.associate { it.relativeTo(root).invariantSeparatorsPathString to it.readText() }

        val copies = Clones.find(sources).map { at -> "${at.first()}: [clone] copied at ${at.drop(1).joinToString()}. ${Clones.FIX}" }

        assertEquals(
            emptyList(),
            copies,
            "Fix each copy, or end one of its lines with a comment `rule:clone <reason>` if the user agreed to an exception.\n" +
                copies.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `a branch copied with one value changed is found`() {
        val source = """
            when (cell) {
                is Cell.Pick<T, *> -> put(label + cell.width) { l, hx, dy -> Hit(l, hx + label, dy + 1, cell.width, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.name) }
                is Cell.Count -> put(label + COUNT) { l, hx, dy -> Hit(l, hx + label, dy + 1, COUNT, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.name) }
                is Cell.Text -> put(label + cell.width) { l, hx, dy -> Hit(l, hx + label, dy + 1, cell.width, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.name) }
            }
        """.trimIndent()

        assertTrue(Clones.find(mapOf("A.kt" to source)).isNotEmpty())
    }

    @Test
    fun `a chunk repeated three times is found`() {
        val source = """
            val essence = halves("Essence", dungeons.essence.map { Row(body(it.name), listOf(text { value(amount(it.amount)) }.toComponent())) })
            val nodes = table("Nodes", hotd.nodes.map { Row(body(it.name), listOf(text { value(amount(it.level)) }.toComponent())) }, empty = "No nodes yet.")
            val meters = table("RNG Meters", shown.rngMeters.map { Row(body(it.name), listOf(text { value(amount(it.progress)) }.toComponent())) })
        """.trimIndent()

        assertTrue(Clones.find(mapOf("A.kt" to source)).isNotEmpty())
    }

    @Test
    fun `short branches, repeated signatures and an agreed exception pass`() {
        val source = """
            fun widthOf(cell: Cell<T>) = when (cell) {
                is Cell.Text -> cell.width
                is Cell.Keys -> cell.width
                is Cell.Check -> Kit.SWITCH_WIDTH
            }
            override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double, shift: Boolean, alt: Boolean): Boolean = a()
            override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double, shift: Boolean, alt: Boolean): Boolean = b()
            val one = put(label + cell.width) { l, hx, dy -> Hit(l, hx + label, dy + 1, cell.width, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.name) } // rule:clone the user agreed
            val two = put(label + cell.width) { l, hx, dy -> Hit(l, hx + label, dy + 1, cell.width, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.text) }
        """.trimIndent()

        assertEquals(emptyList(), Clones.find(mapOf("A.kt" to source)))
    }
}
