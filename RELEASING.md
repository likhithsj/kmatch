# Releasing kmatch

The one-time setup below is **done** (three releases have gone out through
it) and is kept as a reference for rotating a token or key, or for setting
the pipeline up in a fork. A release itself is the short [Per
release](#per-release) section at the end.

## One-time setup

### 1. Central Portal account

1. Go to [central.sonatype.com](https://central.sonatype.com) and sign in
   **with GitHub**.
2. The namespace `io.github.likhithsj` is granted and verified automatically
   for a GitHub sign-in — confirm it appears under *Namespaces*.
3. Under your account, *Generate User Token* — this yields a **username** and
   **password** pair used by CI (not your login credentials).

### 2. GPG signing key

```sh
gpg --quick-generate-key "Likhith Serkad Jayakumar <likhithsj@gmail.com>" rsa4096 sign never
gpg --list-secret-keys --keyid-format long          # note the key id (after rsa4096/)
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
gpg --armor --export-secret-keys KEY_ID             # the whole block, for the secret
```

### 3. Repository secrets

GitHub repo → Settings → Secrets and variables → Actions → *New repository
secret*, four of them:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central token username (step 1) |
| `MAVEN_CENTRAL_PASSWORD` | Central token password (step 1) |
| `SIGNING_KEY` | ASCII-armored private key block (step 2) |
| `SIGNING_KEY_PASSWORD` | key passphrase (empty string if none) |

### 4. Workflows and Pages

`.github/workflows/release.yml` publishes on a `v*` tag and
`.github/workflows/docs.yml` deploys the playground and API docs to
likhithsj.github.io/kmatch on every push to `main`. Both are in place.
For docs, Settings → Pages → Source must be **GitHub Actions**.

Workflow files cannot be pushed with this repository's automation
credentials; edit them through the GitHub web UI or with a personal token
that has the `workflow` scope.

## Per release

```sh
# 0. before tagging: ./gradlew jvmTest, and tools/differential-fuzz/run.sh
#    if anything under src/commonMain changed since the last release
# 1. set the release version (drop -SNAPSHOT) in gradle.properties, commit
# 2. tag and push -- the tag triggers the publish workflow
git tag vX.Y.Z && git push origin main vX.Y.Z
# 3. bump gradle.properties to the next -SNAPSHOT, commit, push
# 4. after it lands on Central: bump the samples' pinned version and the
#    README install line, and write the GitHub release notes
```

Central releases are **immutable** — a published version can never be changed
or deleted. The workflow runs the full test matrix before publishing, and the
POM already carries the license/developer/SCM blocks Central validates.
Artifacts appear on Central within ~30 minutes of a green Release run; they
are searchable on [central.sonatype.com](https://central.sonatype.com) sooner.
