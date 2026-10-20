# Security Policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Email
<security@onetimesecret.com> with a description, affected versions and, if
you have one, a proof of concept. You will get an acknowledgement within two
business days and a fix or mitigation plan within a reasonable time frame that
depends on severity.

If the issue is in the Onetime Secret service rather than this SDK, the same
address is the right place.

## Scope

This SDK is a thin HTTP client. The things it is responsible for:

- never logging or printing secret values, passphrases or API tokens
  (`toString()` redacts them; no debug logging exists in the library);
- sending credentials only to the configured `baseUrl`, over whatever
  transport that URL specifies (use `https`);
- building request paths from caller input safely (identifiers are
  path-segment encoded and validated).

Things it does not do, by design:

- store anything on disk;
- pin certificates (configure that on the `OkHttpClient` you pass in if your
  threat model needs it);
- end-to-end encrypt secret values before upload. The service encrypts at
  rest; client-side encryption is a planned, opt-in addition across the SDK
  family.

## Supported versions

Only the latest minor release receives security fixes. Keep your dependency
current; Dependabot can do that for you.
