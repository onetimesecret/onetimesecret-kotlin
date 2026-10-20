package com.onetimesecret.sdk

/**
 * Root of every exception thrown by this SDK.
 *
 * The hierarchy is sealed so a `when` over it is exhaustive:
 *
 * ```kotlin
 * try {
 *     client.secrets.reveal(key)
 * } catch (e: OnetimeSecretException) {
 *     when (e) {
 *         is ApiException -> ...          // the service answered with 4xx/5xx
 *         is TransportException -> ...    // the request never completed
 *         is ConfigurationException -> ... // the client was built wrong
 *     }
 * }
 * ```
 */
public sealed class OnetimeSecretException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The client could not be constructed: a missing or malformed base URL, a
 * customer extid that is not an extid, or credentials that are only half
 * present. Thrown at construction so misconfiguration fails before the first
 * request rather than during it.
 */
public class ConfigurationException(
    message: String,
) : OnetimeSecretException(message)

/**
 * The request did not complete: DNS failure, connection refused, TLS error,
 * the connection dropping mid-response, or a malformed body. [cause] is the
 * underlying `IOException` (or serialization error) when one exists.
 */
public open class TransportException(
    message: String,
    cause: Throwable? = null,
) : OnetimeSecretException(message, cause)

/** The request exceeded the configured connect, read or write timeout. */
public class TimeoutException(
    message: String,
    cause: Throwable? = null,
) : TransportException(message, cause)

/**
 * The service accepted a reveal but returned no value.
 *
 * Reveal is an atomic one-time claim. When two requests race for the same
 * secret, the loser receives a successful metadata-only response; the value
 * went to the winner and is gone.
 */
public class SecretNotRevealedException(
    public val identifier: String,
) : OnetimeSecretException(
        "Secret \"$identifier\" was not revealed: the service returned no value, " +
            "most likely because another request consumed it first.",
    )

/**
 * The service answered with an HTTP 4xx or 5xx status.
 *
 * Fields follow the API's error contract (ADR-013): every error body carries a
 * user-facing [errorMessage] (the `error` field) and a machine-readable
 * [errorType] (the `error_type` field, e.g. `RecordNotFound`, `FormError`,
 * `LimitExceeded`). Subclasses map the common cases to types you can catch
 * directly; this class is the fallback for anything else.
 *
 * @property statusCode HTTP status code.
 * @property errorType The API's `error_type`, when present.
 * @property errorMessage The API's user-facing `error` text, when present.
 * @property requestId The `request_id` echoed by the API, useful when reporting a problem.
 * @property field For form errors, the request field the error refers to.
 * @property rawBody The unparsed response body, for diagnostics.
 */
public open class ApiException(
    public val statusCode: Int,
    public val errorType: String?,
    public val errorMessage: String?,
    public val requestId: String? = null,
    public val field: String? = null,
    public val rawBody: String? = null,
) : OnetimeSecretException(buildMessage(statusCode, errorType, errorMessage, requestId)) {
    private companion object {
        fun buildMessage(
            status: Int,
            type: String?,
            message: String?,
            requestId: String?,
        ): String =
            buildString {
                append("HTTP ").append(status)
                if (type != null) append(" ").append(type)
                if (message != null) append(": ").append(message)
                if (requestId != null) append(" (request_id=").append(requestId).append(')')
            }
    }
}

/** HTTP 400, or any `FormError`: the request was malformed or failed validation. */
public open class BadRequestException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/** HTTP 401: missing or rejected credentials. */
public open class AuthenticationException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/**
 * The operation needs an authenticated account but the client has none.
 *
 * The service reports this inside a 400 form-error body with
 * `field = "requires_account"`. Surfacing it as a [BadRequestException] sends
 * people auditing a request payload that was never the problem, so it gets its
 * own type under [AuthenticationException]. [field] is preserved.
 */
public class AccountRequiredException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : AuthenticationException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/** HTTP 403, `Forbidden`, or `GuestRoutesDisabled`. */
public open class ForbiddenException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/**
 * `EntitlementRequired`: the account's plan does not include this feature.
 *
 * @property entitlement The entitlement the API says is missing, when provided.
 */
public class EntitlementException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    public val entitlement: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ForbiddenException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/** HTTP 404 or `RecordNotFound`: the secret or receipt does not exist (or no longer does). */
public class NotFoundException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/**
 * HTTP 429 or `LimitExceeded`.
 *
 * @property retryAfterSeconds Seconds to wait before retrying, from the body's
 *   `retry_after` or the `Retry-After` header, when either is present.
 */
public class RateLimitException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    public val retryAfterSeconds: Long?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)

/** HTTP 5xx: the service failed. Idempotent requests are retried before this is thrown. */
public class ServerException(
    statusCode: Int,
    errorType: String?,
    errorMessage: String?,
    requestId: String? = null,
    field: String? = null,
    rawBody: String? = null,
) : ApiException(statusCode, errorType, errorMessage, requestId, field, rawBody)
