package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** @keep The edge resolves legacy color codes and inherited styles before it builds one, so plain holds no formatting
 * codes and every ServerSpan carries its final style */
@Serializable(with = TextSerializer::class)
data class ServerText(val spans: List<ServerSpan>) {
    constructor(vararg spans: ServerSpan) : this(spans.asList())

    val plain: String = spans.joinToString("") { it.text }
}

@Serializable
data class ServerSpan(
    val text: String,
    @Serializable(with = HexColor::class) val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
)

@Serializable
private class TextJson(val plain: String, val spans: List<ServerSpan>)

internal object TextSerializer : KSerializer<ServerText> {
    override val descriptor: SerialDescriptor = SerialDescriptor("io.github.noxaur.dsmod.Text", TextJson.serializer().descriptor)

    override fun serialize(encoder: Encoder, value: ServerText) =
        encoder.encodeSerializableValue(TextJson.serializer(), TextJson(value.plain, value.spans))

    override fun deserialize(decoder: Decoder) = ServerText(decoder.decodeSerializableValue(TextJson.serializer()).spans)
}

internal object HexColor : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.noxaur.dsmod.HexColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString("#%06X".format(value))

    override fun deserialize(decoder: Decoder) = decoder.decodeString().removePrefix("#").toInt(16)
}
