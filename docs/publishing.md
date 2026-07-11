# Publishing to Marketplaces

This guide covers how to submit the JiuwenSwarm plugins to the JetBrains Plugin Marketplace and the VS Code Marketplace (and the open-source OpenVSX registry).

---

## JetBrains Plugin Marketplace

### 1. One-time account setup

1. Sign in (or create an account) at [plugins.jetbrains.com](https://plugins.jetbrains.com).
2. Go to **Upload plugin** → you will be prompted to confirm your vendor details.
3. The plugin's `<vendor>` tag in `plugin.xml` must match your registered account:
   ```xml
   <vendor email="dev@openjiuwen.com" url="https://github.com/openjiuwen">OpenJiuwen</vendor>
   ```

### 2. Generate a Marketplace token

1. Go to [plugins.jetbrains.com/author/me](https://plugins.jetbrains.com/author/me).
2. Under **Tokens**, click **Generate new token** — give it a descriptive name like `jiuwenswarm-publish`.
3. Copy the token immediately (it is shown only once).
4. Store it as an environment variable (never hard-code it):
   ```bash
   export JETBRAINS_MARKETPLACE_TOKEN=perm:...
   ```

### 3. Verify the plugin before submitting

The Plugin Verifier checks compatibility with all target IDE versions:

```bash
cd packages/jetbrains-plugin
./gradlew verifyPlugin
```

Fix any reported errors before submitting. The most common issues are:
- Accessing internal `@ApiStatus.Internal` APIs
- Missing `since-build` / `until-build` bounds
- Incompatible Kotlin stdlib version

### 4. Update release metadata

Before each release, update `changeNotes` in `build.gradle.kts` and bump `version`:

```kotlin
version = "0.2.0"   // in the top-level version = ... line

// inside intellijPlatform > pluginConfiguration:
changeNotes = """
    <b>0.2.0</b>
    <ul>
      <li>Your new feature here</li>
    </ul>
    <b>0.1.0</b>
    <ul>
      <li>Streaming chat panel with real-time token output</li>
      <li>Session management (create, switch, resume)</li>
      <li>IDE context injection (active file, selection, diagnostics)</li>
      <li>File edit diff viewer (Current vs Proposed) and auto-apply mode</li>
      <li>Send Selection shortcut prefills chat with selected code</li>
      <li>Connection status bar widget with auto-reconnect</li>
      <li>Dark and light themes</li>
    </ul>
""".trimIndent()
```

### 5. Configure Gradle publishing

Uncomment and fill in the `publishing` block in `build.gradle.kts`:

```kotlin
intellijPlatform {
    // ...
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        // Optional: publish to a specific channel (e.g. "beta" or "eap")
        // channels = listOf("beta")
    }
}
```

### 6. Sign the plugin (required for Marketplace approval)

JetBrains requires plugin signing. Generate a certificate chain and private key:

```bash
# Install the JetBrains Plugin Signing CLI (once)
brew install jetbrains/tools/marketplace-zip-signer   # macOS
# or download from https://github.com/JetBrains/marketplace-zip-signer/releases

# Generate a self-signed certificate (valid for 10 years)
marketplace-zip-signer generate-certificate \
  --output private.pem \
  --cert chain.crt \
  --password "$KEY_PASSWORD"
```

Then configure `signing` in `build.gradle.kts`:

```kotlin
signing {
    certificateChain = providers.environmentVariable("JB_CERT_CHAIN")
    privateKey        = providers.environmentVariable("JB_PRIVATE_KEY")
    password          = providers.environmentVariable("JB_KEY_PASSWORD")
}
```

Store the PEM content as environment variables (base64-encode for CI):

```bash
export JB_CERT_CHAIN=$(cat chain.crt)
export JB_PRIVATE_KEY=$(cat private.pem)
export JB_KEY_PASSWORD=your_passphrase
```

### 7. Build, sign, and publish

```bash
cd packages/jetbrains-plugin
./gradlew signPlugin publishPlugin
```

This runs:
1. `buildPlugin` — produces the `.zip`
2. `signPlugin` — signs it with your certificate
3. `publishPlugin` — uploads it to the Marketplace

The plugin will be in **review** for 2–5 business days on first submission. Subsequent updates are reviewed faster.

### 8. Automate with CI (GitHub Actions)

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

Add all four values as **repository secrets** in GitHub → Settings → Secrets.

---

## VS Code Marketplace

### 1. One-time publisher setup

1. Sign in to [marketplace.visualstudio.com/manage](https://marketplace.visualstudio.com/manage) with a Microsoft account.
2. Click **Create publisher** and use `openjiuwen` as the publisher ID (must match `"publisher"` in `package.json`).

### 2. Generate a Personal Access Token (PAT)

1. Go to [dev.azure.com](https://dev.azure.com) → your organization → **User Settings → Personal access tokens**.
2. Click **New Token**:
   - **Scopes**: Custom defined → **Marketplace → Manage** (or full access)
   - **Expiration**: set to at least 1 year
3. Copy the token.

### 3. Add required fields to package.json

The Marketplace requires these fields (add them if not already present):

```jsonc
{
  "repository": {
    "type": "git",
    "url": "https://github.com/openjiuwen/jiuwenswarm-ide"
  },
  "license": "MIT",
  "homepage": "https://github.com/openjiuwen/jiuwenswarm-ide#readme",
  "galleryBanner": {
    "color": "#1e1e2e",
    "theme": "dark"
  }
}
```

Also ensure `resources/icon.png` exists and is 128×128 px.

### 4. Install vsce and log in

```bash
npm install -g @vscode/vsce

# Log in once (stored in ~/.vsce)
vsce login openjiuwen
# When prompted, paste your PAT
```

### 5. Package and publish

```bash
cd packages/vscode-extension
npm run build          # compile TypeScript → out/extension.js

vsce package           # produces jiuwenswarm-0.1.0.vsix (for manual install / testing)
vsce publish           # build + publish in one step
```

To bump the version automatically:

```bash
vsce publish minor     # 0.1.0 → 0.2.0
vsce publish patch     # 0.1.0 → 0.1.1
vsce publish major     # 0.1.0 → 1.0.0
```

Or publish with an explicit token (useful in CI):

```bash
vsce publish --pat "$VSCE_PAT"
```

### 6. Automate with CI (GitHub Actions)

Create `.github/workflows/publish-vscode.yml`:

```yaml
name: Publish VS Code Extension

on:
  push:
    tags: ['vscode-v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install dependencies
        working-directory: packages/vscode-extension
        run: npm ci

      - name: Build
        working-directory: packages/vscode-extension
        run: npm run build

      - name: Publish
        working-directory: packages/vscode-extension
        run: npx vsce publish --pat "${{ secrets.VSCE_PAT }}"
```

Add `VSCE_PAT` as a repository secret.

---

## OpenVSX (open-source VS Code Marketplace)

[Open VSX](https://open-vsx.org) is the community registry used by VSCodium, Gitpod, Eclipse Theia, and other VS Code-compatible editors.

### 1. Create an account and namespace

1. Go to [open-vsx.org](https://open-vsx.org) → **Sign in with GitHub**.
2. Navigate to **User Settings → Access Tokens** → generate a token.
3. In the **Namespaces** tab, claim the `openjiuwen` namespace (must match `"publisher"` in `package.json`).

### 2. Publish

```bash
# Install ovsx CLI
npm install -g ovsx

cd packages/vscode-extension
npm run build
vsce package   # produces jiuwenswarm-0.1.0.vsix

ovsx publish jiuwenswarm-0.1.0.vsix --pat "$OVSX_PAT"
```

### 3. CI automation

Extend the VS Code publish workflow or create a separate job:

```yaml
- name: Publish to OpenVSX
  working-directory: packages/vscode-extension
  run: |
    npx vsce package
    npx ovsx publish jiuwenswarm-*.vsix --pat "${{ secrets.OVSX_PAT }}"
```

---

## Release Checklist

Before any marketplace release:

- [ ] Bump `version` in `packages/jetbrains-plugin/build.gradle.kts` and `packages/vscode-extension/package.json`
- [ ] Update `changeNotes` in `build.gradle.kts` with the new version block
- [ ] Run `./gradlew verifyPlugin` and fix any reported errors
- [ ] Install the built artifact locally (`buildPlugin` → install from disk) and smoke-test key flows
- [ ] Tag the release: `git tag jetbrains-vX.Y.Z` / `git tag vscode-vX.Y.Z`
- [ ] Push tags to remote — CI workflows pick them up automatically
