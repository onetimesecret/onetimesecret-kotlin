package com.onetimesecret.sdk

import com.onetimesecret.sdk.internal.HttpTransport
import com.onetimesecret.sdk.internal.Identifiers
import com.onetimesecret.sdk.internal.RecordEnvelope
import com.onetimesecret.sdk.internal.RecordsEnvelope
import com.onetimesecret.sdk.model.Receipt
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Receipt operations (creator-facing secret metadata): `show`, `recent`,
 * `burn`, `update`. Reached through [OnetimeSecretClient.receipts].
 *
 * "Receipt" is the current vocabulary; the API also serves the same records
 * under the legacy `/private` alias, which this SDK does not use.
 */
public class Receipts internal constructor(
    private val transport: HttpTransport,
) {
    /**
     * Fetch a receipt by its identifier (or the full receipt URL).
     *
     * @param guest Use the anonymous `/guest/` route (see [Secrets.conceal]).
     * @throws NotFoundException when the receipt does not exist.
     */
    public suspend fun show(
        keyOrUrl: String,
        guest: Boolean = false,
    ): Receipt {
        val identifier = Identifiers.receipt(keyOrUrl)
        return transport
            .get(
                route(guest, "receipt", identifier),
                deserializer = RecordEnvelope.serializer(Receipt.serializer()),
            ).record
    }

    /**
     * List the authenticated account's receipts for secrets created in the
     * last 30 days. Requires credentials.
     *
     * @throws AuthenticationException when the client has no (valid) credentials.
     */
    public suspend fun recent(): List<Receipt> =
        transport
            .get(
                listOf("receipt", "recent"),
                deserializer = RecordsEnvelope.serializer(Receipt.serializer()),
            ).records

    /**
     * Burn (destroy) an unrevealed secret via its receipt. Irreversible.
     *
     * @param keyOrUrl The receipt identifier, or the full receipt URL.
     * @param passphrase Required when the secret was created with one.
     * @param guest Use the anonymous `/guest/` route (see [Secrets.conceal]).
     * @return The updated receipt, now in [com.onetimesecret.sdk.model.ReceiptState.BURNED].
     */
    public suspend fun burn(
        keyOrUrl: String,
        passphrase: String? = null,
        guest: Boolean = false,
    ): Receipt {
        val identifier = Identifiers.receipt(keyOrUrl)
        val body =
            buildJsonObject {
                put("continue", true)
                passphrase?.let { put("passphrase", it) }
            }
        return transport
            .post(
                route(guest, "receipt", identifier, "burn"),
                body = body,
                deserializer = RecordEnvelope.serializer(Receipt.serializer()),
            ).record
    }

    /**
     * Update a receipt's memo (a private note for the creator, up to 500
     * characters). Only the owner may update; requires credentials.
     */
    public suspend fun update(
        keyOrUrl: String,
        memo: String,
    ): Receipt {
        val identifier = Identifiers.receipt(keyOrUrl)
        require(memo.length <= MEMO_MAX_LENGTH) { "memo must be at most $MEMO_MAX_LENGTH characters" }
        return transport
            .patch(
                listOf("receipt", identifier),
                body = buildJsonObject { put("memo", memo) },
                deserializer = RecordEnvelope.serializer(Receipt.serializer()),
            ).record
    }

    private fun route(
        guest: Boolean,
        vararg segments: String,
    ): List<String> = if (guest) listOf("guest", *segments) else segments.toList()

    private companion object {
        const val MEMO_MAX_LENGTH = 500
    }
}
