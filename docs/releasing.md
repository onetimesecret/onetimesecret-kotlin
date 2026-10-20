# Releasing

How `com.onetimesecret:onetimesecret-kotlin` gets from a commit on `main` to
Maven Central. The procedure is deliberately the same shape as the other
official SDKs: the version lives in one file, a `v*` tag triggers the publish,
CI refuses a tag that does not match, and no publishing credential exists on a
laptop.

## Package and repository names

- **Maven coordinates:** `com.onetimesecret:onetimesecret-kotlin`.
- **Repository:** `onetimesecret/onetimesecret-kotlin`. Repository names and
  artifact names do not need to match across the SDK family, but here they do.
- **Kotlin package:** `com.onetimesecret.sdk`.
- **Versioning:** [Semantic Versioning](https://semver.org). While the major
  is `0`, minor bumps may contain breaking changes and say so in the
  changelog. The public API surface is tracked in
  `onetimesecret-kotlin/api/onetimesecret-kotlin.api`; any diff to that file in
  a PR is a deliberate API change and needs a changelog entry.

## One-time setup

Everything below is done once per repository (or once per organization where
noted) by a maintainer with admin rights. Nothing here needs repeating for a
release.

### 1. Maven Central namespace (per organization)

1. Sign in to the [Central Portal](https://central.sonatype.com) with the
   organization's shared publishing account (not a personal one, so that
   maintainers can change without re-verifying).
2. Register the namespace **`com.onetimesecret`** and verify it with the
   requested DNS TXT record on `onetimesecret.com`. Verification is permanent
   and shared by every artifact under the namespace, so later SDKs that use
   `com.onetimesecret` skip this step.
3. Generate a **user token** (Account → Generate User Token). The token's
   username and password are what CI uses; the portal login password is never
   used for publishing.

### 2. Signing key (per organization)

Maven Central requires every artifact to be PGP-signed. Use one release key for
the whole SDK family, kept out of any individual's keyring.

```sh
# Generate (RSA 4096, no expiry; rotate deliberately rather than by surprise)
gpg --batch --full-generate-key <<'EOF'
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: Onetime Secret Release Signing
Name-Email: release@onetimesecret.com
Expire-Date: 0
%no-protection
EOF

# Find the key id, then publish the public key where Central looks it up
gpg --list-secret-keys --keyid-format long
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>

# Export the private key for CI (ASCII-armored, includes BEGIN/END lines)
gpg --armor --export-secret-keys <KEY_ID> > release-signing.asc
```

Store `release-signing.asc` and the key id in the organization's password
manager, then delete the local copy. If you gave the key a passphrase, store
that too.

### 3. GitHub repository configuration

**Environment.** Create an environment named **`maven-central`**
(Settings → Environments). Restrict it to the `main` branch and to tags
matching `v*`, and add required reviewers if you want a human gate before
publish. The release workflow binds to this environment, so its secrets are not
visible to pull requests.

**Secrets** (on the `maven-central` environment):

| Secret                   | Value                                                              |
|--------------------------|--------------------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token *username*                               |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token *password*                               |
| `SIGNING_KEY`            | Contents of `release-signing.asc` (the whole armored block)        |
| `SIGNING_KEY_ID`         | Long key id (optional but recommended when the keyring has subkeys)|
| `SIGNING_KEY_PASSWORD`   | Key passphrase, or leave unset when the key has none               |

**Secrets for the scheduled live tests** (repository-level, used by `ci.yml`
only on schedule or manual dispatch, never on pull requests):

| Secret / variable              | Value                                                   |
|--------------------------------|---------------------------------------------------------|
| `ONETIME_CUSTOMER_EXTID` (secret) | Customer extid of a dedicated test account           |
| `ONETIME_API_TOKEN` (secret)   | That account's API token                                |
| `ONETIME_BASE_URL` (variable)  | Optional; defaults to `https://eu.onetimesecret.com`    |

Use a throwaway account on the free plan for this. The tests create
short-lived secrets and burn or reveal them before finishing.

**Branch protection on `main`:** require the `Build & test` checks to pass,
require pull requests, and forbid force pushes. Tags are not protected by
branch rules; protect `v*` with a tag ruleset limited to maintainers so a
stray `git push --tags` cannot start a release.

### 4. Verify the pipeline without publishing

Before the first real release, run the release job's build step locally to be
sure the artifacts assemble and the POM is complete:

```sh
./gradlew build publishToMavenLocal
ls ~/.m2/repository/com/onetimesecret/onetimesecret-kotlin/
```

You should see the jar, `-sources.jar`, `-javadoc.jar`, `.pom` and `.module`.
Signing is skipped locally when no key is configured; CI signs.

## Cutting a release

1. **Pick the version.** Decide major/minor/patch from the `## [Unreleased]`
   entries in `CHANGELOG.md` and the `.api` diff since the last tag.

2. **Open a release PR** that:
   - sets `VERSION_NAME` in `gradle.properties`;
   - renames `## [Unreleased]` in `CHANGELOG.md` to `## [X.Y.Z] - YYYY-MM-DD`
     and adds a fresh empty `## [Unreleased]` above it;
   - updates the version in the README installation snippet;
   - runs `./gradlew apiDump` if the dump is stale (CI fails otherwise).

   Title it `Release vX.Y.Z`. CI must be green.

3. **Merge, then tag the merge commit:**

   ```sh
   git checkout main && git pull
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```

4. **Watch the Release workflow.** It verifies the tag matches
   `VERSION_NAME`, refuses `-SNAPSHOT` versions and tags without a changelog
   section, runs the full check suite, signs and uploads to the Central Portal
   with automatic release, then creates a GitHub release with the changelog
   section as notes and the jars attached.

5. **Confirm on Central.** The artifact appears at
   <https://central.sonatype.com/artifact/com.onetimesecret/onetimesecret-kotlin>
   within a few minutes and on Maven Central mirrors within an hour or so.

6. **Bump to the next snapshot** on `main` in a follow-up commit:
   `VERSION_NAME=X.Y.(Z+1)-SNAPSHOT`. Snapshot versions never publish from this
   workflow; they only mark `main` as "ahead of the last release".

## When something goes wrong

- **Tag/version mismatch.** The workflow fails before building. Delete the
  tag (`git push --delete origin vX.Y.Z`), fix `gradle.properties`, re-tag.
- **Validation failed on the Central Portal** (missing POM field, bad
  signature). Nothing was released. Fix, delete the tag, re-tag. The portal
  discards failed deployments automatically.
- **A bad version reached Central.** Maven Central is immutable: it cannot be
  deleted or replaced. Publish a fixed patch release and mark the bad version
  in `CHANGELOG.md` as yanked with a one-line reason.
- **Rotating the signing key.** Generate and publish a new key (step 2),
  replace the three `SIGNING_*` secrets, and note the new key id in the next
  release's changelog entry so downstream verifiers can update.
- **Rotating the Central token.** Generate a new user token on the portal and
  replace the two `MAVEN_CENTRAL_*` secrets. The old token stops working
  immediately.

## Reusing this for other SDKs

To bring another Kotlin/JVM SDK onto the same pipeline, copy
`.github/workflows/release.yml`, the `mavenPublishing {}` block and the
`generateBuildInfo` task from `onetimesecret-kotlin/build.gradle.kts`, and
`gradle.properties`. Steps 1 and 2 above are already done for the
organization; only step 3 (environment, secrets, rulesets) is per repository.
