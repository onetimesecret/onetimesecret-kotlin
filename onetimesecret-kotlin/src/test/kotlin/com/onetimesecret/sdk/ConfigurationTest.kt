package com.onetimesecret.sdk

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConfigurationTest {
    @Test
    fun `baseUrl is required`() {
        val e = assertFailsWith<ConfigurationException> { OnetimeSecretConfig { } }
        assertTrue(e.message!!.contains("baseUrl is required"))
        assertTrue(e.message!!.contains("https://eu.onetimesecret.com"), "the error names the regional hosts")
    }

    @Test
    fun `the apex domain is the website, not an API host`() {
        for (host in listOf("https://onetimesecret.com", "https://www.onetimesecret.com/")) {
            val e = assertFailsWith<ConfigurationException> { OnetimeSecretConfig { baseUrl = host } }
            assertTrue(e.message!!.contains("not an API host"), e.message)
        }
    }

    @Test
    fun `regional, self-hosted and custom hosts are accepted and normalised`() {
        assertEquals(
            "https://ca.onetimesecret.com/",
            OnetimeSecretConfig {
                baseUrl = "https://ca.onetimesecret.com/"
            }.baseUrl.toString(),
        )
        assertEquals(
            "https://secrets.example.com/",
            OnetimeSecretConfig {
                baseUrl = " https://secrets.example.com "
            }.baseUrl.toString(),
        )
        assertEquals(
            "http://localhost:3000/",
            OnetimeSecretConfig { baseUrl = "http://localhost:3000" }.baseUrl.toString(),
        )
    }

    @Test
    fun `garbage base URLs are rejected`() {
        assertFailsWith<ConfigurationException> { OnetimeSecretConfig { baseUrl = "not a url" } }
        assertFailsWith<ConfigurationException> { OnetimeSecretConfig { baseUrl = "ftp://x.example" } }
    }

    @Test
    fun `customer extid format is checked at construction`() {
        val e = assertFailsWith<ConfigurationException> { Credentials("acct-123", "tok") }
        assertTrue(e.message!!.contains("not a customer extid"))
        assertFailsWith<ConfigurationException> { Credentials("", "tok") }
        assertFailsWith<ConfigurationException> { Credentials("ur1abc23def", " ") }
        Credentials("UR1ABC23DEF", "tok") // case-insensitive
    }

    @Test
    fun `credentials never print the token`() {
        val creds = Credentials("ur1abc23def", "tok_supersecret")
        assertFalse(creds.toString().contains("tok_supersecret"))
        assertTrue(creds.toString().contains("ur1abc23def"))
        assertEquals(Credentials("ur1abc23def", "tok_supersecret"), creds)
    }

    @Test
    fun `defaults and overrides`() {
        val config =
            OnetimeSecretConfig {
                baseUrl = "https://ca.onetimesecret.com"
                credentials("ur1abc23def", "tok")
                readTimeout = 3.seconds
                userAgentSuffix = "MyApp/1.2"
            }
        assertEquals(10.seconds, config.connectTimeout)
        assertEquals(3.seconds, config.readTimeout)
        assertEquals(2, config.maxRetries)
        assertEquals("ur1abc23def", config.credentials?.customerExtid)
        assertEquals("MyApp/1.2", config.userAgentSuffix)
    }

    @Test
    fun `negative retries are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OnetimeSecretConfig {
                baseUrl = "https://ca.onetimesecret.com"
                maxRetries = -1
            }
        }
    }

    @Test
    fun `a caller-supplied OkHttpClient is not shut down by close`() {
        val shared = OkHttpClient()
        val client =
            OnetimeSecretClient {
                baseUrl = "https://ca.onetimesecret.com"
                okHttpClient = shared
            }
        client.close()
        assertFalse(shared.dispatcher.executorService.isShutdown)
    }

    @Test
    fun `VERSION is populated from the build`() {
        assertTrue(Regex("""\d+\.\d+\.\d+.*""").matches(OnetimeSecretClient.VERSION), OnetimeSecretClient.VERSION)
    }
}
