# Contributing

Thanks for helping improve the Kotlin SDK. This page covers the mechanics;
the [README](README.md) covers what the SDK does.

## Setup

You need a JDK 17 or newer on your `PATH` to run Gradle. The Gradle toolchain
resolver provisions the exact JDK the build uses, so nothing else is required.

```sh
git clone https://github.com/onetimesecret/onetimesecret-kotlin.git
cd onetimesecret-kotlin
./gradlew build
```

`build` runs, in order: formatting check (Spotless + ktlint), static analysis
(detekt), public API check (binary-compatibility-validator), compilation with
warnings as errors, and the unit tests. The same command runs in CI, so if it
passes locally it passes there.

## Day-to-day commands

| Command                              | Purpose                                                  |
|--------------------------------------|----------------------------------------------------------|
| `./gradlew spotlessApply`            | Reformat Kotlin, Gradle scripts, Markdown and YAML       |
| `./gradlew detekt`                   | Static analysis; report in `build/reports/detekt/`       |
| `./gradlew apiDump`                  | Regenerate `onetimesecret-kotlin/api/*.api` after an intentional API change |
| `./gradlew apiCheck`                 | Fail if the public API changed without `apiDump`         |
| `./gradlew :onetimesecret-kotlin:test` | Unit tests only                                        |
| `./gradlew dokkaGenerate`            | API docs into `onetimesecret-kotlin/build/dokka/html`    |

## Conventions

- **Explicit API mode is on.** Every public declaration needs an explicit
  visibility modifier and return type. If you do not mean to export it, make
  it `internal`.
- **Public API changes are deliberate.** CI fails when the `.api` dump drifts.
  Run `apiDump`, look at the diff, and describe the change in `CHANGELOG.md`
  under `## [Unreleased]`. Additive changes are fine during `0.x`; removals and
  signature changes need a note calling them out as breaking.
- **No raw JSON at the call site.** Resources return models; models are
  `@Serializable` data classes with `@SerialName` for every snake_case field.
  When the API sends a scalar as a string, use the lenient serializers in
  `internal/LenientSerializers.kt` rather than widening the Kotlin type.
- **New server states must not break old apps.** Enums use a fallback
  serializer that maps unknown wire values to `UNKNOWN`. Keep it that way.
- **Secrets never reach logs.** `toString()` on anything that can hold a
  secret value or token must redact it. There is a test for each.
- **Errors are typed.** New API error types go in `internal/ErrorMapper.kt`
  with a test in `ErrorHandlingTest.kt`.
- **Tests use MockWebServer.** Unit tests must not touch the network. Anything
  that needs a real deployment goes in `LiveApiTest`, which only runs with
  `ONETIME_LIVE=1`.
- **Android compatibility.** The library targets Java 11 bytecode and avoids
  `java.time`, `java.util.Optional` and streams in its public API. Prefer
  `kotlin.time.Duration` and epoch seconds.

## Pull requests

- One change per PR. Keep refactors separate from behaviour changes.
- Fill in the PR template checklist.
- Commit messages: imperative subject line under 72 characters, body
  explaining *why* when it is not obvious.

## Releases

Maintainers only; see [docs/releasing.md](docs/releasing.md).

## Code of conduct

Be kind, assume good faith, keep discussions on the work. Report problems to
the maintainers at <security@onetimesecret.com>.
