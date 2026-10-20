package com.onetimesecret.sdk.model

import com.onetimesecret.sdk.internal.LenientBooleanSerializer
import com.onetimesecret.sdk.internal.LenientLongSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The recipient-facing record of a secret: metadata, and the value itself
 * once it has been revealed.
 *
 * Timestamps are Unix epoch seconds. Fields the service did not send are
 * `null`; the SDK does not invent values.
 *
 * @property identifier The secret's identifier (what the share link contains).
 * @property key Same identifier under the field name older responses used.
 * @property shortId A short display form of the identifier.
 * @property state Lifecycle state.
 * @property createdAt Creation time (epoch seconds).
 * @property updatedAt Last update time (epoch seconds).
 * @property hasPassphrase Whether a passphrase is required to reveal.
 * @property value The secret content. Only present on a successful reveal.
 * @property displayType How [value] should be rendered.
 * @property ttlSeconds The remaining time-to-live reported by the service, in seconds.
 * @property lifespan The original lifespan as a human-readable string (e.g. `"7 days"`).
 * @property isPreviewed Whether the link has been opened without revealing.
 * @property isRevealed Whether the value has been revealed (and therefore destroyed).
 * @property isViewed Whether the secret has been viewed.
 * @property isReceived Whether the recipient has received it.
 * @property verification Internal verification marker, exposed as sent.
 */
@Serializable
public data class Secret(
    val identifier: String,
    val key: String? = null,
    @SerialName("shortid") val shortId: String? = null,
    val state: SecretState = SecretState.UNKNOWN,
    @SerialName("created") @Serializable(with = LenientLongSerializer::class) val createdAt: Long? = null,
    @SerialName("updated") @Serializable(with = LenientLongSerializer::class) val updatedAt: Long? = null,
    @SerialName(
        "has_passphrase",
    ) @Serializable(with = LenientBooleanSerializer::class) val hasPassphrase: Boolean? = null,
    @SerialName("secret_value") val value: String? = null,
    @SerialName("display_type") val displayType: DisplayType = DisplayType.TEXT,
    @SerialName("secret_ttl") @Serializable(with = LenientLongSerializer::class) val ttlSeconds: Long? = null,
    val lifespan: String? = null,
    @SerialName("is_previewed") @Serializable(with = LenientBooleanSerializer::class) val isPreviewed: Boolean? = null,
    @SerialName("is_revealed") @Serializable(with = LenientBooleanSerializer::class) val isRevealed: Boolean? = null,
    @SerialName("is_viewed") @Serializable(with = LenientBooleanSerializer::class) val isViewed: Boolean? = null,
    @SerialName("is_received") @Serializable(with = LenientBooleanSerializer::class) val isReceived: Boolean? = null,
    val verification: String? = null,
) {
    /** Redacts [value] so a logged `Secret` never leaks its content. */
    override fun toString(): String =
        "Secret(identifier=$identifier, state=$state, hasPassphrase=$hasPassphrase, " +
            "displayType=$displayType, ttlSeconds=$ttlSeconds, value=${if (value == null) "null" else "****"})"
}

/**
 * The result of revealing a secret: the content plus the record it came from.
 *
 * Revealing is a one-time, destructive action. Treat [value] as the only copy.
 */
public class RevealedSecret internal constructor(
    public val value: String,
    public val secret: Secret,
    /** `true` when the service reports the caller as the secret's owner. */
    public val isOwner: Boolean?,
) {
    /** Redacts [value]. */
    override fun toString(): String = "RevealedSecret(secret=$secret, value=****)"
}

/**
 * Status of a single secret, from `GET /secret/{identifier}/status`.
 *
 * @property identifier The identifier that was checked.
 * @property state The current state; [SecretState.UNKNOWN] when the service does not know the identifier.
 * @property secret The full record when the service knows the identifier.
 * @property expiresAt Expiration time (epoch seconds), when known.
 */
public class SecretStatus internal constructor(
    public val identifier: String,
    public val state: SecretState,
    public val secret: Secret?,
    public val expiresAt: Long?,
) {
    /** Convenience: `true` while the secret can still be revealed. */
    public val isAvailable: Boolean
        get() = state == SecretState.NEW || state == SecretState.PREVIEWED

    override fun toString(): String = "SecretStatus(identifier=$identifier, state=$state, expiresAt=$expiresAt)"
}

/** Options for server-side password generation (`generate`). `null` fields use the service defaults. */
@Serializable
public data class CharacterSets(
    val uppercase: Boolean? = null,
    val lowercase: Boolean? = null,
    val numbers: Boolean? = null,
    val symbols: Boolean? = null,
    @SerialName("exclude_ambiguous") val excludeAmbiguous: Boolean? = null,
)
