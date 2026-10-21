package com.onetimesecret.sdk

import com.onetimesecret.sdk.internal.ConcealRecord
import com.onetimesecret.sdk.internal.HttpTransport
import com.onetimesecret.sdk.internal.Identifiers
import com.onetimesecret.sdk.internal.ReceiptDetails
import com.onetimesecret.sdk.internal.RecordEnvelope
import com.onetimesecret.sdk.internal.RecordsEnvelope
import com.onetimesecret.sdk.internal.RevealDetails
import com.onetimesecret.sdk.internal.StatusDetails
import com.onetimesecret.sdk.model.CharacterSets
import com.onetimesecret.sdk.model.ConcealResult
import com.onetimesecret.sdk.model.DisplayType
import com.onetimesecret.sdk.model.RevealedSecret
import com.onetimesecret.sdk.model.Secret
import com.onetimesecret.sdk.model.SecretState
import com.onetimesecret.sdk.model.SecretStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import kotlin.time.Duration

/**
 * Secret operations: create (`conceal`, `generate`), consume (`reveal`), and
 * inspect (`show`, `status`). Reached through [OnetimeSecretClient.secrets].
 *
 * Every method is a `suspend` function that throws an [OnetimeSecretException]
 * subtype on failure; see that class for the hierarchy.
 */
public class Secrets internal constructor(
    private val transport: HttpTransport,
    private val baseUrl: HttpUrl,
) {
    /**
     * Conceal a secret value you already have. Returns the receipt (keep it)
     * and the secret record (give [ConcealResult.secretUrl] to the recipient).
     *
     * @param secret The content to share. Must not be empty.
     * @param ttl How long the secret may live before it expires unrevealed.
     *   `null` uses the service default for your account.
     * @param passphrase A passphrase the recipient must enter to reveal.
     * @param recipient An email address the service notifies with the link.
     * @param shareDomain A verified custom domain the link should use.
     * @param displayType How the value should be rendered when revealed.
     * @param guest Use the anonymous `/guest/` route instead of the account route.
     *   The account route also accepts anonymous calls on most deployments;
     *   the guest route is the one self-hosted operators can switch off.
     * @throws AccountRequiredException when the deployment requires an account for this call.
     * @throws BadRequestException when the service rejects a parameter.
     */
    public suspend fun conceal(
        secret: String,
        ttl: Duration? = null,
        passphrase: String? = null,
        recipient: String? = null,
        shareDomain: String? = null,
        displayType: DisplayType? = null,
        guest: Boolean = false,
    ): ConcealResult {
        require(secret.isNotEmpty()) { "secret must not be empty" }
        val body =
            buildJsonObject {
                putNested("secret") {
                    put("secret", secret)
                    putCommon(ttl, passphrase, recipient, shareDomain, displayType)
                }
            }
        val envelope =
            transport.post(
                route(guest, "secret", "conceal"),
                body = body,
                deserializer = RecordEnvelope.serializer(ConcealRecord.serializer()),
            )
        return toConcealResult(envelope.record)
    }

    /**
     * Have the service generate a random secret value (for example a
     * password). The recipient gets it through the link as usual; the creator
     * can see it once, in [ConcealResult.value].
     *
     * The create response never contains the value. The service discloses it
     * exactly once, on the first view of the receipt while it is still new and
     * within a short window after creation (60 seconds by default). With
     * [claimValue] `true` this method fetches the receipt immediately and
     * returns the value; after that, nobody but the recipient can see it. If
     * that follow-up request fails the secret still exists and the links are
     * valid, so the result is returned with [ConcealResult.value] `null` and
     * the cause in [ConcealResult.claimFailure] rather than thrown.
     *
     * @param length Length of the generated value. `null` uses the service default.
     * @param characterSets Which character classes to draw from. `null` uses the service default.
     * @param claimValue Fetch the receipt once to obtain the value. Pass
     *   `false` to leave the one-time disclosure for your own first
     *   `receipts.show` call instead.
     * @see conceal for the remaining parameters.
     */
    public suspend fun generate(
        ttl: Duration? = null,
        passphrase: String? = null,
        recipient: String? = null,
        shareDomain: String? = null,
        displayType: DisplayType? = null,
        length: Int? = null,
        characterSets: CharacterSets? = null,
        guest: Boolean = false,
        claimValue: Boolean = true,
    ): ConcealResult {
        require(length == null || length > 0) { "length must be positive" }
        val body =
            buildJsonObject {
                putNested("secret") {
                    putCommon(ttl, passphrase, recipient, shareDomain, displayType)
                    length?.let { put("length", it) }
                    characterSets?.let { put("character_sets", transport.json.encodeToJsonElement(it)) }
                }
            }
        val envelope =
            transport.post(
                route(guest, "secret", "generate"),
                body = body,
                deserializer = RecordEnvelope.serializer(ConcealRecord.serializer()),
            )
        val created = toConcealResult(envelope.record)
        if (!claimValue) return created
        return try {
            created.withValue(claimGeneratedValue(guest, created.receipt.identifier))
        } catch (e: OnetimeSecretException) {
            created.withClaimFailure(e)
        }
    }

    /**
     * Reveal (consume) a secret. **This is a one-time, destructive action**:
     * the value is returned to exactly one caller and then destroyed.
     *
     * @param keyOrUrl The secret identifier, or the full link the recipient was given.
     * @param passphrase Required when the secret was created with one.
     * @param guest Use the anonymous `/guest/` route (see [conceal]).
     * @throws NotFoundException when the secret does not exist or has already been revealed, burned or expired.
     * @throws BadRequestException when the passphrase is wrong (the service
     *   reports this as a form error; the message is localized).
     * @throws SecretNotRevealedException when the service accepted the request
     *   but another request consumed the secret first.
     */
    public suspend fun reveal(
        keyOrUrl: String,
        passphrase: String? = null,
        guest: Boolean = false,
    ): RevealedSecret {
        val identifier = Identifiers.secret(keyOrUrl)
        val body =
            buildJsonObject {
                put("continue", true)
                passphrase?.let { put("passphrase", it) }
            }
        val envelope =
            transport.post(
                route(guest, "secret", identifier, "reveal"),
                body = body,
                deserializer = RecordEnvelope.serializer(Secret.serializer()),
            )
        val value = envelope.record.value ?: throw SecretNotRevealedException(identifier)
        val details = envelope.details?.let { transport.json.decodeFromJsonElement(RevealDetails.serializer(), it) }
        return RevealedSecret(value = value, secret = envelope.record, isOwner = details?.isOwner)
    }

    /**
     * Fetch a secret's metadata (state, whether it needs a passphrase, time
     * remaining) **without** revealing it. Safe to call repeatedly.
     *
     * @param keyOrUrl The secret identifier, or the full link.
     * @param guest Use the anonymous `/guest/` route (see [conceal]).
     * @throws NotFoundException when the secret does not exist or is gone.
     */
    public suspend fun show(
        keyOrUrl: String,
        guest: Boolean = false,
    ): Secret {
        val identifier = Identifiers.secret(keyOrUrl)
        return transport
            .get(
                route(guest, "secret", identifier),
                deserializer = RecordEnvelope.serializer(Secret.serializer()),
            ).record
    }

    /**
     * Lightweight status check for one secret. Unlike [show], an unknown
     * identifier is not an error: the result has [SecretState.UNKNOWN].
     */
    public suspend fun status(keyOrUrl: String): SecretStatus {
        val identifier = Identifiers.secret(keyOrUrl)
        val envelope =
            transport.get(
                listOf("secret", identifier, "status"),
                deserializer = RecordEnvelope.serializer(JsonObject.serializer()),
            )
        val state = (envelope.record["state"] as? JsonPrimitive)?.contentOrNull
        if (state == SecretState.UNKNOWN.wireName || envelope.record["identifier"] == null) {
            return SecretStatus(identifier = identifier, state = SecretState.UNKNOWN, secret = null, expiresAt = null)
        }
        val secret = transport.json.decodeFromJsonElement(Secret.serializer(), envelope.record)
        val details = envelope.details?.let { transport.json.decodeFromJsonElement(StatusDetails.serializer(), it) }
        return SecretStatus(
            identifier = secret.identifier,
            state = secret.state,
            secret = secret,
            expiresAt = details?.currentExpiration,
        )
    }

    /**
     * Status of several secrets in one request. Identifiers the service does
     * not know are omitted from the result.
     */
    public suspend fun status(keysOrUrls: Collection<String>): List<Secret> {
        require(keysOrUrls.isNotEmpty()) { "at least one identifier is required" }
        val identifiers = keysOrUrls.map(Identifiers::secret)
        val body =
            buildJsonObject {
                putJsonArray("identifiers") { identifiers.forEach { add(JsonPrimitive(it)) } }
            }
        return transport
            .post(
                listOf("secret", "status"),
                body = body,
                deserializer = RecordsEnvelope.serializer(Secret.serializer()),
            ).records
    }

    // -- helpers ------------------------------------------------------------

    /** First view of a fresh receipt: the one place the service shows a generated value. */
    private suspend fun claimGeneratedValue(
        guest: Boolean,
        receiptIdentifier: String,
    ): String? {
        val envelope =
            transport.get(
                route(guest, "receipt", receiptIdentifier),
                deserializer = RecordEnvelope.serializer(JsonObject.serializer()),
            )
        return envelope.details
            ?.let { transport.json.decodeFromJsonElement(ReceiptDetails.serializer(), it) }
            ?.secretValue
    }

    private fun ConcealResult.withValue(value: String?): ConcealResult =
        ConcealResult(receipt, secret, shareDomain, secretUrl, receiptUrl, value = value)

    private fun ConcealResult.withClaimFailure(failure: OnetimeSecretException): ConcealResult =
        ConcealResult(receipt, secret, shareDomain, secretUrl, receiptUrl, claimFailure = failure)

    private fun route(
        guest: Boolean,
        vararg segments: String,
    ): List<String> = if (guest) listOf("guest", *segments) else segments.toList()

    private fun JsonObjectBuilder.putCommon(
        ttl: Duration?,
        passphrase: String?,
        recipient: String?,
        shareDomain: String?,
        displayType: DisplayType?,
    ) {
        ttl?.let {
            require(it.isPositive()) { "ttl must be positive" }
            put("ttl", it.inWholeSeconds)
        }
        passphrase?.let { put("passphrase", it) }
        recipient?.let { put("recipient", it) }
        shareDomain?.let { put("share_domain", it) }
        displayType?.let { put("display_type", it.wireName) }
    }

    private inline fun JsonObjectBuilder.putNested(
        key: String,
        block: JsonObjectBuilder.() -> Unit,
    ) {
        put(key, buildJsonObject(block))
    }

    private fun toConcealResult(record: ConcealRecord): ConcealResult {
        val linkHost =
            record.shareDomain
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    HttpUrl
                        .Builder()
                        .scheme("https")
                        .host(it)
                        .build()
                }
                ?: baseUrl
        val secretUrl =
            linkHost
                .newBuilder()
                .encodedPath("/")
                .addPathSegment("secret")
                .addPathSegment(record.secret.identifier)
                .build()
                .toString()
        val receiptUrl =
            record.receipt.receiptUrl
                ?: baseUrl
                    .newBuilder()
                    .encodedPath("/")
                    .addPathSegment("receipt")
                    .addPathSegment(record.receipt.identifier)
                    .build()
                    .toString()
        return ConcealResult(
            receipt = record.receipt,
            secret = record.secret,
            shareDomain = record.shareDomain,
            secretUrl = secretUrl,
            receiptUrl = receiptUrl,
        )
    }
}
