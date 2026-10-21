package com.onetimesecret.sdk

/** Canned v2 responses, shaped like the OpenAPI document and real captures. */
object Fixtures {
    // Timestamps as strings on secrets and numbers on receipts, booleans as
    // strings: this is what the service actually sends.
    val concealResponse: String get() = concealResponse(null)

    fun concealResponse(shareDomain: String?): String =
        """
        {
          "user_id": "ur1abc23def",
          "shrimp": "",
          "record": {
            "receipt": {
              "identifier": "rcpt1234567890",
              "created": 1760976000,
              "updated": 1760976000,
              "key": "rcpt1234567890",
              "shortid": "rcpt123",
              "state": "new",
              "custid": "ur1abc23def",
              "owner_id": "ur1abc23def",
              "secret_ttl": "3600",
              "receipt_ttl": "7200",
              "lifespan": "1 hour",
              "secret_shortid": "sec1234",
              "secret_identifier": "sec1234567890abc",
              "recipients": null,
              "share_domain": null,
              "has_passphrase": true,
              "is_previewed": "false",
              "is_revealed": "false",
              "memo": null,
              "kind": "conceal"
            },
            "secret": {
              "identifier": "sec1234567890abc",
              "created": "1760976000",
              "updated": "1760976000",
              "key": "sec1234567890abc",
              "shortid": "sec1234",
              "state": "new",
              "has_passphrase": "true",
              "display_type": "text",
              "secret_ttl": "3600",
              "lifespan": "1 hour",
              "is_previewed": "false",
              "is_revealed": "false"
            },
            "share_domain": ${if (shareDomain == null) "null" else "\"$shareDomain\""}
          },
          "details": { "kind": "conceal", "recipient": null, "recipient_safe": null }
        }
        """.trimIndent()

    // A generate response as captured from a current server: numeric scalars
    // throughout, a `metadata` alias of the receipt, `kind: "generate"`, and
    // no value anywhere. That is disclosed by the first receipt view instead.
    val generateResponse =
        """
        {
          "success": true,
          "record": {
            "receipt": {
              "identifier": "rcptgen1234567890",
              "key": "rcptgen1234567890",
              "custid": null,
              "owner_id": null,
              "state": "new",
              "secret_shortid": "secgen12",
              "secret_identifier": "secgen1234567890",
              "secret_ttl": 300,
              "metadata_ttl": 300,
              "receipt_ttl": 300,
              "lifespan": 300,
              "share_domain": null,
              "created": 1792596963.0864809,
              "updated": 1792596963.086494,
              "recipients": "",
              "memo": null,
              "shortid": "rcptgen1",
              "is_previewed": false,
              "is_revealed": false,
              "has_passphrase": false,
              "kind": "generate",
              "source": "standard"
            },
            "metadata": { "identifier": "rcptgen1234567890", "kind": "generate" },
            "secret": {
              "identifier": "secgen1234567890",
              "key": "secgen1234567890",
              "shortid": "secgen12",
              "state": "new",
              "lifespan": 300,
              "secret_ttl": 300,
              "has_passphrase": false,
              "verification": false,
              "display_type": "text",
              "created": 1792596963.009391,
              "updated": 1792596963.0101912,
              "is_previewed": false,
              "is_revealed": false
            },
            "share_domain": null
          },
          "details": { "kind": "generate", "recipient": [], "recipient_safe": [] }
        }
        """.trimIndent()

    /** First view of a fresh generated receipt: the value is in `details`, never in `record`. */
    fun generatedReceiptResponse(value: String?): String =
        """
        {
          "record": {
            "identifier": "rcptgen1234567890",
            "key": "rcptgen1234567890",
            "state": "new",
            "secret_identifier": "secgen1234567890",
            "secret_ttl": 300,
            "created": 1792596963.0864809,
            "updated": 1792596963.086494,
            "has_passphrase": false,
            "kind": "generate"
          },
          "details": {
            "type": "record",
            "display_lines": 4,
            "no_cache": true,
            "view_count": 0,
            "can_decrypt": true,
            "secret_value": ${if (value == null) "null" else "\"$value\""},
            "show_secret": true
          }
        }
        """.trimIndent()

    val revealResponse =
        """
        {
          "record": {
            "identifier": "sec1234567890abc",
            "created": "1760976000",
            "updated": "1760976100",
            "key": "sec1234567890abc",
            "shortid": "sec1234",
            "state": "revealed",
            "has_passphrase": "true",
            "secret_value": "hunter2",
            "display_type": "text",
            "secret_ttl": "3500",
            "lifespan": "1 hour",
            "is_revealed": "true"
          },
          "details": { "continue": "true", "is_owner": "false", "show_secret": "true", "display_lines": "1" }
        }
        """.trimIndent()

    val revealWithoutValueResponse =
        """
        {
          "record": {
            "identifier": "sec1234567890abc",
            "created": "1760976000",
            "updated": "1760976100",
            "key": "sec1234567890abc",
            "shortid": "sec1234",
            "state": "revealed",
            "display_type": "text",
            "secret_ttl": null,
            "lifespan": "1 hour"
          },
          "details": { "display_lines": "1" }
        }
        """.trimIndent()

    val statusKnownResponse =
        """
        {
          "record": {
            "identifier": "sec1234567890abc",
            "created": "1760976000",
            "updated": "1760976000",
            "key": "sec1234567890abc",
            "shortid": "sec1234",
            "state": "previewed",
            "display_type": "text",
            "secret_ttl": "3000",
            "lifespan": "1 hour"
          },
          "details": { "current_expiration": 1760979600 }
        }
        """.trimIndent()

    val statusListResponse =
        """
        {
          "records": [
            { "identifier": "sec1234567890abc", "created": "1760976000", "updated": "1760976000",
              "key": "sec1234567890abc", "shortid": "sec1234", "state": "previewed", "display_type": "text" }
          ],
          "details": {},
          "count": 1
        }
        """.trimIndent()

    val statusUnknownResponse = """{ "record": { "state": "unknown" } }""".trimIndent()

    val receiptResponse =
        """
        {
          "record": {
            "identifier": "rcpt1234567890",
            "created": 1760976000.5,
            "updated": 1760976000,
            "key": "rcpt1234567890",
            "shortid": "rcpt123",
            "state": "shared",
            "secret_ttl": "3600",
            "receipt_ttl": "7200",
            "lifespan": "1 hour",
            "secret_identifier": "sec1234567890abc",
            "recipients": ["a@example.com", "b@example.com"],
            "has_passphrase": false,
            "memo": "for ops",
            "kind": "conceal",
            "natural_expiration": "59 minutes",
            "expiration_in_seconds": "3540",
            "expiration": 1760979600,
            "share_url": "https://eu.onetimesecret.com/secret/sec1234567890abc",
            "receipt_url": "https://eu.onetimesecret.com/receipt/rcpt1234567890",
            "burn_url": "https://eu.onetimesecret.com/receipt/rcpt1234567890/burn",
            "share_path": "/secret/sec1234567890abc",
            "receipt_path": "/receipt/rcpt1234567890",
            "burn_path": "/receipt/rcpt1234567890/burn",
            "some_future_field": { "nested": true }
          },
          "details": { "type": "record", "display_lines": "1" }
        }
        """.trimIndent()

    val recentResponse =
        """
        {
          "records": [
            { "identifier": "r1", "created": 1, "updated": 1, "shortid": "r1", "state": "new",
              "recipients": "x@example.com" },
            { "identifier": "r2", "created": 2, "updated": 2, "shortid": "r2", "state": "some_new_state" }
          ],
          "count": 2
        }
        """.trimIndent()

    val statusResponse = """{ "success": true, "status": "nominal", "locale": "en" }""".trimIndent()
    val versionResponse = """{ "success": true, "version": [0, 25, "3"], "locale": "en" }""".trimIndent()
    val localesResponse =
        """{ "success": true, "locales": ["en", "fr_CA"], "default_locale": "en", "locale": "en" }""".trimIndent()

    fun error(
        type: String?,
        message: String,
        extra: String = "",
    ): String =
        buildString {
            append("{")
            if (type != null) append("\"error_type\": \"$type\", ")
            append("\"error\": \"$message\", \"request_id\": \"req-123\"")
            if (extra.isNotEmpty()) append(", ").append(extra)
            append("}")
        }
}
