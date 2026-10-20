package com.onetimesecret.sdk.internal

import com.onetimesecret.sdk.OnetimeSecretConfig
import com.onetimesecret.sdk.ServerException
import com.onetimesecret.sdk.TimeoutException
import com.onetimesecret.sdk.TransportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.toJavaDuration
import okhttp3.Credentials as OkHttpCredentials

/**
 * The single place HTTP happens. Resources describe *what* to call; this class
 * owns headers, auth, JSON, retries, cancellation and error mapping.
 */
internal class HttpTransport(
    private val config: OnetimeSecretConfig,
    val json: Json = defaultJson,
) {
    private val ownsClient = config.okHttpClient == null
    private val client: OkHttpClient =
        (config.okHttpClient?.newBuilder() ?: OkHttpClient.Builder())
            .connectTimeout(config.connectTimeout.toJavaDuration())
            .readTimeout(config.readTimeout.toJavaDuration())
            .writeTimeout(config.writeTimeout.toJavaDuration())
            // Idempotent retries are handled here with backoff; OkHttp's own
            // silent retry would double up and hide what happened.
            .retryOnConnectionFailure(false)
            .build()

    private val authorization: String? =
        config.credentials?.let { OkHttpCredentials.basic(it.customerExtid, it.apiToken) }

    private val userAgent: String =
        buildString {
            append("onetimesecret-kotlin/").append(BuildInfo.VERSION)
            append(" (okhttp/").append(okhttp3.OkHttp.VERSION).append(')')
            config.userAgentSuffix?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
        }

    /** Base path for every v2 call; `segments` are appended as encoded path segments. */
    private fun url(segments: List<String>): HttpUrl =
        config.baseUrl
            .newBuilder()
            .apply {
                addPathSegment("api")
                addPathSegment("v2")
                segments.forEach { addPathSegment(it) }
            }.build()

    suspend fun <T> get(
        segments: List<String>,
        deserializer: DeserializationStrategy<T>,
    ): T = execute(Request.Builder().url(url(segments)).get(), idempotent = true, deserializer)

    suspend fun <T> post(
        segments: List<String>,
        body: JsonElement? = null,
        deserializer: DeserializationStrategy<T>,
    ): T = execute(Request.Builder().url(url(segments)).post(jsonBody(body)), idempotent = false, deserializer)

    suspend fun <T> patch(
        segments: List<String>,
        body: JsonElement? = null,
        deserializer: DeserializationStrategy<T>,
    ): T = execute(Request.Builder().url(url(segments)).patch(jsonBody(body)), idempotent = false, deserializer)

    /** Releases resources the SDK created. A caller-supplied [OkHttpClient] is left alone. */
    fun close() {
        if (ownsClient) {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun jsonBody(body: JsonElement?): RequestBody =
        // Every v2 write endpoint declares a required JSON body, so an empty
        // object is sent when the caller has nothing to say.
        (body?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}").toRequestBody(JSON_MEDIA_TYPE)

    private suspend fun <T> execute(
        builder: Request.Builder,
        idempotent: Boolean,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val request =
            builder
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .apply { authorization?.let { header("Authorization", it) } }
                .build()

        val maxAttempts = if (idempotent) config.maxRetries + 1 else 1
        var attempt = 0
        while (true) {
            attempt++
            try {
                return client.newCall(request).await().use { response -> handle(response, deserializer) }
            } catch (e: TransportException) {
                if (attempt >= maxAttempts) throw e
            } catch (e: ServerException) {
                if (attempt >= maxAttempts || e.statusCode !in RETRYABLE_STATUSES) throw e
            }
            delay(backoffMillis(attempt))
        }
    }

    private fun <T> handle(
        response: Response,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw ErrorMapper.map(response.code, body, response.header("Retry-After"), json)
        }
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            throw TransportException("Could not parse the API response (HTTP ${response.code}): ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw TransportException("Unexpected API response shape (HTTP ${response.code}): ${e.message}", e)
        }
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        continuation.resume(response) { _, _, _ -> response.close() }
                    }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isCancelled) return
                        continuation.resumeWithException(translate(e))
                    }
                },
            )
            continuation.invokeOnCancellation { cancel() }
        }

    private fun translate(e: IOException): Exception =
        when {
            e is SocketTimeoutException || (e is InterruptedIOException && e.message == "timeout") -> {
                TimeoutException("Request to ${config.baseUrl.host} timed out: ${e.message}", e)
            }

            e.cause is CancellationException -> {
                e.cause as CancellationException
            }

            else -> {
                TransportException("Request to ${config.baseUrl.host} failed: ${e.message}", e)
            }
        }

    private fun backoffMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS shl (attempt - 1)

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val RETRYABLE_STATUSES = setOf(502, 503, 504)
        private const val BASE_BACKOFF_MILLIS = 250L

        val defaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
                encodeDefaults = false
            }
    }
}
