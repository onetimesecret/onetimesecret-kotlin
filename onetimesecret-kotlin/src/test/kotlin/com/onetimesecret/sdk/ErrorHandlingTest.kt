package com.onetimesecret.sdk

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorHandlingTest : MockServerTest() {
    @Test
    fun `error_type takes precedence over status code`() =
        runTest {
            enqueueJson(Fixtures.error("RecordNotFound", "Unknown secret"), code = 400)
            val e = assertFailsWith<NotFoundException> { client.secrets.show("x") }
            assertEquals(400, e.statusCode)
            assertEquals("RecordNotFound", e.errorType)
            assertEquals("Unknown secret", e.errorMessage)
            assertEquals("req-123", e.requestId)
            assertTrue(e.message!!.contains("req-123"), "request id is in the message for bug reports")
        }

    @Test
    fun `status code is the fallback when error_type is absent or unknown`() =
        runTest {
            enqueueJson(Fixtures.error(null, "Nope"), code = 401)
            assertFailsWith<AuthenticationException> { client.receipts.recent() }

            enqueueJson(Fixtures.error("SomethingNew", "Nope"), code = 403)
            assertFailsWith<ForbiddenException> { client.receipts.recent() }

            enqueueJson(Fixtures.error(null, "Nope"), code = 404)
            assertFailsWith<NotFoundException> { client.receipts.recent() }

            enqueueJson(Fixtures.error(null, "Nope"), code = 422)
            assertFailsWith<BadRequestException> { client.receipts.recent() }

            enqueueJson(Fixtures.error(null, "Nope"), code = 418)
            val e = assertFailsWith<ApiException> { client.receipts.recent() }
            assertEquals(418, e.statusCode)
        }

    @Test
    fun `FormError maps to BadRequestException with the field`() =
        runTest {
            enqueueJson(Fixtures.error("FormError", "Secret is too long", "\"field\": \"secret\""), code = 400)
            val e = assertFailsWith<BadRequestException> { client.secrets.conceal("x") }
            assertEquals("secret", e.field)
        }

    @Test
    fun `requires_account in any slot maps to AccountRequiredException`() =
        runTest {
            enqueueJson(
                Fixtures.error("FormError", "Account required", "\"field\": \"requires_account\""),
                code = 400,
            )
            val byField = assertFailsWith<AccountRequiredException> { client.secrets.conceal("x") }
            assertEquals("requires_account", byField.field)
            assertIs<AuthenticationException>(byField)

            enqueueJson(
                Fixtures.error("GuestRoutesDisabled", "No", "\"code\": \"GUEST_CONCEAL_REQUIRES_ACCOUNT\""),
                code = 403,
            )
            assertFailsWith<AccountRequiredException> { client.secrets.conceal("x") }

            enqueueJson(
                Fixtures.error("FormError", "No", "\"error_key\": \"web.COMMON.account_required\""),
                code = 400,
            )
            assertFailsWith<AccountRequiredException> { client.secrets.conceal("x") }
        }

    @Test
    fun `GuestRoutesDisabled and Forbidden map to ForbiddenException`() =
        runTest {
            enqueueJson(
                Fixtures.error("GuestRoutesDisabled", "Guest routes are off", "\"code\": \"GUEST_REVEAL_DISABLED\""),
                code = 403,
            )
            assertFailsWith<ForbiddenException> { client.secrets.reveal("x", guest = true) }
        }

    @Test
    fun `EntitlementRequired carries the entitlement`() =
        runTest {
            enqueueJson(
                Fixtures.error("EntitlementRequired", "Upgrade", "\"entitlement\": \"custom_domains\""),
                code = 403,
            )
            val e =
                assertFailsWith<EntitlementException> {
                    client.secrets.conceal("x", shareDomain = "d.example")
                }
            assertEquals("custom_domains", e.entitlement)
            assertIs<ForbiddenException>(e)
        }

    @Test
    fun `LimitExceeded carries retry_after from the body or header`() =
        runTest {
            enqueueJson(Fixtures.error("LimitExceeded", "Slow down", "\"retry_after\": 42"), code = 429)
            val fromBody = assertFailsWith<RateLimitException> { client.secrets.show("x") }
            assertEquals(42L, fromBody.retryAfterSeconds)

            enqueueJson(Fixtures.error(null, "Slow down"), code = 429, headers = mapOf("Retry-After" to "7"))
            val fromHeader = assertFailsWith<RateLimitException> { client.secrets.show("x") }
            assertEquals(7L, fromHeader.retryAfterSeconds)
        }

    @Test
    fun `non-JSON error bodies still map by status`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(502)
                    .body("<html>Bad Gateway</html>")
                    .build(),
            )
            val e = assertFailsWith<ServerException> { client.secrets.show("x") }
            assertEquals(502, e.statusCode)
            assertEquals("<html>Bad Gateway</html>", e.rawBody)
        }

    @Test
    fun `GET retries on 503 then succeeds and POST never retries`() =
        runTest {
            val retrying = newClient(maxRetries = 2)
            enqueueJson(Fixtures.error("ServerError", "busy"), code = 503)
            enqueueJson(Fixtures.error("ServerError", "busy"), code = 503)
            enqueueJson(Fixtures.statusResponse)
            assertEquals("nominal", retrying.status().status)
            assertEquals(3, server.requestCount)

            enqueueJson(Fixtures.error("ServerError", "busy"), code = 503)
            assertFailsWith<ServerException> { retrying.secrets.conceal("x") }
            assertEquals(4, server.requestCount, "a POST is not retried")
            retrying.close()
        }

    @Test
    fun `GET gives up after maxRetries`() =
        runTest {
            val retrying = newClient(maxRetries = 1)
            enqueueJson(Fixtures.error("ServerError", "busy"), code = 503)
            enqueueJson(Fixtures.error("ServerError", "busy"), code = 503)
            assertFailsWith<ServerException> { retrying.status() }
            assertEquals(2, server.requestCount)
            retrying.close()
        }

    @Test
    fun `a 500 is not retried`() =
        runTest {
            val retrying = newClient(maxRetries = 2)
            enqueueJson(Fixtures.error("ServerError", "boom"), code = 500)
            assertFailsWith<ServerException> { retrying.status() }
            assertEquals(1, server.requestCount)
            retrying.close()
        }

    @Test
    fun `a dropped connection surfaces as TransportException with the cause`() =
        runTest {
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
            val e = assertFailsWith<TransportException> { client.secrets.conceal("x") }
            assertNotNull(e.cause)
        }

    @Test
    fun `a malformed success body surfaces as TransportException`() =
        runTest {
            enqueueJson("{ not json")
            assertFailsWith<TransportException> { client.status() }
            enqueueJson("""{ "record": { "state": "new" } }""")
            assertFailsWith<TransportException> { client.secrets.show("x") }
        }
}
