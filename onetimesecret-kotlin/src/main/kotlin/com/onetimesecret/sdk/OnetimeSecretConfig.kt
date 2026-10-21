package com.onetimesecret.sdk

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * API credentials: HTTP Basic auth where the username is your **customer
 * extid** and the password is your **API token**.
 *
 * The customer extid is a short, opaque identifier that begins with `ur` (for
 * example `ur1abc23def`). It is shown, with a copy button, at the bottom of
 * the user menu when you are signed in. Other identifiers the service uses
 * are not interchangeable with it, so the format is checked here and a value
 * of the wrong kind fails at construction rather than at the first request.
 *
 * @throws ConfigurationException if [customerExtid] is not an extid or [apiToken] is blank.
 */
public class Credentials(
    public val customerExtid: String,
    public val apiToken: String,
) {
    init {
        if (!CUSTOMER_EXTID_PATTERN.matches(customerExtid)) {
            throw ConfigurationException(
                "customerExtid \"$customerExtid\" is not a customer extid: extids begin with \"ur\" " +
                    "(e.g. \"ur1abc23def\"). $CUSTOMER_EXTID_HINT",
            )
        }
        if (apiToken.isBlank()) {
            throw ConfigurationException("apiToken must not be blank.")
        }
    }

    /** Redacts the token; safe to log. */
    override fun toString(): String = "Credentials(customerExtid=$customerExtid, apiToken=****)"

    override fun equals(other: Any?): Boolean =
        other is Credentials && other.customerExtid == customerExtid && other.apiToken == apiToken

    override fun hashCode(): Int = 31 * customerExtid.hashCode() + apiToken.hashCode()

    private companion object {
        val CUSTOMER_EXTID_PATTERN = Regex("^ur[a-z0-9]+$", RegexOption.IGNORE_CASE)
    }
}

// A `const val` in a private companion still becomes a public static field in
// bytecode, so the hint lives at file level to keep it out of the API surface.
private const val CUSTOMER_EXTID_HINT =
    "Your customer extid is the \"ur…\" identifier at the bottom of the user menu when you are signed in."

/**
 * Immutable client configuration. Build one with [OnetimeSecretConfig.Builder]
 * or the [OnetimeSecretClient] factory DSL:
 *
 * ```kotlin
 * val client = OnetimeSecretClient {
 *     baseUrl = "https://ca.onetimesecret.com"
 *     credentials = Credentials("ur1abc23def", apiToken)
 * }
 * ```
 *
 * @property baseUrl The API host for your region, self-hosted domain, or custom domain.
 * @property credentials Account credentials, or `null` for an anonymous client.
 * @property connectTimeout Time allowed to establish a connection.
 * @property readTimeout Time allowed between bytes of the response.
 * @property writeTimeout Time allowed between bytes of the request.
 * @property maxRetries Retries for idempotent (GET) requests that fail with a
 *   network error or a 502/503/504. Non-idempotent requests are never retried.
 * @property okHttpClient An existing [OkHttpClient] to derive from. The SDK
 *   calls `newBuilder()` on it, so interceptors, connection pool, dispatcher
 *   and TLS configuration are shared with your app; timeouts from this config
 *   are applied on top. When `null`, the SDK creates its own.
 * @property userAgentSuffix Appended to the SDK's `User-Agent`, typically your
 *   app name and version.
 */
public class OnetimeSecretConfig private constructor(
    public val baseUrl: HttpUrl,
    public val credentials: Credentials?,
    public val connectTimeout: Duration,
    public val readTimeout: Duration,
    public val writeTimeout: Duration,
    public val maxRetries: Int,
    public val okHttpClient: OkHttpClient?,
    public val userAgentSuffix: String?,
) {
    /** Mutable builder; every property has a sensible default except [baseUrl]. */
    public class Builder {
        /**
         * **Required.** The API host for your region (`https://us.onetimesecret.com`,
         * `https://eu.onetimesecret.com`, `https://uk.onetimesecret.com`,
         * `https://ca.onetimesecret.com`, `https://nz.onetimesecret.com`), your
         * self-hosted domain, or your custom domain. There is no default:
         * deployments are region-isolated for data sovereignty, so the SDK cannot
         * guess one for you. The apex `onetimesecret.com` is the company website,
         * not an API host, and is rejected.
         */
        public var baseUrl: String? = null

        /** Account credentials. Leave `null` for anonymous / guest usage. */
        public var credentials: Credentials? = null

        public var connectTimeout: Duration = 10.seconds
        public var readTimeout: Duration = 30.seconds
        public var writeTimeout: Duration = 30.seconds
        public var maxRetries: Int = 2
        public var okHttpClient: OkHttpClient? = null
        public var userAgentSuffix: String? = null

        /** Convenience for `credentials = Credentials(customerExtid, apiToken)`. */
        public fun credentials(
            customerExtid: String,
            apiToken: String,
        ): Builder =
            apply {
                credentials = Credentials(customerExtid, apiToken)
            }

        /** @throws ConfigurationException when the configuration is invalid. */
        public fun build(): OnetimeSecretConfig {
            val url = parseBaseUrl(baseUrl)
            if (maxRetries < 0) throw ConfigurationException("maxRetries must be >= 0, was $maxRetries.")
            return OnetimeSecretConfig(
                baseUrl = url,
                credentials = credentials,
                connectTimeout = connectTimeout,
                readTimeout = readTimeout,
                writeTimeout = writeTimeout,
                maxRetries = maxRetries,
                okHttpClient = okHttpClient,
                userAgentSuffix = userAgentSuffix,
            )
        }
    }

    public companion object {
        /** Builds a configuration with a DSL block. */
        public inline operator fun invoke(block: Builder.() -> Unit): OnetimeSecretConfig =
            Builder().apply(block).build()

        private val APEX_HOSTS = setOf("onetimesecret.com", "www.onetimesecret.com")
        private const val REGION_EXAMPLES =
            "https://us.onetimesecret.com, https://eu.onetimesecret.com, https://uk.onetimesecret.com, " +
                "https://ca.onetimesecret.com or https://nz.onetimesecret.com"

        internal fun parseBaseUrl(raw: String?): HttpUrl {
            val value = raw?.trim()?.trimEnd('/')
            if (value.isNullOrEmpty()) {
                throw ConfigurationException(
                    "baseUrl is required. Use your region's API host (e.g. $REGION_EXAMPLES), " +
                        "your self-hosted domain, or your custom domain.",
                )
            }
            val url =
                value.toHttpUrlOrNull()
                    ?: throw ConfigurationException("baseUrl \"$value\" is not a valid http(s) URL.")
            if (url.host.lowercase() in APEX_HOSTS) {
                throw ConfigurationException(
                    "baseUrl \"$value\" is the Onetime Secret website, not an API host. " +
                        "Use your region's API host (e.g. $REGION_EXAMPLES), your self-hosted domain, " +
                        "or your custom domain.",
                )
            }
            return url
        }
    }
}
