package com.onetimesecret.sdk.model

import com.onetimesecret.sdk.internal.DottedVersionSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /status`. Works without credentials. */
@Serializable
public data class SystemStatus(
    val success: Boolean = true,
    /** The service's own status word, e.g. `"nominal"`. */
    val status: String,
    /** Locale the service resolved for this request. */
    val locale: String? = null,
)

/** Response of `GET /version`. Requires credentials. */
@Serializable
public data class SystemVersion(
    val success: Boolean = true,
    /** Version as a dotted string (the API sends an array of components). */
    @Serializable(with = DottedVersionSerializer::class) val version: String,
    val locale: String? = null,
)

/** Response of `GET /supported-locales`. Works without credentials. */
@Serializable
public data class SupportedLocales(
    val success: Boolean = true,
    val locales: List<String> = emptyList(),
    @SerialName("default_locale") val defaultLocale: String? = null,
    val locale: String? = null,
)
