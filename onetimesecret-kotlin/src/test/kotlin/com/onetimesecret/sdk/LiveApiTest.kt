package com.onetimesecret.sdk

import com.onetimesecret.sdk.model.ReceiptState
import com.onetimesecret.sdk.model.SecretState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end tests against a real deployment. They never run by default:
 * set `ONETIME_LIVE=1` and `ONETIME_BASE_URL` (plus `ONETIME_CUSTOMER_EXTID`
 * and `ONETIME_API_TOKEN` for the authenticated cases). CI runs them on a
 * schedule and on manual dispatch only, never on push or pull request.
 *
 * Secrets created here are short-lived and are revealed or burned before the
 * test ends.
 */
class LiveApiTest {
    private val live = System.getenv("ONETIME_LIVE") == "1"
    private val liveBaseUrl = System.getenv("ONETIME_BASE_URL")
    private val extid = System.getenv("ONETIME_CUSTOMER_EXTID")
    private val token = System.getenv("ONETIME_API_TOKEN")

    private fun anonymousClient(): OnetimeSecretClient {
        assumeTrue(live && !liveBaseUrl.isNullOrBlank(), "ONETIME_LIVE=1 and ONETIME_BASE_URL are required")
        return OnetimeSecretClient {
            baseUrl = liveBaseUrl
            readTimeout = 30.seconds
        }
    }

    private fun authenticatedClient(): OnetimeSecretClient {
        assumeTrue(
            live && !liveBaseUrl.isNullOrBlank() && !extid.isNullOrBlank() && !token.isNullOrBlank(),
            "ONETIME_LIVE=1, ONETIME_BASE_URL, ONETIME_CUSTOMER_EXTID and ONETIME_API_TOKEN are required",
        )
        return OnetimeSecretClient {
            baseUrl = liveBaseUrl
            credentials(extid!!, token!!)
            readTimeout = 30.seconds
        }
    }

    @Test
    fun `status and supported locales work anonymously`() =
        runTest {
            anonymousClient().use { client ->
                assertTrue(client.status().status.isNotBlank())
                assertTrue(client.supportedLocales().locales.isNotEmpty())
            }
        }

    @Test
    fun `guest conceal, show, status and reveal round-trip`() =
        runTest {
            anonymousClient().use { client ->
                val value = "live-test ${System.nanoTime()}"
                val created = client.secrets.conceal(value, ttl = 5.minutes, guest = true)
                assertEquals(SecretState.NEW, created.secret.state)

                val shown = client.secrets.show(created.secret.identifier, guest = true)
                assertEquals(created.secret.identifier, shown.identifier)

                // The server builds receipt links from its HOST setting; a local
                // server whose HOST differs from ONETIME_BASE_URL fails here.
                val receipt = client.receipts.show(created.receipt.identifier, guest = true)
                assertEquals(created.secretUrl, receipt.shareUrl)

                val status = client.secrets.status(created.secretUrl)
                assertTrue(status.isAvailable, "status=${status.state}")

                val revealed = client.secrets.reveal(created.secretUrl, guest = true)
                assertTrue(revealed.value.startsWith("live-test "))

                assertFailsWith<NotFoundException> {
                    client.secrets.reveal(created.secret.identifier, guest = true)
                }
            }
        }

    @Test
    fun `authenticated conceal with passphrase, receipt and burn`() =
        runTest {
            authenticatedClient().use { client ->
                assertTrue(client.version().version.isNotBlank())

                val created = client.secrets.conceal("burn me", ttl = 5.minutes, passphrase = "pw-live")
                assertEquals(true, created.secret.hasPassphrase)

                val receipt = client.receipts.show(created.receipt.identifier)
                assertEquals(created.receipt.identifier, receipt.identifier)

                val memo = "onetimesecret-kotlin live test"
                val updated = client.receipts.update(created.receipt.identifier, memo = memo)
                assertEquals(memo, updated.memo)

                assertTrue(client.receipts.recent().any { it.identifier == created.receipt.identifier })

                val burned = client.receipts.burn(created.receipt.identifier, passphrase = "pw-live")
                assertEquals(ReceiptState.BURNED, burned.state)
            }
        }
}
