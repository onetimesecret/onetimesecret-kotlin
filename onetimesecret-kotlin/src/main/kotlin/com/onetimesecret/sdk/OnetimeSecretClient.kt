package com.onetimesecret.sdk

import com.onetimesecret.sdk.internal.BuildInfo
import com.onetimesecret.sdk.internal.HttpTransport
import com.onetimesecret.sdk.model.SupportedLocales
import com.onetimesecret.sdk.model.SystemStatus
import com.onetimesecret.sdk.model.SystemVersion
import java.io.Closeable

/**
 * Entry point to the Onetime Secret API (v2).
 *
 * ```kotlin
 * val client = OnetimeSecretClient {
 *     baseUrl = "https://ca.onetimesecret.com"          // your region (required)
 *     credentials = Credentials("ur1abc23def", token)   // omit for anonymous use
 * }
 *
 * val result = client.secrets.conceal("hunter2", ttl = 1.hours, passphrase = "pw12")
 * share(result.secretUrl)
 *
 * val revealed = client.secrets.reveal(result.secret.identifier, passphrase = "pw12")
 * revealed.value // "hunter2" -- and now it is gone
 * ```
 *
 * A client is immutable and thread-safe: create one per base URL and account
 * and share it across your app (a singleton, a DI graph, an `Application`
 * field). All methods are `suspend` functions, so call them from a coroutine
 * such as `viewModelScope.launch { ... }` on Android.
 *
 * Failures are thrown as [OnetimeSecretException] subtypes; nothing is
 * returned as an error value.
 *
 * @property config The configuration this client was built with.
 * @property secrets Secret operations.
 * @property receipts Receipt operations.
 */
public class OnetimeSecretClient(
    public val config: OnetimeSecretConfig,
) : Closeable {
    private val transport = HttpTransport(config)

    public val secrets: Secrets = Secrets(transport, config.baseUrl)
    public val receipts: Receipts = Receipts(transport)

    /** `true` when the client was built with [Credentials]. */
    public val isAuthenticated: Boolean
        get() = config.credentials != null

    /** Service health. Works without credentials. */
    public suspend fun status(): SystemStatus =
        transport.get(listOf("status"), deserializer = SystemStatus.serializer())

    /** Service version. Requires credentials. */
    public suspend fun version(): SystemVersion =
        transport.get(listOf("version"), deserializer = SystemVersion.serializer())

    /** Locales the service can answer in. Works without credentials. */
    public suspend fun supportedLocales(): SupportedLocales =
        transport.get(listOf("supported-locales"), deserializer = SupportedLocales.serializer())

    /**
     * Releases the HTTP resources this client created. Optional: the JVM
     * reclaims them when the client is garbage collected, and an
     * [okhttp3.OkHttpClient] you supplied yourself is never touched.
     */
    override fun close(): Unit = transport.close()

    public companion object {
        /** The SDK version, as published. */
        public const val VERSION: String = BuildInfo.VERSION

        /** Builds a client with a DSL block over [OnetimeSecretConfig.Builder]. */
        public inline operator fun invoke(block: OnetimeSecretConfig.Builder.() -> Unit): OnetimeSecretClient =
            OnetimeSecretClient(OnetimeSecretConfig(block))
    }
}
