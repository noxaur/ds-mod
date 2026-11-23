package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.SettingsTree
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.superclasses
import kotlin.test.Test
import kotlin.test.assertEquals

private val leaves = setOf(String::class, Boolean::class, Char::class, Byte::class, Short::class, Int::class, Long::class, Float::class, Double::class)
private val readOnly = setOf(List::class, Set::class, Map::class, Collection::class, Pair::class, Triple::class)

fun savedProblems(default: Any, path: String): List<String> =
    problems(generateSequence(default::class) { it.superclasses.firstOrNull(KClass<*>::isSealed) }.last().starProjectedType, path, mutableSetOf())

private fun problems(type: KType, path: String, seen: MutableSet<KClass<*>>): List<String> {
    val kind = type.classifier as? KClass<*> ?: return listOf("$path is a type parameter")
    if (type.toString().startsWith("kotlin.collections.Mutable") || kind.java.isArray) return listOf("$path can change in place")
    if (kind in leaves || kind.java.isEnum) return emptyList()
    if (kind in readOnly) return type.arguments.flatMapIndexed { i, arg -> arg.type?.let { problems(it, "$path[$i]", seen) }.orEmpty() }
    if (!kind.java.name.startsWith("io.github.noxaur.")) return listOf("$path is a ${kind.qualifiedName}, which may change in place")
    if (!seen.add(kind)) return emptyList()
    if (kind.isSealed) return kind.sealedSubclasses.flatMap { problems(it.starProjectedType, "$path(${it.simpleName})", seen) }
    val descriptor = serializer(type).descriptor
    val missing = if (descriptor.kind != StructureKind.CLASS) emptyList() else (0 until descriptor.elementsCount)
        .filterNot(descriptor::isElementOptional).map { "$path.${descriptor.getElementName(it)} has no default" }
    return missing + kind.memberProperties.flatMap { property ->
        if (property is KMutableProperty1<*, *>) listOf("$path.${property.name} is a var")
        else problems(property.returnType, "$path.${property.name}", seen)
    }
}

@Serializable
private data class Inner(var count: Int = 0)

@Serializable
private data class Bad(val list: MutableList<Int> = mutableListOf(), val inner: Map<String, Inner> = emptyMap(), val noDefault: Int)

internal sealed interface Kept

internal sealed interface Unset : Kept

@Serializable
internal data object Empty : Unset

@Serializable
internal data class Full(val count: Int) : Kept

private val sameOnEveryProfile = mapOf(
    "hide-glass-panes" to "a hint told once, whichever profile the player is on",
    "storage-overview" to "a hint told once, whichever profile the player is on",
)

class SavedStateTest {
    @Test
    fun `a feature's state is kept for each profile, unless it is the same on every profile`() {
        val shared = SettingsTree().features.filter { it.state?.profiled == false }.map { it.id }.toSet()
        assertEquals(
            sameOnEveryProfile.keys,
            shared,
            "A feature's saved state is the player's own, so declare it with `feature.profiled(..)`, which keeps it for each " +
                "profile. Only a state that is the same on every profile, such as a hint told once, takes `feature.saved(..)`, " +
                "and then name its feature in `sameOnEveryProfile` in this test with why.\n",
        )
    }

    @Test
    fun `every saved state is immutable and has a default for every field`() {
        val problems = SettingsTree().features.flatMap { feature ->
            feature.state?.value?.let { savedProblems(it, feature.id) }.orEmpty()
        }
        assertEquals(
            emptyList(),
            problems,
            "A feature's saved state must be immutable, with a default for every field. Make each field a `val` of a " +
                "read-only type and change the state by replacing `value` with a copy, and give each field a default, " +
                "so a save from before the field existed still loads.\n" + problems.joinToString("\n") + "\n",
        )
    }

    @Test
    fun `the walk catches a mutable collection, a nested var and a field with no default, in any case of a sealed state`() {
        assertEquals(
            setOf("bad.noDefault has no default", "bad.inner[1].count is a var", "bad.list can change in place"),
            savedProblems(Bad(noDefault = 0), "bad").toSet(),
        )
        assertEquals(listOf("kept(Full).count has no default"), savedProblems(Empty, "kept"))
    }
}
