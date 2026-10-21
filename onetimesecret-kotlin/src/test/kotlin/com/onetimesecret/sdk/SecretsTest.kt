package com.onetimesecret.sdk

import com.onetimesecret.sdk.model.CharacterSets
import com.onetimesecret.sdk.model.DisplayType
import com.onetimesecret.sdk.model.SecretKind
import com.onetimesecret.sdk.model.SecretState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SecretsTest : MockServerTest() {
    @Test
    fun `conceal sends a nested secret payload and returns receipt, secret and links`() =
        runTest {
            enqueueJson(Fixtures.concealResponse)

            val result =
                client.secrets.conceal(
                    "hunter2",
                    ttl = 1.hours,
                    passphrase = "pw12",
                    recipient = "ops@example.com",
                    displayType = DisplayType.MARKDOWN,
                )

            val request = takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/secret/conceal", request.url.encodedPath)
            assertEquals("application/json", request.headers["Accept"])
            assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
            assertTrue(request.headers["User-Agent"]!!.startsWith("onetimesecret-kotlin/"))
            val payload = Json.parseToJsonElement(request.bodyString()).jsonObject["secret"]!!.jsonObject
            assertEquals("hunter2", payload["secret"]!!.jsonPrimitive.content)
            assertEquals(3600L, payload["ttl"]!!.jsonPrimitive.long)
            assertEquals("pw12", payload["passphrase"]!!.jsonPrimitive.content)
            assertEquals("ops@example.com", payload["recipient"]!!.jsonPrimitive.content)
            assertEquals("markdown", payload["display_type"]!!.jsonPrimitive.content)
            assertNull(payload["share_domain"], "absent options are not sent")

            assertEquals("rcpt1234567890", result.receipt.identifier)
            assertEquals("sec1234567890abc", result.secret.identifier)
            assertEquals(SecretState.NEW, result.secret.state)
            assertEquals(true, result.secret.hasPassphrase, "\"true\" string coerces to Boolean")
            assertEquals(3600L, result.secret.ttlSeconds, "\"3600\" string coerces to Long")
            assertEquals(1760976000L, result.secret.createdAt)
            assertEquals(server.url("/secret/sec1234567890abc").toString(), result.secretUrl)
            assertEquals(server.url("/receipt/rcpt1234567890").toString(), result.receiptUrl)
        }

    @Test
    fun `conceal with a share domain builds the link on that domain`() =
        runTest {
            enqueueJson(Fixtures.concealResponse("secrets.example.com"))

            val result = client.secrets.conceal("x", shareDomain = "secrets.example.com")

            assertEquals("secrets.example.com", result.shareDomain)
            assertEquals("https://secrets.example.com/secret/sec1234567890abc", result.secretUrl)
        }

    @Test
    fun `conceal as guest uses the guest route`() =
        runTest {
            enqueueJson(Fixtures.concealResponse)
            client.secrets.conceal("x", guest = true)
            assertEquals("/api/v2/guest/secret/conceal", takeRequest().url.encodedPath)
        }

    @Test
    fun `conceal rejects an empty secret before any request`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { client.secrets.conceal("") }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `generate sends length and character sets`() =
        runTest {
            enqueueJson(Fixtures.concealResponse)

            client.secrets.generate(
                length = 24,
                characterSets = CharacterSets(symbols = false, excludeAmbiguous = true),
            )

            val request = takeRequest()
            assertEquals("/api/v2/secret/generate", request.url.encodedPath)
            val payload = Json.parseToJsonElement(request.bodyString()).jsonObject["secret"]!!.jsonObject
            assertEquals(24L, payload["length"]!!.jsonPrimitive.long)
            val sets = payload["character_sets"]!!.jsonObject
            assertFalse(sets["symbols"]!!.jsonPrimitive.boolean)
            assertTrue(sets["exclude_ambiguous"]!!.jsonPrimitive.boolean)
            assertNull(sets["uppercase"], "unset character set flags are omitted")
        }

    @Test
    fun `generate returns the generated value once and a receipt of kind GENERATE`() =
        runTest {
            enqueueJson(Fixtures.generateResponse)

            val result = client.secrets.generate(length = 12)

            assertEquals("""{"secret":{"length":12}}""", takeRequest().bodyString())
            assertEquals(SecretKind.GENERATE, result.receipt.kind)
            assertEquals("Xk9#mP2vQ7wL", result.secret.value)
            assertEquals(SecretState.NEW, result.secret.state)
            assertEquals(false, result.secret.hasPassphrase)
            assertEquals(300L, result.secret.ttlSeconds, "numeric scalars decode too")
            assertEquals(1792596963L, result.receipt.createdAt, "fractional epoch truncates")
            assertEquals(server.url("/secret/secgen1234567890").toString(), result.secretUrl)
            assertFalse(result.secret.toString().contains("Xk9#mP2vQ7wL"), "toString must redact the value")
        }

    @Test
    fun `reveal posts continue and passphrase and returns the value`() =
        runTest {
            enqueueJson(Fixtures.revealResponse)

            val revealed = client.secrets.reveal("sec1234567890abc", passphrase = "pw12")

            val request = takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/secret/sec1234567890abc/reveal", request.url.encodedPath)
            val payload = Json.parseToJsonElement(request.bodyString()).jsonObject
            assertTrue(payload["continue"]!!.jsonPrimitive.boolean)
            assertEquals("pw12", payload["passphrase"]!!.jsonPrimitive.content)

            assertEquals("hunter2", revealed.value)
            assertEquals(SecretState.REVEALED, revealed.secret.state)
            assertEquals(false, revealed.isOwner)
            assertFalse(revealed.toString().contains("hunter2"), "toString must redact the value")
            assertFalse(revealed.secret.toString().contains("hunter2"), "toString must redact the value")
        }

    @Test
    fun `reveal accepts the full secret URL`() =
        runTest {
            enqueueJson(Fixtures.revealResponse)
            client.secrets.reveal("https://eu.onetimesecret.com/secret/sec1234567890abc")
            assertEquals("/api/v2/secret/sec1234567890abc/reveal", takeRequest().url.encodedPath)
        }

    @Test
    fun `reveal without a value in the response throws SecretNotRevealedException`() =
        runTest {
            enqueueJson(Fixtures.revealWithoutValueResponse)
            val e = assertFailsWith<SecretNotRevealedException> { client.secrets.reveal("sec1234567890abc") }
            assertEquals("sec1234567890abc", e.identifier)
        }

    @Test
    fun `show fetches metadata with GET`() =
        runTest {
            enqueueJson(Fixtures.statusKnownResponse)
            val secret = client.secrets.show("sec1234567890abc")
            val request = takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v2/secret/sec1234567890abc", request.url.encodedPath)
            assertEquals(SecretState.PREVIEWED, secret.state)
            assertNull(secret.value)
        }

    @Test
    fun `status returns the record and expiration for a known secret`() =
        runTest {
            enqueueJson(Fixtures.statusKnownResponse)
            val status = client.secrets.status("sec1234567890abc")
            assertEquals("/api/v2/secret/sec1234567890abc/status", takeRequest().url.encodedPath)
            assertEquals(SecretState.PREVIEWED, status.state)
            assertTrue(status.isAvailable)
            assertEquals(1760979600L, status.expiresAt)
            assertEquals("sec1234567890abc", status.secret?.identifier)
        }

    @Test
    fun `status returns UNKNOWN rather than failing for an unknown identifier`() =
        runTest {
            enqueueJson(Fixtures.statusUnknownResponse)
            val status = client.secrets.status("nope")
            assertEquals(SecretState.UNKNOWN, status.state)
            assertFalse(status.isAvailable)
            assertNull(status.secret)
            assertEquals("nope", status.identifier)
        }

    @Test
    fun `status for many identifiers posts an identifiers array`() =
        runTest {
            enqueueJson(Fixtures.statusListResponse)

            val records = client.secrets.status(listOf("sec1234567890abc", "https://x.example/secret/other1"))

            val request = takeRequest()
            assertEquals("/api/v2/secret/status", request.url.encodedPath)
            assertEquals(
                """{"identifiers":["sec1234567890abc","other1"]}""",
                request.bodyString(),
            )
            assertEquals(1, records.size)
        }

    @Test
    fun `identifiers containing path characters are rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { client.secrets.show("../receipt/recent") }
            assertFailsWith<IllegalArgumentException> { client.secrets.show("  ") }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `unknown response fields and states do not break parsing`() =
        runTest {
            enqueueJson(
                """
                { "record": { "identifier": "s", "state": "brand_new_state", "display_type": "hologram",
                              "extra": [1,2] } }
                """.trimIndent(),
            )
            val secret = client.secrets.show("s")
            assertEquals(SecretState.UNKNOWN, secret.state)
            assertEquals(DisplayType.TEXT, secret.displayType)
        }
}
