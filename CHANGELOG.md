# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org).

## [Unreleased]

### Changed

- `secrets.generate` now returns the generated value in `ConcealResult.value`.
  The service never includes it in the create response; it discloses it once,
  on the first view of the receipt, so `generate` fetches the receipt right
  after creation. A failed fetch leaves `value` `null` with the cause in
  `ConcealResult.claimFailure` instead of failing the call. Pass
  `claimValue = false` to claim it yourself. `Secret.value` on a generate
  result is always `null`.

## [0.1.0] - 2026-10-20

Initial release.

### Added

- `OnetimeSecretClient` with a builder DSL, immutable `OnetimeSecretConfig`
  and `Credentials` (customer extid + API token, validated at construction).
- `client.secrets`: `conceal`, `generate`, `reveal`, `show`, `status` (single
  and batch), with `guest = true` to select the anonymous `/guest/` routes.
- `client.receipts`: `show`, `recent`, `burn`, `update` (memo).
- `client.status()`, `client.version()`, `client.supportedLocales()`.
- Typed models (`Secret`, `Receipt`, `ConcealResult`, `RevealedSecret`,
  `SecretStatus`) with lenient decoding of the API's string-typed scalars and
  forward-compatible enum handling.
- Sealed `OnetimeSecretException` hierarchy following the API's
  `{ error, error_type }` contract, including `AccountRequiredException`,
  `RateLimitException.retryAfterSeconds` and `EntitlementException.entitlement`.
- Automatic retry with backoff for idempotent requests on network errors and
  502/503/504.
- Consumer R8 rules bundled in the jar.

[Unreleased]: https://github.com/onetimesecret/onetimesecret-kotlin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/onetimesecret/onetimesecret-kotlin/releases/tag/v0.1.0
