package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.nio.file.Path
import kotlin.io.path.exists

val modClasses: JavaClasses by lazy {
    val dirs = listOf("main", "debug").flatMap { listOf("build/classes/kotlin/$it", "build/classes/java/$it") }.map(Path::of).filter { it.exists() }
    ClassFileImporter().importPaths(dirs).also { check(it.size > 100) { "Found ${it.size} classes in main and debug. Build them first." } }
}

val releaseClasses: JavaClasses by lazy {
    modClasses.that(describe("in the release source set") { type -> type.source.map { "/main/" in it.uri.path }.orElse(false) })
}

fun fixture(type: Class<*>): JavaClasses = ClassFileImporter()
    .withImportOption { it.contains("/${type.simpleName}.class") || it.contains("/${type.simpleName}$") }
    .importPackagesOf(type)

fun outside(vararg files: String): DescribedPredicate<JavaClass> = describe("outside ${files.joinToString()}") { type ->
    val file = type.source.flatMap { it.fileName }.orElse("")
    "${type.packageName.removePrefix("io.github.noxaur.dsmod.").replace('.', '/')}/$file" !in files
}

fun flags(rule: (DescribedPredicate<JavaClass>) -> com.tngtech.archunit.lang.ArchRule, vararg types: Class<*>) =
    types.filter { rule(DescribedPredicate.alwaysTrue()).evaluate(fixture(it)).hasViolation() }
