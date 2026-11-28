package io.github.noxaur.dragen.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.nio.file.Path
import kotlin.io.path.exists

fun sourceSet(name: String) = describe<JavaClass>("in the $name source set") { type ->
    type.source.map { Regex("/build/classes/(kotlin|java)/$name/") in it.uri.path }.orElse(false)
}

val everyClass: JavaClasses by lazy {
    val dirs = listOf("api", "main", "debug", "gametest").flatMap { listOf("build/classes/kotlin/$it", "build/classes/java/$it") }
        .map(Path::of).filter { it.exists() }
    ClassFileImporter().importPaths(dirs).also { check(it.size > 100) { "Found ${it.size} classes in api, main, debug and gametest. Build them first." } }
}

val modClasses: JavaClasses by lazy { everyClass.that(sourceSet("api").or(sourceSet("main")).or(sourceSet("debug"))) }

val releaseClasses: JavaClasses by lazy { everyClass.that(sourceSet("api").or(sourceSet("main"))) }

fun fixture(type: Class<*>): JavaClasses = ClassFileImporter()
    .withImportOption { it.contains("/${type.simpleName}.class") || it.contains("/${type.simpleName}$") }
    .importPackagesOf(type)

fun outside(vararg files: String): DescribedPredicate<JavaClass> = describe("outside ${files.joinToString()}") { type ->
    val file = type.source.flatMap { it.fileName }.orElse("")
    "${type.packageName.removePrefix("io.github.noxaur.dragen.").replace('.', '/')}/$file" !in files
}

fun flags(rule: (DescribedPredicate<JavaClass>) -> com.tngtech.archunit.lang.ArchRule, vararg types: Class<*>) =
    types.filter { rule(DescribedPredicate.alwaysTrue()).evaluate(fixture(it)).hasViolation() }
