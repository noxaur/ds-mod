package io.github.noxaur.dsmod.rules

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.streams.asSequence

fun releaseClasses(): List<ClassNode> {
    val classes = listOf("build/classes/kotlin/main", "build/classes/java/main").map(Path::of).filter { it.exists() }.flatMap { dir ->
        Files.walk(dir).asSequence().filter { it.extension == "class" }
            .map { ClassNode().also { node -> ClassReader(it.readBytes()).accept(node, ClassReader.SKIP_FRAMES) } }.toList()
    }
    check(classes.size > 100) { "Found ${classes.size} release classes. Build the main source set first." }
    return classes
}
