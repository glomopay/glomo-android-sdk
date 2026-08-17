# Android Integration Guide

## Add the SDK

After the `0.0.2` artifact is published, add Maven Central and the SDK dependency to the host app:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.glomopay:glomo-android-sdk:0.0.2")
}
```

For local testing, use the standalone [Android SDK test app](../../glomopay-android-sdk-test-app/README.md). It links the sibling `glomo-android-sdk` module with `implementation(project(":glomo-android-sdk"))`.

## Configure checkout

The host Activity should implement `GlomoPayListener`:

```kotlin
class CheckoutActivity : Activity(), GlomoPayListener {
    fun start(orderId: String) {
        val config = GlomoPayConfig(
            publicKey = "live_public_key",
            orderId = orderId,
            devMode = false,
        )

        GlomoPaySdk.startCheckout(this, config, this)
    }

    override fun onPaymentSuccess(payload: GlomoPayPayload) {
        // Fulfil only after server-side payment verification.
    }

    override fun onPaymentFailure(payload: GlomoPayPayload) { /* Show a failure state. */ }
    override fun onSdkError(errors: List<SdkError>) { /* Handle validation/device errors. */ }
    override fun onConnectionError(error: ConnectionError) { /* Handle network/WebView errors. */ }
}
```

## Order type detection

The default launcher mode is `auto`:

```kotlin
GlomoPaySdk.startCheckout(this, config, this, orderType = "auto")
```

For an order ID, the SDK applies this rule:

1. A non-empty `orderType` response field wins.
2. Otherwise, a non-null `lrs` response field selects LRS.
3. Otherwise, Standard is selected.
4. If order detection fails, Standard is used as a safe fallback.

Pass `orderType = "standard"` or `orderType = "lrs"` only when the host intentionally wants to override automatic detection. The sample app uses `auto` so the API remains the source of truth. Subscription IDs currently open the Standard checkout flow.

## Configuration rules

- Provide exactly one of `orderId` or `subscriptionId`.
- Use `test_` or `mock_` public keys with `devMode = true` for local testing.
- Use a `live_` public key only for production on a compliant device.
- Do not log keys, identifiers, payment payloads, or signatures in production.
- Verify payment results on the merchant backend before fulfilling an order.

## Events and errors

`onEvent` provides diagnostic WebView and checkout lifecycle events. `onSdkError` is for SDK validation/device errors, while `onConnectionError` is for network and WebView failures. `onPaymentTerminate` is called when the user or SDK closes checkout.

## Local sample app

From the repository root:

```bash
cd ../glomopay-android-sdk-test-app
./gradlew :app:installDebug
```

The sample app accepts a public key and order/subscription ID, enables developer mode for testing, and records callback events on screen.
## Analytics build configuration

The Android AAR sends the documented checkout events directly to Mixpanel's `/track?ip=1` API. The
project token is compiled into the AAR from the `MIXPANEL_TOKEN` Gradle property or environment
variable and is never accepted through the merchant-facing SDK API.

```bash
MIXPANEL_TOKEN="<project-token>" ./gradlew :glomo-android-sdk:assembleRelease
```

If the token is absent, analytics uses a no-op tracker and checkout behavior is unchanged. Do not
commit the token to `gradle.properties`; provide it through the release CI environment instead.

The AAR declares `ACCESS_NETWORK_STATE`, a normal Android permission that does not show a runtime
permission dialog. It is used only to populate `$wifi_enabled` and `$cellular_enabled`. Mixpanel's
`ip=1` ingestion option derives coarse `$city`, `$region`, and `mp_country_code` properties from the
request IP; the SDK does not request device location permission or read GPS coordinates.

Every event includes the approved device, screen, merchant-app, locale, SDK, flow, and session
properties. `$insert_id` equals the checkout `session_id`, while `distinct_id` follows the nullable
`order_id` contract. Properties that cannot be determined are encoded as explicit JSON nulls.

For bank/3DS redirects, only `https://hostname` is transmitted. Credentials, port, path, query, and
fragment are discarded before `Redirect Opened`, `Redirect Page Started`, `Redirect Page Finished`,
and `Redirect URL Change` events are built. WebView context values are limited to `main` and `flow`.
When development mode skips compliance enforcement, all compliance detection properties are sent
as null to distinguish "not checked" from a passing result.

## Sentry build configuration

The release build can report explicitly captured SDK and analytics-delivery failures through an
isolated Sentry client. Supply its DSN using the `SENTRY_DSN` Gradle property or environment
variable:

```bash
SENTRY_DSN="<android-sdk-dsn>" ./gradlew :glomo-android-sdk:assembleRelease
```

If the DSN is absent, error reporting uses a no-op implementation and checkout behavior is
unchanged. Never commit the DSN to `gradle.properties`; inject it through release CI.

The SDK does not call global Sentry initialization and therefore does not replace the merchant's
Sentry client. It excludes NDK and Session Replay and disables app-wide uncaught-exception, ANR,
session, PII, tracing, and profiling collection. Only failures explicitly captured within the
GlomoPay SDK boundary are sent.

## ProGuard/R8 and Sentry mappings

No additional keep rules are required for normal SDK integration. The AAR packages consumer rules
that preserve `@JavascriptInterface` callbacks, runtime annotations, source file names, and line
numbers. Merchant release builds can keep shrinking, optimization, and obfuscation enabled.

The complete R8 mapping is generated only when the merchant application creates its final APK or
App Bundle. It is normally available at:

```text
app/build/outputs/mapping/<variant>/mapping.txt
```

For readable stack traces in the GlomoPay-owned Sentry project, that exact mapping must be uploaded
from the merchant application's protected build/CI job using GlomoPay-provided, least-privilege
Sentry credentials and the matching release/build metadata. Do not commit the mapping, Sentry auth
token, or `sentry.properties` credentials to source control.

The isolated SDK client reads the standard generated `sentry-debug-meta.properties` mapping UUID
so Sentry can associate an event with that upload. It reads only debug-meta identifiers, not the
merchant's Sentry DSN, auth token, scope, or runtime client configuration.

Do not apply automatic Sentry runtime initialization for this purpose. If the Sentry Android Gradle
plugin or `sentry-cli` is used for mapping upload, configure it only in the final application build
and keep automatic SDK installation and instrumentation disabled. This preserves the isolated
GlomoPay Sentry client and avoids altering the merchant application's own Sentry configuration.
