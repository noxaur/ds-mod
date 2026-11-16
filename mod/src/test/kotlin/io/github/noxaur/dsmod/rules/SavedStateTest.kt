package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.features
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

// Values that cannot change, and the read-only collections, whose elements are checked in turn.
private val leaves = setOf(String::class, Boolean::class, Char::class, Byte::class, Short::class, Int::class, Long::class, Float::class, Double::class)
private val readOnly = setOf(List::class, Set::class, Map::class, Collection::class, Pair::class, Triple::class)

/**
 * What in the state whose default is [default], named [path], a save could lose: a value that changes in place,
 * which `Saved` never writes because only replacing its `value` marks it changed, and a field with no default,
 * which a save written before the field existed lacks, so it would not load. A default that is one case of a sealed
 * state stands for all of them.
 */
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

class SavedStateTest {
    @Test
    fun `every saved state is immutable and has a default for every field`() {
        val problems = features(DsModConfig()).flatMap { feature ->
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
