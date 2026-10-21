package com.onetimesecret.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.Credentials as OkHttpCredentials

/** Behaviour of the HTTP layer itself: headers on the wire, timeouts and cancellation. */
class TransportTest : MockServerTest() {
    @Test
    fun `User-Agent names the SDK and OkHttp versions and carries the configured suffix`() =
        runTest {
            val suffixed = newClient { userAgentSuffix = "  MyApp/1.2  " }
            enqueueJson(Fixtures.statusResponse)

            suffixed.status()

            assertEquals(
                "onetimesecret-kotlin/${OnetimeSecretClient.VERSION} (okhttp/${OkHttp.VERSION}) MyApp/1.2",
                takeRequest().headers["User-Agent"],
            )
            suffixed.close()
        }

    @Test
    fun `credentials are sent as HTTP Basic`() =
        runTest {
            enqueueJson(Fixtures.statusResponse)
            client.status()
            assertEquals(OkHttpCredentials.basic("ur1abc23def", "tok_test"), takeRequest().headers["Authorization"])
        }

    @Test
    fun `a response that never arrives surfaces as TimeoutException after readTimeout`() =
        runTest {
            val impatient = newClient { readTimeout = 250.milliseconds }
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())

            val e = assertFailsWith<TimeoutException> { impatient.status() }

            assertIs<SocketTimeoutException>(e.cause)
            assertTrue(e.message!!.contains("timed out"), e.message)
            assertEquals(1, server.requestCount, "no retry when maxRetries is 0")
            impatient.close()
        }

    @Test
    fun `cancelling the coroutine cancels the OkHttp call`() {
        val callCancelled = CountDownLatch(1)
        val listener =
            object : EventListener() {
                override fun canceled(call: Call) {
                    callCancelled.countDown()
                }
            }
        val observed = newClient { okHttpClient = OkHttpClient.Builder().eventListener(listener).build() }
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())

        runBlocking {
            val job = launch(Dispatchers.IO) { observed.status() }
            takeRequest() // blocks until the request has reached the server, so the call is in flight
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }

        assertTrue(callCancelled.await(5, TimeUnit.SECONDS), "OkHttp never saw the cancellation")
        observed.close()
    }
}
