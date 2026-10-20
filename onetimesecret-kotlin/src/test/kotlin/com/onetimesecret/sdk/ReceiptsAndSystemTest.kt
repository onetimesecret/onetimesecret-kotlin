package com.onetimesecret.sdk

import com.onetimesecret.sdk.model.ReceiptState
import com.onetimesecret.sdk.model.SecretKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptsAndSystemTest : MockServerTest() {
    @Test
    fun `show parses the receipt including lenient scalars and lists`() =
        runTest {
            enqueueJson(Fixtures.receiptResponse)

            val receipt = client.receipts.show("rcpt1234567890")

            assertEquals("/api/v2/receipt/rcpt1234567890", takeRequest().url.encodedPath)
            assertEquals(ReceiptState.SHARED, receipt.state)
            assertEquals(1760976000L, receipt.createdAt, "fractional epoch truncates")
            assertEquals(listOf("a@example.com", "b@example.com"), receipt.recipients)
            assertEquals(false, receipt.hasPassphrase)
            assertEquals("for ops", receipt.memo)
            assertEquals(SecretKind.CONCEAL, receipt.kind)
            assertEquals(3540L, receipt.expirationInSeconds)
            assertEquals(1760979600L, receipt.expiresAt)
            assertEquals("https://eu.onetimesecret.com/secret/sec1234567890abc", receipt.shareUrl)
        }

    @Test
    fun `show accepts receipt, private and metadata URLs`() =
        runTest {
            repeat(3) { enqueueJson(Fixtures.receiptResponse) }
            client.receipts.show("https://eu.onetimesecret.com/receipt/rcpt1234567890")
            client.receipts.show("https://eu.onetimesecret.com/private/rcpt1234567890")
            client.receipts.show("https://eu.onetimesecret.com/metadata/rcpt1234567890")
            repeat(3) { assertEquals("/api/v2/receipt/rcpt1234567890", takeRequest().url.encodedPath) }
        }

    @Test
    fun `recent lists records and tolerates a single-string recipient and unknown state`() =
        runTest {
            enqueueJson(Fixtures.recentResponse)

            val receipts = client.receipts.recent()

            assertEquals("/api/v2/receipt/recent", takeRequest().url.encodedPath)
            assertEquals(2, receipts.size)
            assertEquals(listOf("x@example.com"), receipts[0].recipients)
            assertEquals(ReceiptState.UNKNOWN, receipts[1].state)
            assertEquals(emptyList(), receipts[1].recipients)
        }

    @Test
    fun `burn posts continue and passphrase`() =
        runTest {
            enqueueJson(Fixtures.receiptResponse.replace("\"state\": \"shared\"", "\"state\": \"burned\""))

            val receipt = client.receipts.burn("rcpt1234567890", passphrase = "pw")

            val request = takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/receipt/rcpt1234567890/burn", request.url.encodedPath)
            assertEquals("""{"continue":true,"passphrase":"pw"}""", request.bodyString())
            assertEquals(ReceiptState.BURNED, receipt.state)
        }

    @Test
    fun `burn as guest uses the guest route and sends an empty object without options`() =
        runTest {
            enqueueJson(Fixtures.receiptResponse)
            client.receipts.burn("rcpt1234567890", guest = true)
            val request = takeRequest()
            assertEquals("/api/v2/guest/receipt/rcpt1234567890/burn", request.url.encodedPath)
            assertEquals("""{"continue":true}""", request.bodyString())
        }

    @Test
    fun `update patches the memo`() =
        runTest {
            enqueueJson(Fixtures.receiptResponse)

            client.receipts.update("rcpt1234567890", memo = "rotated")

            val request = takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v2/receipt/rcpt1234567890", request.url.encodedPath)
            assertEquals("""{"memo":"rotated"}""", request.bodyString())
        }

    @Test
    fun `update rejects a memo over 500 characters locally`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { client.receipts.update("r", "x".repeat(501)) }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `status version and supportedLocales`() =
        runTest {
            enqueueJson(Fixtures.statusResponse)
            enqueueJson(Fixtures.versionResponse)
            enqueueJson(Fixtures.localesResponse)

            assertEquals("nominal", client.status().status)
            assertEquals("0.25.3", client.version().version)
            val locales = client.supportedLocales()
            assertEquals(listOf("en", "fr_CA"), locales.locales)
            assertEquals("en", locales.defaultLocale)

            assertEquals("/api/v2/status", takeRequest().url.encodedPath)
            assertEquals("/api/v2/version", takeRequest().url.encodedPath)
            assertEquals("/api/v2/supported-locales", takeRequest().url.encodedPath)
        }

    @Test
    fun `base URL with a path prefix is preserved`() =
        runTest {
            val prefixed = newClient { baseUrl = server.url("/ots/").toString() }
            enqueueJson(Fixtures.statusResponse)
            prefixed.status()
            assertEquals("/ots/api/v2/status", takeRequest().url.encodedPath)
            prefixed.close()
        }

    @Test
    fun `anonymous client sends no Authorization header`() =
        runTest {
            val anonymous = newClient(credentials = null)
            enqueueJson(Fixtures.statusResponse)
            anonymous.status()
            assertNull(takeRequest().headers["Authorization"])
            assertTrue(!anonymous.isAuthenticated)
            anonymous.close()
        }
}
