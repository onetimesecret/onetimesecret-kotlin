// End-to-end round trip against a real server: conceal a secret, read the
// receipt, reveal the secret, then confirm the receipt's state changed.
//
//   ONETIME_BASE_URL=https://eu.onetimesecret.com ./gradlew :examples:roundtrip:run
//
// Set ONETIME_CUSTOMER_EXTID and ONETIME_API_TOKEN to run as your account;
// without them the example is anonymous and uses the guest endpoints. Point
// ONETIME_BASE_URL at a self-hosted deployment to run against that instead.
package com.onetimesecret.examples

import com.onetimesecret.sdk.OnetimeSecretClient
import com.onetimesecret.sdk.OnetimeSecretException
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

private const val DEFAULT_BASE_URL = "https://eu.onetimesecret.com"

fun main() {
    val baseUrl = System.getenv("ONETIME_BASE_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    val extid = System.getenv("ONETIME_CUSTOMER_EXTID")?.takeIf { it.isNotBlank() }
    val token = System.getenv("ONETIME_API_TOKEN")?.takeIf { it.isNotBlank() }

    val client =
        OnetimeSecretClient {
            this.baseUrl = baseUrl
            if (extid != null && token != null) credentials(extid, token)
            userAgentSuffix = "roundtrip-example"
        }
    // Anonymous callers use the /guest/ endpoints; accounts use the regular ones.
    val guest = !client.isAuthenticated
    println("server: ${client.config.baseUrl} (${if (guest) "anonymous" else "authenticated"})")

    try {
        client.use { roundTrip(it, guest) }
    } catch (e: OnetimeSecretException) {
        System.err.println("${e::class.simpleName}: ${e.message}")
        exitProcess(1)
    }
}

private fun roundTrip(
    client: OnetimeSecretClient,
    guest: Boolean,
) = runBlocking {
    val created = client.secrets.conceal("hunter2", ttl = 5.minutes, guest = guest)
    val secretId = created.secret.identifier
    val receiptId = created.receipt.identifier
    println("secret:      $secretId")
    println("receipt:     $receiptId")
    println("share link:  ${created.secretUrl}")

    val before = client.receipts.show(receiptId, guest = guest)
    println("receipt state before reveal: ${before.state}")

    val revealed = client.secrets.reveal(secretId, guest = guest)
    println("revealed value:              ${revealed.value}")

    val after = client.receipts.show(receiptId, guest = guest)
    println("receipt state after reveal:  ${after.state}")

    if (after.state == before.state) {
        System.err.println("receipt state did not change (still ${after.state})")
        exitProcess(1)
    }
    println("ok: receipt moved from ${before.state} to ${after.state}")

    // A generated value is disclosed once, on the first receipt view, which
    // generate performs for you. The window is short and deployment-specific,
    // so a missing value is reported rather than treated as a failure.
    val generated = client.secrets.generate(length = 16, ttl = 5.minutes, guest = guest)
    println("generated secret:            ${generated.secret.identifier}")
    val shown = generated.value ?: "(not disclosed: ${generated.claimFailure ?: "window passed"})"
    println("generated value:             $shown")
    client.receipts.burn(generated.receipt.identifier, guest = guest)
    println("ok: generated secret burned")
}
