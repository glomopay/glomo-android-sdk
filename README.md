# GlomoPay Android SDK

Native Kotlin SDK for integrating GlomoPay Standard and LRS hosted checkout
flows into Android applications.

## Current Status

Version `0.0.1` is implemented and available for local integration testing.
Maven Central publication is planned; until then, use the local module or the
published artifact when it becomes available.

The SDK hosts GlomoPay checkout in a native Android WebView and exposes payment
results and lifecycle events through a Kotlin callback contract. Card details,
3DS, bank authentication, and document upload screens remain inside the hosted
checkout and are not implemented natively by the SDK.

It deliberately does **not** capture card numbers, CVVs, or expiry dates;
tokenize payment instruments; implement 3DS or SCA logic natively; or store or
transmit KYC document contents. Card data is handled entirely by the hosted
checkout page, server-side. This boundary is what keeps the SDK and every app
embedding it out of PCI scope. It is a hard architectural constraint, not a
current implementation detail. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Requirements

| Requirement | Value |
|---|---|
| Minimum Android version | Android 7.0 / API 24 |
| Compile SDK | 35 |
| Kotlin | 2.0.21 or compatible |
| Java/JVM target | 17 |
| Planned Maven coordinates | `com.glomopay:glomopay-sdk:0.0.1` |

**Why minSdk 24.** The deciding factor is TLS, not market share. Devices below
Android 7.1.1 carry a stale CA trust store and can fail handshakes against modern
certificates. For a checkout SDK, that means a payment can fail before reaching
GlomoPay in a device-specific failure mode. API 24 sits at that support boundary,
covers the overwhelming majority of active devices, keeps WebView and Kotlin/AGP
tooling comfortable, and matches the modern React Native ecosystem floor.

Merchants currently using `minSdk 21` must raise their application's minimum SDK
before adopting this library. Coordinate that change with
`developer@glomopay.com` before planning the integration.

## Installation

When the artifact is available from Maven Central:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.glomopay:glomopay-sdk:0.0.1")
}
```

For local SDK development, include the `glomopay-sdk` module in the host
application and use:

```kotlin
implementation(project(":glomopay-sdk"))
```

## Basic Integration

Implement `GlomoPayListener` and start checkout from an Activity or another
Android context:

```kotlin
class CheckoutActivity : Activity(), GlomoPayListener {
    fun startPayment(orderId: String) {
        val config = GlomoPayConfig(
            publicKey = "live_public_key",
            orderId = orderId,
            devMode = false,
        )

        GlomoPaySdk.startCheckout(this, config, this, orderType = "auto")
    }

    override fun onPaymentSuccess(payload: GlomoPayPayload) {
        // Verify the payment on the merchant server before fulfilment.
    }

    override fun onPaymentFailure(payload: GlomoPayPayload) {}
    override fun onSdkError(errors: List<SdkError>) {}
    override fun onConnectionError(error: ConnectionError) {}
    override fun onPaymentTerminate(source: TerminationSource) {}
    override fun onEvent(name: String, payload: Map<String, Any?>) {}
}
```

## Checkout Types

Supported values are `auto`, `standard`, and `lrs`.

- `auto`: detects the order type from the order API response.
- `standard`: explicitly opens Standard checkout.
- `lrs`: explicitly opens LRS checkout.

Use `auto` when the API should remain the source of truth. Provide exactly one
of `orderId` or `subscriptionId` in `GlomoPayConfig`.

## Configuration

```kotlin
val config = GlomoPayConfig(
    publicKey = "test_public_key",
    orderId = "order_example",
    server = null,
    devMode = true,
)
```

Guidelines:

- Use `test_` or `mock_` keys with `devMode = true` for development and QA.
- Use live keys only in production and on compliant devices.
- Never log public keys, identifiers, payment signatures, or raw payment data
  in production.
- Verify successful payments server-side before delivering goods or services.

## WebView and File Upload Behavior

The SDK provides:

- Native main checkout WebView and a separate secure bank/3DS flow overlay.
- JavaScript bridge events for payment, redirect, navigation, and errors.
- Android native file chooser support for hosted bank upload fields, including
  PDF/document uploads.
- Loading, connection-error, retry, and back-navigation handling.
- Root, debugger, and developer-mode compliance checks for live sessions.

The hosted checkout remains responsible for payment UI, bank authentication,
3DS, and validation of uploaded documents.

## Testing

Run SDK unit tests from this repository:

```bash
./gradlew :glomopay-sdk:test
```

The standalone wrapper app is maintained separately at
`glomopay-android-sdk-test-app`. It is used to test Standard, LRS,
subscription, validation, developer-mode, bank redirect, and file-upload
flows. The wrapper APK is a QA artifact and is not the SDK dependency.

## Documentation

- [Integration guide](docs/integration.md)
- [API reference](docs/api-reference.md)
- [Maven Central publishing guide](docs/maven-central-publishing.md)
- [Release process](docs/release-process.md)

## Support and Security

For integration questions, contact `developer@glomopay.com`. For security
reports, follow [SECURITY.md](SECURITY.md) and do not open a public issue.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
