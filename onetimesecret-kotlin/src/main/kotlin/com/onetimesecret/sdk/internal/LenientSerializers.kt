package com.onetimesecret.sdk.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * The v2 API grew out of a form-encoded v1 and still serialises many scalars
 * as strings: booleans arrive as "true"/"false", timestamps as "1726000000" or
 * 1726000000.5, and list-ish fields as either an array or a single string.
 * These serializers accept every shape the OpenAPI document declares and
 * normalise to the Kotlin type callers expect. They only decode; the SDK never
 * encodes these types.
 */

private fun Decoder.asJsonDecoder(): JsonDecoder =
    this as? JsonDecoder ?: throw SerializationException("This serializer only supports JSON")

/** Boolean from `true`, `"true"`, `"1"`, `1`, and their false counterparts; `""`/`null` → null. */
internal object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val element = decoder.asJsonDecoder().decodeJsonElement()
        if (element is JsonNull || element !is JsonPrimitive) return null
        element.booleanOrNull?.let { return it }
        return when (element.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: Boolean?,
    ) = encoder.encodeNullableSerializableValue(Boolean.serializer(), value)
}

/** Long from a JSON number (fractions truncated) or a numeric string; anything else → null. */
internal object LenientLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long? {
        val element = decoder.asJsonDecoder().decodeJsonElement()
        if (element is JsonNull || element !is JsonPrimitive) return null
        element.longOrNull?.let { return it }
        element.doubleOrNull?.let { return it.toLong() }
        return element.content.trim().toLongOrNull() ?: element.content
            .trim()
            .toDoubleOrNull()
            ?.toLong()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: Long?,
    ) = encoder.encodeNullableSerializableValue(Long.serializer(), value)
}

/** `List<String>` from an array, a single string, or null (→ empty list). */
internal object StringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        when (val element = decoder.asJsonDecoder().decodeJsonElement()) {
            is JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }

    override fun serialize(
        encoder: Encoder,
        value: List<String>,
    ) = delegate.serialize(encoder, value)
}

/** The `version` endpoint returns `[2026, 10, "15"]`-style arrays; join them as a dotted string. */
internal object DottedVersionSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DottedVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        when (val element = decoder.asJsonDecoder().decodeJsonElement()) {
            is JsonArray -> element.joinToString(".") { it.jsonPrimitive.content }
            is JsonPrimitive -> element.content
            else -> ""
        }

    override fun serialize(
        encoder: Encoder,
        value: String,
    ) = encoder.encodeString(value)
}

/**
 * Enum serializer that maps unknown wire values to a fallback constant instead
 * of failing, so a new server-side state never breaks a shipped app.
 */
internal open class FallbackEnumSerializer<T : Enum<T>>(
    serialName: String,
    private val values: Array<T>,
    private val fallback: T,
    private val wireName: (T) -> String,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val element = decoder.asJsonDecoder().decodeJsonElement()
        val raw = (element as? JsonPrimitive)?.content?.trim()?.lowercase() ?: return fallback
        return values.firstOrNull { wireName(it) == raw } ?: fallback
    }

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = encoder.encodeString(wireName(value))
}
