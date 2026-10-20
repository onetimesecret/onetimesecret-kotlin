package com.onetimesecret.sdk.model

import com.onetimesecret.sdk.internal.LenientBooleanSerializer
import com.onetimesecret.sdk.internal.LenientLongSerializer
import com.onetimesecret.sdk.internal.StringListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The creator-facing record of a secret ("receipt"; older docs say "metadata"
 * or "private"). It tracks what happened to the secret without ever holding
 * the value.
 *
 * Timestamps are Unix epoch seconds. Fields the service did not send are
 * `null`; the SDK does not invent values.
 *
 * @property identifier The receipt's identifier.
 * @property key Same identifier under the field name older responses used.
 * @property shortId A short display form of the identifier.
 * @property state Lifecycle state.
 * @property createdAt Creation time (epoch seconds).
 * @property updatedAt Last update time (epoch seconds).
 * @property customerId The owning customer, when the secret was created by an account.
 * @property ownerId The owning account's identifier, when available.
 * @property sharedAt When the link was shared (epoch seconds).
 * @property previewedAt When the link was first opened without revealing (epoch seconds).
 * @property revealedAt When the value was revealed (epoch seconds).
 * @property burnedAt When the secret was burned (epoch seconds).
 * @property receivedAt When the secret was received (epoch seconds).
 * @property viewedAt When the secret was viewed (epoch seconds).
 * @property secretTtlSeconds Remaining time-to-live of the secret, in seconds.
 * @property receiptTtlSeconds Remaining time-to-live of this receipt, in seconds.
 * @property lifespan The original lifespan as a human-readable string.
 * @property secretIdentifier The identifier of the secret this receipt tracks, while it exists.
 * @property secretShortId Short display form of [secretIdentifier].
 * @property secretState State of the tracked secret, when reported.
 * @property recipients Email recipients the service notified, if any.
 * @property recipientName Display name for the recipient, if any.
 * @property shareDomain Custom domain the share link uses, if any.
 * @property hasPassphrase Whether a passphrase protects the secret.
 * @property memo Free-text note set by the creator (see `receipts.update`).
 * @property kind Whether the secret was concealed or generated.
 * @property source `standard` or `incoming`, when reported.
 * @property naturalExpiration Human-readable expiration (e.g. `"6 days"`).
 * @property expirationInSeconds Seconds until expiration, when reported.
 * @property expiresAt Expiration time (epoch seconds), when reported.
 * @property shareUrl Full URL of the secret link. Withheld (`null`) once the secret is gone.
 * @property receiptUrl Full URL of this receipt page.
 * @property burnUrl Full URL of the burn action page.
 * @property sharePath Path component of [shareUrl].
 * @property receiptPath Path component of [receiptUrl].
 * @property burnPath Path component of [burnUrl].
 */
@Serializable
public data class Receipt(
    val identifier: String,
    val key: String? = null,
    @SerialName("shortid") val shortId: String? = null,
    val state: ReceiptState = ReceiptState.UNKNOWN,
    @SerialName("created") @Serializable(with = LenientLongSerializer::class) val createdAt: Long? = null,
    @SerialName("updated") @Serializable(with = LenientLongSerializer::class) val updatedAt: Long? = null,
    @SerialName("custid") val customerId: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("shared") @Serializable(with = LenientLongSerializer::class) val sharedAt: Long? = null,
    @SerialName("previewed") @Serializable(with = LenientLongSerializer::class) val previewedAt: Long? = null,
    @SerialName("revealed") @Serializable(with = LenientLongSerializer::class) val revealedAt: Long? = null,
    @SerialName("burned") @Serializable(with = LenientLongSerializer::class) val burnedAt: Long? = null,
    @SerialName("received") @Serializable(with = LenientLongSerializer::class) val receivedAt: Long? = null,
    @SerialName("viewed") @Serializable(with = LenientLongSerializer::class) val viewedAt: Long? = null,
    @SerialName("secret_ttl") @Serializable(with = LenientLongSerializer::class) val secretTtlSeconds: Long? = null,
    @SerialName("receipt_ttl") @Serializable(with = LenientLongSerializer::class) val receiptTtlSeconds: Long? = null,
    val lifespan: String? = null,
    @SerialName("secret_identifier") val secretIdentifier: String? = null,
    @SerialName("secret_shortid") val secretShortId: String? = null,
    @SerialName("secret_state") val secretState: ReceiptState? = null,
    @Serializable(with = StringListSerializer::class) val recipients: List<String> = emptyList(),
    @SerialName("recipient_name") val recipientName: String? = null,
    @SerialName("share_domain") val shareDomain: String? = null,
    @SerialName(
        "has_passphrase",
    ) @Serializable(with = LenientBooleanSerializer::class) val hasPassphrase: Boolean? = null,
    @SerialName("is_previewed") @Serializable(with = LenientBooleanSerializer::class) val isPreviewed: Boolean? = null,
    @SerialName("is_revealed") @Serializable(with = LenientBooleanSerializer::class) val isRevealed: Boolean? = null,
    @SerialName("is_burned") @Serializable(with = LenientBooleanSerializer::class) val isBurned: Boolean? = null,
    @SerialName("is_destroyed") @Serializable(with = LenientBooleanSerializer::class) val isDestroyed: Boolean? = null,
    @SerialName("is_expired") @Serializable(with = LenientBooleanSerializer::class) val isExpired: Boolean? = null,
    @SerialName("is_orphaned") @Serializable(with = LenientBooleanSerializer::class) val isOrphaned: Boolean? = null,
    @SerialName("is_viewed") @Serializable(with = LenientBooleanSerializer::class) val isViewed: Boolean? = null,
    @SerialName("is_received") @Serializable(with = LenientBooleanSerializer::class) val isReceived: Boolean? = null,
    val memo: String? = null,
    val kind: SecretKind? = null,
    val source: String? = null,
    @SerialName("natural_expiration") val naturalExpiration: String? = null,
    @SerialName("expiration_in_seconds") @Serializable(with = LenientLongSerializer::class) val expirationInSeconds:
        Long? = null,
    @SerialName("expiration") @Serializable(with = LenientLongSerializer::class) val expiresAt: Long? = null,
    @SerialName("share_url") val shareUrl: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("burn_url") val burnUrl: String? = null,
    @SerialName("share_path") val sharePath: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
    @SerialName("burn_path") val burnPath: String? = null,
)

/**
 * What `conceal` and `generate` return: the receipt (keep this), the secret
 * record (give the link to the recipient), and ready-made URLs.
 *
 * @property receipt The creator-facing record.
 * @property secret The recipient-facing record. For `generate`, [Secret.value]
 *   carries the generated value so you can show it to the creator once.
 * @property shareDomain The custom domain the share link uses, if one was requested.
 * @property secretUrl The link to give the recipient.
 * @property receiptUrl The link to the creator's receipt page.
 */
public class ConcealResult internal constructor(
    public val receipt: Receipt,
    public val secret: Secret,
    public val shareDomain: String?,
    public val secretUrl: String,
    public val receiptUrl: String,
) {
    override fun toString(): String =
        "ConcealResult(receipt=${receipt.identifier}, secret=${secret.identifier}, secretUrl=$secretUrl)"
}
