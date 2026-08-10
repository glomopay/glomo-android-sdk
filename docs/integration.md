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
