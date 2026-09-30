# API Reference

## `GlomoPaySdk`

```kotlin
fun startCheckout(
    context: Context,
    config: GlomoPayConfig,
    listener: GlomoPayListener,
    orderType: String = "auto",
): GlomoPayCheckoutHandle
```

Starts the native checkout Activity. If the context is not an Activity, the SDK adds the required new-task flag.

Retain the returned `GlomoPayCheckoutHandle` to dismiss that session with `handle.close()`;
the listener then receives `onPaymentTerminate(PROGRAMMATIC)` once. The handle controls only
the session it was returned for, and calls after completion do nothing.

Supported `orderType` values are `auto`, `standard`, and `lrs`.

## `GlomoPayConfig`

```kotlin
data class GlomoPayConfig(
    val publicKey: String,
    val orderId: String? = null,
    val subscriptionId: String? = null,
    val server: String? = null,
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
| `onUserJourneyCompleted(GlomoPayUserJourneyPayload)` | **Required.** A non-payment journey completed, such as submitted bank-transfer details. No money has moved. |
| `onPaymentTerminate(TerminationSource)` | Checkout was closed by the user or SDK. |
| `onUserRefusedDevicePermissions(permission)` | A device permission the checkout asked for was refused. Checkout stays open. |
| `onEvent(name, payload)` | Deprecated diagnostic channel; not part of the integration contract. |

`onPaymentTerminate`, `onUserRefusedDevicePermissions` and `onEvent` have default
implementations, so a host only overrides what it uses. `onUserJourneyCompleted` has
no default on purpose: the SDK cannot tell which merchants have bank transfers enabled,
because the order decides that server-side, so a default body would let a host upgrade,
keep compiling, and silently stop hearing about a journey it used to be told about.

## `GlomoPayUserJourneyPayload`

```kotlin
data class GlomoPayUserJourneyPayload(
    val journeyType: GlomoPayUserJourneyType,
    val orderId: String,
    val senderAccountNumber: String? = null,
    val transactionReference: String? = null,
    val status: String? = null,
    val rawResponse: Map<String, Any?>? = null,
)

enum class GlomoPayUserJourneyType { BANK_TRANSFER }
```

Deliberately not a `GlomoPayPayload`: that type's `paymentId` and `signature` are the
fields a host verifies a payment with, and neither exists for a journey. Do not treat a
journey as a payment result - reconcile it server-side against the order. Fields are read
in both camelCase and snake_case, and the page's response travels verbatim in
`rawResponse`. Enum members are append-only.

## Payload and errors

`GlomoPayPayload` exposes the order ID, payment ID, signature, and raw response where available. Treat the signature as sensitive and verify payment server-side.

`SdkError` exposes an error type, message, and optional field. `ConnectionError` exposes category, message, HTTP status, failed URL, native error code, recoverability, and auto-close behavior.

## Security and logging

Strict device checks apply to live, non-developer-mode sessions. Test/mock keys and developer mode are intended for development and QA. Detailed logs are enabled only in developer mode and must not contain sensitive payment data in production.
