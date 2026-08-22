# Releasing kmatch

Everything on the code side is ready; a release needs four one-time setup
steps (~15 minutes, owner only) and then two commands per release.

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

### 4. Activate the workflows

`tools/release-workflow.yml` → `.github/workflows/release.yml` and
`tools/docs-workflow.yml` → `.github/workflows/docs.yml` — this one also publishes the browser playground at likhithsj.github.io/kmatch (move via the GitHub
web UI: open the file → edit → rewrite the path — same as ci.yml was).
For docs, also set Settings → Pages → Source to **GitHub Actions**.

## Per release

```sh
# 1. set the release version (drop -SNAPSHOT) in gradle.properties, commit
# 2. tag and push -- the tag triggers the publish workflow
git tag v0.3.0 && git push origin main v0.3.0
# 3. bump gradle.properties to the next -SNAPSHOT, commit, push
```

Central releases are **immutable** — a published version can never be changed
or deleted. The workflow runs the full test matrix before publishing, and the
POM already carries the license/developer/SCM blocks Central validates.
Artifacts appear on Central within ~30 minutes of a green Release run; they
are searchable on [central.sonatype.com](https://central.sonatype.com) sooner.
