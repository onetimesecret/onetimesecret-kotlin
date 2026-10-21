# AGENTS.md

Orientation for coding agents and new contributors. Humans: CONTRIBUTING.md
has the same material with more context.

## What this is

`com.onetimesecret:onetimesecret-kotlin`: the official Kotlin client for the
Onetime Secret API v2, for Android (API 21+) and the JVM (11+). Coroutines
over OkHttp, kotlinx.serialization for JSON. It belongs to a family of SDKs
(`onetimesecret-py`, `onetimesecret-rust`, `onetimesecret-ruby`, ...) that
share the API contract and release model but are each idiomatic to their
language, not ports of one another.

## Commands

```sh
./gradlew build          # spotless check, detekt, apiCheck, compile (warnings as errors), unit tests
./gradlew spotlessApply  # fix formatting (ktlint for Kotlin and .kts; trailing whitespace for md/yml/toml)
./gradlew apiDump        # after an intentional public API change; commit the .api diff
./gradlew dokkaGenerate  # API docs -> onetimesecret-kotlin/build/dokka/html
ONETIME_BASE_URL=... ./gradlew :examples:roundtrip:run  # end-to-end example against a real server
```

Always use the wrapper. The build needs JDK 17+ to launch Gradle; the
toolchain resolver provisions the JDK the library compiles with.

## Layout

The repository is `onetimesecret-kotlin` and so is the one Gradle module
inside it, so sources live under `onetimesecret-kotlin/onetimesecret-kotlin/`.
That is deliberate: the module name is the Maven artifactId.

- `onetimesecret-kotlin/src/main/kotlin/com/onetimesecret/sdk/`:
  `OnetimeSecretClient`, `OnetimeSecretConfig` (+ `Credentials`), the
  `Secrets` and `Receipts` resource groups, `Exceptions.kt`.
- `.../sdk/model/`: public `@Serializable` data classes and enums.
- `.../sdk/internal/`: `HttpTransport` (the only place HTTP happens),
  `ErrorMapper` (`error_type`/status -> exception), `Envelopes`,
  `Identifiers`, `LenientSerializers`. Excluded from the API dump.
- `onetimesecret-kotlin/api/onetimesecret-kotlin.api`: generated public API
  dump; never hand-edit.
- `onetimesecret-kotlin/build/generated/.../BuildInfo.kt`: generated from
  `VERSION_NAME` in `gradle.properties` at build time.
- `examples/roundtrip/`: runnable end-to-end example (`application`
  subproject, built by `build`, never published, ignored by `apiCheck`).
- `gradle/libs.versions.toml`: every dependency and plugin version.
- `config/detekt/detekt.yml`: static analysis rules.

## Rules

- Explicit API mode is on: every public declaration needs a visibility
  modifier and a return type. Default to `internal`. A `const val` in a
  private companion is still a public static field; put it at file level.
- Public API changes are deliberate. `apiCheck` fails on drift; run
  `apiDump`, review the diff, and add a `CHANGELOG.md` line under
  `## [Unreleased]`.
- Models use `@SerialName` for every snake_case field and the lenient
  serializers in `internal/LenientSerializers.kt` for the API's string-typed
  scalars. Unknown enum values map to `UNKNOWN`; unknown JSON fields are
  ignored. Keep both.
- Nothing that can hold a secret value, passphrase or token may print it.
  `toString()` redacts; the library has no logging.
- Receipts are served under `/api/v2/receipt/*`. The `/api/v2/private/*`
  routes are deprecated aliases of the same handlers and are deliberately
  not exposed: `Identifiers.receipt` accepts a pasted `/private/` (or
  `/metadata/`) URL, but every request the SDK sends goes to `/receipt/`.
  Do not add `private` routes or a flag to select them.
- Writes (POST/PATCH) are never retried. Only GETs retry, on network errors
  and 502/503/504.
- New server `error_type` values go in `internal/ErrorMapper.kt` with a test
  in `ErrorHandlingTest.kt`.
- Unit tests use MockWebServer and never touch the network. Anything that
  needs a real deployment goes in `LiveApiTest` (opt-in via `ONETIME_LIVE=1`).
- Library bytecode stays Java 11 and the public API avoids `java.time`,
  `Optional` and streams. Prefer `kotlin.time.Duration` and epoch seconds.
- Releases are tag-driven; do not bump `VERSION_NAME` in feature PRs. See
  `docs/releasing.md`.
