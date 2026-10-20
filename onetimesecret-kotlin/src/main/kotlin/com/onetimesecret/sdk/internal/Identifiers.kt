package com.onetimesecret.sdk.internal

/**
 * Accepts either a bare identifier or a full URL the user was given
 * (`https://eu.onetimesecret.com/secret/abc123`) and returns the identifier.
 */
internal object Identifiers {
    private val SECRET_IN_URL = Regex("/secret/([A-Za-z0-9]+)")
    private val RECEIPT_IN_URL = Regex("/(?:receipt|private|metadata)/([A-Za-z0-9]+)")

    fun secret(keyOrUrl: String): String = extract(keyOrUrl, SECRET_IN_URL, "secret")

    fun receipt(keyOrUrl: String): String = extract(keyOrUrl, RECEIPT_IN_URL, "receipt")

    private fun extract(
        input: String,
        pattern: Regex,
        what: String,
    ): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "A $what identifier is required." }
        pattern.find(trimmed)?.let { return it.groupValues[1] }
        require('/' !in trimmed && '?' !in trimmed && '#' !in trimmed) {
            "\"$trimmed\" is neither a $what identifier nor a $what URL."
        }
        return trimmed
    }
}
