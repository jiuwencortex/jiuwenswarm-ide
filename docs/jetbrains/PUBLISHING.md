# Publishing the JetBrains Plugin

This guide covers building, signing, and publishing the JiuwenSwarm plugin to the JetBrains Plugin Marketplace.

## Development Build

```bash
cd packages/jetbrains-plugin
./gradlew buildPlugin   # → build/distributions/*.zip
```

Requires JDK 17 and Gradle 8.7.

## One-time Account Setup

1. Sign in (or create an account) at [plugins.jetbrains.com](https://plugins.jetbrains.com).
2. Go to **Upload plugin** → confirm your vendor details.
3. The plugin's `<vendor>` tag in `plugin.xml` must match your registered account:
   ```xml
   <vendor email="dev@openjiuwen.com" url="https://github.com/openjiuwen">OpenJiuwen</vendor>
   ```

## Generate a Marketplace Token

1. Go to [plugins.jetbrains.com/author/me](https://plugins.jetbrains.com/author/me).
2. Under **Tokens**, click **Generate new token** — name it `jiuwenswarm-publish`.
3. Copy the token immediately (shown only once).
4. Store it as an environment variable (never hard-code):
   ```bash
   export JETBRAINS_MARKETPLACE_TOKEN=perm:...
   ```

## Verify the Plugin

The Plugin Verifier checks compatibility with all target IDE versions:

```bash
cd packages/jetbrains-plugin
./gradlew verifyPlugin
```

Fix any reported errors before submitting. Common issues:
- Accessing internal `@ApiStatus.Internal` APIs
- Missing `since-build` / `until-build` bounds
- Incompatible Kotlin stdlib version

## Update Release Metadata

Before each release, update `changeNotes` in `build.gradle.kts` and bump `version`:

```kotlin
version = "0.2.0"

intellijPlatform {
    pluginConfiguration {
        changeNotes = """
            <b>0.2.0</b>
            <ul><li>Your new feature here</li></ul>
            <b>0.1.0</b>
            <ul>
              <li>Streaming chat panel with real-time token output</li>
              <li>Session management (create, switch, resume)</li>
              <li>IDE context injection</li>
              <li>File edit diff viewer and auto-apply mode</li>
              <li>Send Selection shortcut</li>
              <li>Connection status bar widget</li>
              <li>Dark and light themes</li>
            </ul>
        """.trimIndent()
    }
}
```

## Configure Gradle Publishing

Uncomment and fill in the `publishing` block in `build.gradle.kts`:

```kotlin
intellijPlatform {
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        // channels = listOf("beta")
    }
}
```

## Sign the Plugin

JetBrains requires plugin signing. Generate a certificate chain and private key:

```bash
# Install the JetBrains Plugin Signing CLI
brew install jetbrains/tools/marketplace-zip-signer   # macOS
# or download from https://github.com/JetBrains/marketplace-zip-signer/releases

# Generate a self-signed certificate (valid for 10 years)
marketplace-zip-signer generate-certificate \
  --output private.pem \
  --cert chain.crt \
  --password "$KEY_PASSWORD"
```

Configure `signing` in `build.gradle.kts`:

```kotlin
signing {
    certificateChain = providers.environmentVariable("JB_CERT_CHAIN")
    privateKey        = providers.environmentVariable("JB_PRIVATE_KEY")
    password          = providers.environmentVariable("JB_KEY_PASSWORD")
}
```

Store the PEM content as environment variables:

```bash
export JB_CERT_CHAIN=$(cat chain.crt)
export JB_PRIVATE_KEY=$(cat private.pem)
export JB_KEY_PASSWORD=your_passphrase
```

## Build, Sign, and Publish

```bash
cd packages/jetbrains-plugin
./gradlew signPlugin publishPlugin
```

This runs:
1. `buildPlugin` — produces the `.zip`
2. `signPlugin` — signs it
3. `publishPlugin` — uploads to the Marketplace

First submission is reviewed in 2–5 business days. Subsequent updates are reviewed faster.

## CI Automation (GitHub Actions)

Create `.github/workflows/publish-jetbrains.yml`:

```yaml
name: Publish JetBrains Plugin

on:
  push:
    tags: ['jetbrains-v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Build and publish
        working-directory: packages/jetbrains-plugin
        env:
          JETBRAINS_MARKETPLACE_TOKEN: ${{ secrets.JETBRAINS_MARKETPLACE_TOKEN }}
          JB_CERT_CHAIN: ${{ secrets.JB_CERT_CHAIN }}
          JB_PRIVATE_KEY: ${{ secrets.JB_PRIVATE_KEY }}
          JB_KEY_PASSWORD: ${{ secrets.JB_KEY_PASSWORD }}
        run: ./gradlew signPlugin publishPlugin
```

Add all four values as repository secrets in GitHub → Settings → Secrets.

## Release Checklist

- [ ] Bump `version` in `packages/jetbrains-plugin/build.gradle.kts`
- [ ] Update `changeNotes` in `build.gradle.kts` with the new version block
- [ ] Run `./gradlew verifyPlugin` and fix any errors
- [ ] Install the built artifact locally (`buildPlugin` → install from disk) and smoke-test
- [ ] Tag the release: `git tag jetbrains-vX.Y.Z`
- [ ] Push tags to remote — CI workflows pick them up automatically
