# Onetime Secret Kotlin SDK

[![CI](https://github.com/onetimesecret/onetimesecret-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/onetimesecret/onetimesecret-kotlin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.onetimesecret/onetimesecret-kotlin)](https://central.sonatype.com/artifact/com.onetimesecret/onetimesecret-kotlin)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![API reference](https://img.shields.io/badge/docs-API%20reference-blue)](https://onetimesecret.github.io/onetimesecret-kotlin/)

The official Kotlin client for the [Onetime Secret](https://onetimesecret.com)
API, for Android and the JVM. Share sensitive information through a link that
can only be viewed once.

- **Coroutines-first.** Every call is a `suspend` function; cancel the
  coroutine and the HTTP call is cancelled with it.
- **Built on OkHttp.** Bring your own `OkHttpClient` to share a connection
  pool, interceptors and TLS configuration with the rest of your app.
- **Typed.** Immutable models, a sealed exception hierarchy, and no raw JSON
  at the call site. Unknown fields and states from newer servers are
  tolerated rather than fatal.
- **Android-friendly.** Java 11 bytecode, no `java.time` in the API surface,
  consumer R8 rules shipped in the jar, nothing logged that could contain a
  secret.
- **API v2 only.** The current stable API. v1 is in maintenance mode and not
  offered here.

The Maven artifact is `com.onetimesecret:onetimesecret-kotlin`. The repository
is `onetimesecret-kotlin`, one of the [Onetime Secret SDK family](https://github.com/onetimesecret).
The API reference (Dokka) is published at
[onetimesecret.github.io/onetimesecret-kotlin](https://onetimesecret.github.io/onetimesecret-kotlin/).

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.onetimesecret:onetimesecret-kotlin:0.1.0")
}
```

Requirements: Kotlin 2.4+ (the `kotlinx-serialization` floor), Android 5.0+ (API 21, the OkHttp 5 floor) or
JDK 11+. The SDK depends on `okhttp`, `kotlinx-coroutines-core` and
`kotlinx-serialization-json`.

## Quick start

```kotlin
import com.onetimesecret.sdk.Credentials
import com.onetimesecret.sdk.OnetimeSecretClient
import kotlin.time.Duration.Companion.hours

val client = OnetimeSecretClient {
    baseUrl = "https://ca.onetimesecret.com"            // your region's API host (required)
    credentials = Credentials("ur1abc23defghijklmnop",  // customer extid (see below)
                              BuildConfig.ONETIME_API_TOKEN)
}

// Conceal a secret you already have
val created = client.secrets.conceal("hunter2", ttl = 1.hours, passphrase = "pw12")
created.secretUrl            // give this to the recipient
created.receipt.identifier   // keep this to check on it or burn it

// Generate a random secret server-side
val generated = client.secrets.generate(length = 24, ttl = 24.hours)
generated.secret.value       // the generated value, shown to you once

// Reveal (consume) a secret -- one-time only
val revealed = client.secrets.reveal(created.secretUrl, passphrase = "pw12")
revealed.value               // "hunter2"

// Service status (no credentials needed)
client.status().status
```

On Android, call from a coroutine scope that outlives the request, for example
`viewModelScope`:

```kotlin
class ShareViewModel(private val client: OnetimeSecretClient) : ViewModel() {
    fun share(text: String) = viewModelScope.launch {
        try {
            val result = client.secrets.conceal(text, ttl = 1.hours)
            _link.value = result.secretUrl
        } catch (e: OnetimeSecretException) {
            _error.value = e.message
        }
    }
}
```

Create one client per base URL and account and share it (singleton, DI graph,
`Application` field). It is immutable and thread-safe.

### Runnable example

[`examples/roundtrip`](examples/roundtrip/src/main/kotlin/com/onetimesecret/examples/RoundTrip.kt)
conceals a secret, reads its receipt, reveals the secret and confirms the
receipt's state changed. It builds against the SDK source in this repository:

```sh
ONETIME_BASE_URL=https://eu.onetimesecret.com ./gradlew :examples:roundtrip:run
```

Without `ONETIME_CUSTOMER_EXTID` and `ONETIME_API_TOKEN` it runs anonymously
on the guest endpoints; set them to run as your account. Point
`ONETIME_BASE_URL` at a self-hosted deployment to exercise that instead.

### Authentication

Authentication uses HTTP Basic, where the **customer extid** is the username
and your **API token** is the password.

The customer extid is a short, opaque identifier that begins with `ur`, for
example `ur1abc23def`. It is shown, with a copy button, at the bottom of the
user menu when you are signed in. Other identifiers the API uses are not
interchangeable with it. The format is checked when you construct
`Credentials`, so a value of the wrong kind fails immediately rather than at
the first request:

```kotlin
Credentials("acct-123", token)
// ConfigurationException: customerExtid "acct-123" is not a customer extid:
// extids begin with "ur" (e.g. "ur1abc23def"). ...
```

Never hard-code the API token. On Android, inject it at build time
(`buildConfigField`) or fetch it from your own backend; the token grants full
access to the account's secrets.

### Base URL (required)

`baseUrl` must be the API host for your region, your self-hosted domain, or
your custom domain. There is no default: deployments are region-isolated for
data sovereignty, so the client cannot guess one for you.

| Region               | Host                           |
|----------------------|--------------------------------|
| United States        | `https://us.onetimesecret.com` |
| Europe               | `https://eu.onetimesecret.com` |
| United Kingdom       | `https://uk.onetimesecret.com` |
| Canada               | `https://ca.onetimesecret.com` |
| Aotearoa New Zealand | `https://nz.onetimesecret.com` |

The apex `onetimesecret.com` is the company website, not an API host, and is
rejected with a helpful error.

### Configuration

| Option            | Default        | Notes                                                                 |
|-------------------|----------------|-----------------------------------------------------------------------|
| `baseUrl`         | **required**   | Region, self-hosted or custom domain                                  |
| `credentials`     | `null`         | `Credentials(customerExtid, apiToken)`; `null` for anonymous use      |
| `connectTimeout`  | `10.seconds`   |                                                                       |
| `readTimeout`     | `30.seconds`   |                                                                       |
| `writeTimeout`    | `30.seconds`   |                                                                       |
| `maxRetries`      | `2`            | Retries for idempotent (GET) requests on network errors and 502/503/504 |
| `okHttpClient`    | `null`         | An existing client to derive from (interceptors, pool, TLS are shared) |
| `userAgentSuffix` | `null`         | Appended to the SDK `User-Agent`, e.g. `"MyApp/1.2"`                   |

To see requests on the wire during development, pass an `OkHttpClient` with
[`HttpLoggingInterceptor`](https://square.github.io/okhttp/features/interceptors/).
Keep it at `HEADERS` or below outside debug builds: `BODY` would log secret
values and your `Authorization` header.

## Anonymous and guest usage

A client created without credentials is anonymous and can use the public and
guest endpoints. The `guest = true` flag selects the explicit `/guest/` routes,
which self-hosted operators can switch off; the account routes also accept
anonymous calls on most deployments.

```kotlin
val guest = OnetimeSecretClient { baseUrl = "https://ca.onetimesecret.com" }
guest.secrets.conceal("no account needed", guest = true)
```

When a deployment requires an account for an operation, the SDK throws
`AccountRequiredException` (a subtype of `AuthenticationException`).

## Resources

### `client.secrets`

| Method                                                                                   | Returns          |
|------------------------------------------------------------------------------------------|------------------|
| `conceal(secret, ttl, passphrase, recipient, shareDomain, displayType, guest)`           | `ConcealResult`  |
| `generate(ttl, passphrase, recipient, shareDomain, displayType, length, characterSets, guest)` | `ConcealResult` |
| `reveal(keyOrUrl, passphrase, guest)`                                                    | `RevealedSecret` |
| `show(keyOrUrl, guest)`                                                                  | `Secret`         |
| `status(keyOrUrl)`                                                                       | `SecretStatus`   |
| `status(keysOrUrls)`                                                                     | `List<Secret>`   |

`reveal`, `show` and `status` accept either a bare identifier or the full link
the recipient was given.

### `client.receipts`

| Method                            | Returns         |
|-----------------------------------|-----------------|
| `show(keyOrUrl, guest)`           | `Receipt`       |
| `recent()`                        | `List<Receipt>` |
| `burn(keyOrUrl, passphrase, guest)` | `Receipt`     |
| `update(keyOrUrl, memo)`          | `Receipt`       |

### Meta

`client.status()`, `client.version()` (requires credentials),
`client.supportedLocales()`.

## Models

Responses are mapped to immutable data classes under
`com.onetimesecret.sdk.model`: `Secret`, `Receipt`, `ConcealResult`,
`RevealedSecret`, `SecretStatus`, and the enums `SecretState`, `ReceiptState`,
`DisplayType`, `SecretKind`.

The API grew out of a form-encoded v1 and still sends many scalars as strings
(`"true"`, `"3600"`); the SDK normalises them to `Boolean?` and `Long?`.
Timestamps are Unix epoch seconds. Fields the service did not send are `null`.
A state this SDK version does not know maps to `UNKNOWN` rather than failing,
and unknown JSON fields are ignored, so a newer server does not break an older
app.

`Secret` and `RevealedSecret` redact the secret value in `toString()`;
`Credentials` redacts the token.

## Errors

Every failure is thrown as a subtype of `OnetimeSecretException`. The
hierarchy is sealed, so a `when` over it is exhaustive:

```kotlin
try {
    client.secrets.reveal(key)
} catch (e: NotFoundException) {
    // already revealed, burned or expired
} catch (e: RateLimitException) {
    delay((e.retryAfterSeconds ?: 5) * 1000)
} catch (e: ApiException) {
    e.statusCode    // 4xx / 5xx
    e.errorType     // machine-readable, e.g. "FormError"
    e.errorMessage  // user-facing text from the API
    e.requestId     // quote this when reporting a problem
} catch (e: TransportException) {
    // the request never completed; e.cause is the IOException
}
```

| Exception                    | When                                                                                  |
|------------------------------|---------------------------------------------------------------------------------------|
| `ConfigurationException`     | bad `baseUrl`, malformed customer extid, blank token, negative `maxRetries` (at construction) |
| `TransportException`         | network failure or unparseable response body                                          |
| `TimeoutException`           | connect/read/write timeout (subtype of `TransportException`)                          |
| `ApiException`               | any HTTP 4xx/5xx not covered below                                                    |
| `BadRequestException`        | 400 / 422 / `FormError` (see `field`)                                                 |
| `AuthenticationException`    | 401                                                                                   |
| `AccountRequiredException`   | the operation needs an account (`requires_account`); subtype of `AuthenticationException` |
| `ForbiddenException`         | 403 / `Forbidden` / `GuestRoutesDisabled`                                             |
| `EntitlementException`       | `EntitlementRequired` (see `entitlement`); subtype of `ForbiddenException`            |
| `NotFoundException`          | 404 / `RecordNotFound`                                                                |
| `RateLimitException`         | 429 / `LimitExceeded` (see `retryAfterSeconds`)                                       |
| `ServerException`            | 5xx (GETs are retried on 502/503/504 first)                                           |
| `SecretNotRevealedException` | the reveal succeeded but another request consumed the secret first                    |

A wrong passphrase on `reveal` or `burn` arrives as a `BadRequestException`
whose `errorMessage` is localized by the service.

The mapping follows the API's error contract: every error body carries a
user-facing `error` and a machine-readable `error_type`, and `error_type` wins
over the status code when both are present.

## Android notes

- **ProGuard/R8.** The jar ships consumer rules under `META-INF/proguard/`,
  picked up automatically. kotlinx.serialization ships its own.
- **Main thread.** Calls suspend; they never block the thread they are called
  from. Run them in `viewModelScope`, `lifecycleScope` or a `WorkManager`
  coroutine worker.
- **Network security.** All regional hosts use TLS. For a self-hosted
  deployment on plain HTTP in development, allow it in your
  [network security config](https://developer.android.com/privacy-and-security/security-config).
- **Minimum SDK.** 21, inherited from OkHttp 5. The SDK itself uses nothing
  newer.

## Development

```sh
./gradlew build          # format check, detekt, API check, tests
./gradlew spotlessApply  # fix formatting
./gradlew apiDump        # after an intentional public API change
./gradlew dokkaGenerate  # API docs -> onetimesecret-kotlin/build/dokka/html
./gradlew :examples:roundtrip:run  # end-to-end example (see above)
```

The Docs workflow publishes the Dokka output to
[GitHub Pages](https://onetimesecret.github.io/onetimesecret-kotlin/) on every
push to `main` that touches the library or the build.

Unit tests run against an in-process [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver)
and need no network. The live tests in `LiveApiTest` hit a real deployment and
only run when `ONETIME_LIVE=1` is set:

```sh
ONETIME_LIVE=1 ONETIME_BASE_URL=https://eu.onetimesecret.com \
ONETIME_CUSTOMER_EXTID=ur... ONETIME_API_TOKEN=... \
./gradlew :onetimesecret-kotlin:test --tests 'com.onetimesecret.sdk.LiveApiTest'
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions and
[docs/releasing.md](docs/releasing.md) for how releases are cut.

## License

[MIT](LICENSE).
