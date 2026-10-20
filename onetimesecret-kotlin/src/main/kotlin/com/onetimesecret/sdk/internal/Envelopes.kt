package com.onetimesecret.sdk.internal

import com.onetimesecret.sdk.model.Receipt
import com.onetimesecret.sdk.model.Secret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * v2 wraps records in `{ record: {...}, details: {...} }` or
 * `{ records: [...], count: n }`. These wrappers are an implementation
 * detail; resources unwrap them before returning.
 */

@Serializable
internal class RecordEnvelope<T>(
    val record: T,
    val details: JsonObject? = null,
)

@Serializable
internal class RecordsEnvelope<T>(
    val records: List<T> = emptyList(),
    val count: Int? = null,
)

@Serializable
internal class ConcealRecord(
    val receipt: Receipt,
    val secret: Secret,
    @SerialName("share_domain") val shareDomain: String? = null,
)

@Serializable
internal class RevealDetails(
    @SerialName("is_owner") @Serializable(with = LenientBooleanSerializer::class) val isOwner: Boolean? = null,
)

@Serializable
internal class StatusDetails(
    @SerialName("current_expiration")
    @Serializable(with = LenientLongSerializer::class)
    val currentExpiration: Long? = null,
)
