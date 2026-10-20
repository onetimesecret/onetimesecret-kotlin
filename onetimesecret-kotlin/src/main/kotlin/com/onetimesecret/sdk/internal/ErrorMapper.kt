package com.onetimesecret.sdk.internal

import com.onetimesecret.sdk.AccountRequiredException
import com.onetimesecret.sdk.ApiException
import com.onetimesecret.sdk.AuthenticationException
import com.onetimesecret.sdk.BadRequestException
import com.onetimesecret.sdk.EntitlementException
import com.onetimesecret.sdk.ForbiddenException
import com.onetimesecret.sdk.NotFoundException
import com.onetimesecret.sdk.RateLimitException
import com.onetimesecret.sdk.ServerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Maps an HTTP error response to the typed exception hierarchy, following the
 * API's ADR-013 contract: `{ error, error_type, ... }` with class-specific
 * extras (`field`, `retry_after`, `entitlement`, `request_id`).
 *
 * `error_type` wins over the status code when both are present and the type
 * is one we know; the status code is the fallback for everything else.
 */
internal object ErrorMapper {
    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_UNPROCESSABLE = 422
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR_MIN = 500

    // The account requirement can arrive in any of several slots depending on
    // the endpoint: as the FormError `field`, the i18n `error_key`, or a
    // `code` such as "GUEST_CONCEAL_REQUIRES_ACCOUNT".
    private val ACCOUNT_REQUIRED_KEYS = listOf("error_type", "field", "error_key", "code")
    private val ACCOUNT_REQUIRED_PATTERN =
        Regex("(?:^|[^a-z0-9])(requires[_-]?account|account[_-]?required)(?:$|[^a-z0-9])", RegexOption.IGNORE_CASE)

    fun map(
        statusCode: Int,
        body: String?,
        retryAfterHeader: String?,
        json: Json,
    ): ApiException {
        val fields = parse(body, json)
        val errorType = fields.string("error_type")
        val message = fields.string("error") ?: fields.string("message")
        val requestId = fields.string("request_id")
        val field = fields.string("field")

        if (ACCOUNT_REQUIRED_KEYS.any { key ->
                fields.string(key)?.let(ACCOUNT_REQUIRED_PATTERN::containsMatchIn) ==
                    true
            }
        ) {
            return AccountRequiredException(statusCode, errorType, message, requestId, field, body)
        }

        return when (errorType) {
            "FormError" -> {
                BadRequestException(statusCode, errorType, message, requestId, field, body)
            }

            "RecordNotFound", "NotFound" -> {
                NotFoundException(statusCode, errorType, message, requestId, field, body)
            }

            "Forbidden", "GuestRoutesDisabled" -> {
                ForbiddenException(statusCode, errorType, message, requestId, field, body)
            }

            "EntitlementRequired" -> {
                EntitlementException(
                    statusCode,
                    errorType,
                    message,
                    fields.string("entitlement"),
                    requestId,
                    field,
                    body,
                )
            }

            "LimitExceeded" -> {
                RateLimitException(
                    statusCode,
                    errorType,
                    message,
                    retryAfter(fields, retryAfterHeader),
                    requestId,
                    field,
                    body,
                )
            }

            "ServerError" -> {
                ServerException(statusCode, errorType, message, requestId, field, body)
            }

            else -> {
                byStatus(statusCode, errorType, message, requestId, field, body, fields, retryAfterHeader)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun byStatus(
        statusCode: Int,
        errorType: String?,
        message: String?,
        requestId: String?,
        field: String?,
        body: String?,
        fields: JsonObject?,
        retryAfterHeader: String?,
    ): ApiException =
        when {
            statusCode == HTTP_BAD_REQUEST || statusCode == HTTP_UNPROCESSABLE -> {
                BadRequestException(statusCode, errorType, message, requestId, field, body)
            }

            statusCode == HTTP_UNAUTHORIZED -> {
                AuthenticationException(statusCode, errorType, message, requestId, field, body)
            }

            statusCode == HTTP_FORBIDDEN -> {
                ForbiddenException(statusCode, errorType, message, requestId, field, body)
            }

            statusCode == HTTP_NOT_FOUND -> {
                NotFoundException(statusCode, errorType, message, requestId, field, body)
            }

            statusCode == HTTP_TOO_MANY_REQUESTS -> {
                RateLimitException(
                    statusCode,
                    errorType,
                    message,
                    retryAfter(fields, retryAfterHeader),
                    requestId,
                    field,
                    body,
                )
            }

            statusCode >= HTTP_SERVER_ERROR_MIN -> {
                ServerException(statusCode, errorType, message, requestId, field, body)
            }

            else -> {
                ApiException(statusCode, errorType, message, requestId, field, body)
            }
        }

    private fun parse(
        body: String?,
        json: Json,
    ): JsonObject? {
        if (body.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    }

    private fun JsonObject?.string(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun retryAfter(
        fields: JsonObject?,
        header: String?,
    ): Long? {
        val primitive = fields?.get("retry_after") as? JsonPrimitive
        primitive?.longOrNull?.let { return it }
        primitive?.doubleOrNull?.let { return it.toLong() }
        primitive
            ?.contentOrNull
            ?.trim()
            ?.toLongOrNull()
            ?.let { return it }
        return header?.trim()?.toLongOrNull()
    }
}
