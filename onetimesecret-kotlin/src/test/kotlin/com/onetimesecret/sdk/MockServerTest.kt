package com.onetimesecret.sdk

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for tests that drive the client against a [MockWebServer].
 * Each test gets a fresh server and a client pointed at it.
 */
abstract class MockServerTest {
    protected lateinit var server: MockWebServer
    protected lateinit var client: OnetimeSecretClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        client = newClient()
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.close()
    }

    protected fun newClient(
        credentials: Credentials? = Credentials("ur1abc23def", "tok_test"),
        maxRetries: Int = 0,
        configure: OnetimeSecretConfig.Builder.() -> Unit = {},
    ): OnetimeSecretClient =
        OnetimeSecretClient {
            baseUrl = server.url("/").toString()
            this.credentials = credentials
            this.maxRetries = maxRetries
            readTimeout = 5.seconds
            connectTimeout = 5.seconds
            configure()
        }

    protected fun enqueueJson(
        body: String,
        code: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) {
        val builder =
            MockResponse
                .Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    protected fun takeRequest(): RecordedRequest = server.takeRequest()

    protected fun RecordedRequest.bodyString(): String = body?.utf8() ?: ""
}
