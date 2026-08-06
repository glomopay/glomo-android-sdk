# API Reference

## `GlomoPaySdk`

```kotlin
fun startCheckout(
    context: Context,
    config: GlomoPayConfig,
    listener: GlomoPayListener,
    orderType: String = "auto",
)
```

Starts the native checkout Activity. If the context is not an Activity, the SDK adds the required new-task flag.

Supported `orderType` values are `auto`, `standard`, and `lrs`.

## `GlomoPayConfig`

```kotlin
data class GlomoPayConfig(
    val publicKey: String,
    val orderId: String? = null,
    val subscriptionId: String? = null,
    val server: String? = null,
    val devMode: Boolean = false,
)
```

`orderId` and `subscriptionId` are mutually exclusive. `checkoutId` returns the active identifier, and `isSubscription` indicates whether a subscription ID was supplied.

## `GlomoPayListener`

| Callback | Purpose |
|---|---|
| `onPaymentSuccess(GlomoPayPayload)` | Payment completed successfully. |
| `onPaymentFailure(GlomoPayPayload)` | Payment was rejected or failed. |
| `onSdkError(List<SdkError>)` | Configuration, validation, or device compliance failure. |
| `onConnectionError(ConnectionError)` | Network, HTTP, or WebView loading failure. |
| `onPaymentTerminate(TerminationSource)` | Checkout was closed by the user or SDK. |
| `onEvent(name, payload)` | Non-terminal checkout and WebView diagnostics. |

## Payload and errors

`GlomoPayPayload` exposes the order ID, payment ID, signature, and raw response where available. Treat the signature as sensitive and verify payment server-side.

`SdkError` exposes an error type, message, and optional field. `ConnectionError` exposes category, message, HTTP status, failed URL, native error code, recoverability, and auto-close behavior.

## Security and logging

Strict device checks apply to live, non-developer-mode sessions. Test/mock keys and developer mode are intended for development and QA. Detailed logs are enabled only in developer mode and must not contain sensitive payment data in production.
