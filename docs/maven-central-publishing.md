# Publishing the Kotlin SDK to Maven Central

This guide explains how to publish the native GlomoPay Kotlin SDK as a public Maven artifact. The standalone test application APK is not published to Maven Central; only the `glomo-android-sdk` Android library is published.

> **Current setup status:** The library coordinates and version are ready in this repository. The Sonatype namespace verification, publishing credentials, signing configuration, and CI secrets are still release-owner setup items. Do not run a production publish until those items are completed.

## Release coordinates

```text
Group ID:    com.glomopay
Artifact ID: glomo-android-sdk
Version:     1.0.0
```

Consumers will eventually add the SDK with:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.glomopay:glomo-android-sdk:1.0.0")
}
```

## Step 1: Create a Sonatype account

1. Open the [Sonatype Central Portal](https://central.sonatype.com/).
2. Create an account or sign in.
3. Use the organization account that owns the `glomopay.com` domain.
4. Keep access to the account email for release notifications.

## Step 2: Register the namespace

The Maven `groupId` is also called a namespace. For this SDK, request:

```text
com.glomopay
```

Sonatype will display a verification key, for example `egautmsohk`. The namespace cannot be used for publishing until ownership is verified.

## Step 3: Add the DNS TXT record

The domain owner must open the DNS dashboard for `glomopay.com`. This may be Cloudflare, GoDaddy, Hostinger, Namecheap, or another DNS provider.

Add a TXT record:

```text
Type:  TXT
Name:  @
Value: <Sonatype verification key>
```

For `com.glomopay`, Sonatype checks the exact root domain `glomopay.com`. Do not add the record to `com.glomopay.com` or `maven-central.glomopay.com`.

Check DNS visibility with:

```bash
dig TXT glomopay.com
```

After DNS propagation, return to the Central Portal, select the namespace, and confirm verification. Official instructions: [Sonatype namespace registration](https://central.sonatype.org/register/namespace/) and [DNS TXT verification](https://central.sonatype.org/faq/how-to-set-txt-record/).

## Step 4: Create publishing credentials

After the namespace is verified:

1. Open Central Portal account settings.
2. Create a publishing user token.
3. Copy the generated username and token immediately.
4. Store them in a password manager or CI secret store.

Never commit these values to Git, Gradle files, or documentation.

## Step 5: Create a signing key

Maven Central releases must be signed. Create a GPG key and publish its public key to a public key server. Keep the private key and passphrase private.

Gradle receives signing values through local properties or CI secrets, for example:

```text
signing.keyId=<key-id>
signing.password=<private-key-passphrase>
signing.secretKeyRingFile=<path-to-private-key>
```

Do not place the private key inside the repository.

## Step 6: Configure the library module

The publishable module is `glomo-android-sdk/`. Its release identity is configured in `glomo-android-sdk/build.gradle.kts`:

```kotlin
group = "com.glomopay"
version = "1.0.0"
```

The publishing setup must generate the release AAR, POM, sources JAR, Javadoc or Dokka JAR, checksums, and GPG signatures. The standalone test app is not included in the SDK publication.

## Step 7: Run local verification

From the repository root:

```bash
./gradlew clean test
cd ../glomopay-android-sdk-test-app
./gradlew :app:testDebugUnitTest
./gradlew :glomo-android-sdk:assembleRelease
```

Manually test Standard, LRS, subscription, validation-error, connection-error, and developer-mode scenarios in the sample app.

## Step 8: Upload the release

After the Maven publishing and signing configuration is enabled, run the task configured by the project. Common task names are:

```bash
./gradlew :glomo-android-sdk:publish
```

or:

```bash
./gradlew :glomo-android-sdk:publishToMavenCentral
```

The configured task uploads the signed publication to the Central Portal.

## Step 9: Validate and publish in the Central Portal

1. Open the Central Portal deployment view.
2. Confirm the deployment passed validation.
3. Review the generated coordinates and version.
4. Publish the validated deployment if manual confirmation is required.
5. Wait for Maven Central synchronization.

The artifact must be available at `com.glomopay:glomo-android-sdk:1.0.0`.

## Step 10: Verify from a clean consumer project

Add the published dependency to a separate Android app:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.glomopay:glomo-android-sdk:1.0.0")
}
```

Then run `./gradlew clean assembleDebug` and open a checkout through `GlomoPaySdk.startCheckout()` without a local project dependency.

## Step 11: Tag and document the release

1. Confirm the same version in `glomo-android-sdk/build.gradle.kts`, `README.md`, and `CHANGELOG.md`.
2. Add release notes to `CHANGELOG.md`.
3. Create and push the Git tag:

```bash
git tag 1.0.0
git push origin 1.0.0
```

4. Share the Maven dependency, not the sample APK, with client developers.

## Security rules

- Never commit Sonatype tokens.
- Never commit GPG private keys or keystores.
- Use CI secret variables for automated releases.
- Use a new version for every published change.
- Do not publish test credentials, payment signatures, or merchant order data in documentation.
