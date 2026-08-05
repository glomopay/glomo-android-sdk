# GlomoPay Android SDK

Official Android SDK (Kotlin) for GlomoPay Standard and LRS checkout.

> **Status: pre-implementation.** This repository currently contains governance,
> CI, and contribution rules only. No SDK code has landed, nothing is published
> to Maven Central, and the public API is not stable. Do not integrate yet.

---

## What this SDK does

It presents GlomoPay's **hosted checkout** inside a WebView and relays events
between that page and the host application over a JavaScript bridge. That is the
whole job.

It deliberately does **not**:

- capture card numbers, CVVs, or expiry dates
- tokenize payment instruments
- implement 3DS or SCA logic natively
- store or transmit KYC document contents

Card data is handled entirely by the hosted checkout page, server-side. This
boundary is what keeps the SDK — and every app embedding it — out of PCI scope.
It is a hard architectural constraint, not a current implementation detail. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## Sibling SDKs

The same hosted checkout is wrapped by three other SDKs. All four must behave
identically for a given checkout configuration:

| Platform | Package | Repository |
|---|---|---|
| Flutter | `glomopay_sdk` on pub.dev | `glomopay/glomopay-flutter-sdk` |
| React Native | `@glomopay/react-native-sdk` on npm | `glomopay/glomopay-rn-sdk` |
| iOS | `GlomoPaySDK` on CocoaPods | `glomopay/glomopay-ios-sdk` |
| Android | *(this repo)* | `glomopay/glomopay-android-sdk` |

The JavaScript bridge contract they share — message names, payload shapes,
callback semantics, error codes, and the checkout URL query contract — is the
authority for this SDK's behaviour. Conform to the contract, not to whichever
sibling SDK you happened to read.

## Requirements

| | |
|---|---|
| **minSdk** | **24** (Android 7.0) |
| **compileSdk / targetSdk** | Latest stable at release time |
| **Kotlin** | 2.x |
| **Maven coordinates** | Intended `com.glomopay`, pending DNS namespace verification on Maven Central |

**Why minSdk 24.** The deciding factor is TLS, not market share. Devices below
Android 7.1.1 carry a stale CA trust store and fail handshakes against modern
certificates — which for a checkout SDK means a payment dying before it reaches
our servers, on a device we cannot reproduce, in a failure mode the merchant will
report as our bug. API 24 sits at that boundary. It also covers the overwhelming
majority of active devices, keeps WebView and Kotlin/AGP tooling comfortable, and
matches where the React Native ecosystem has landed (0.76+ requires 24).

Note for merchants already integrating a sibling SDK: this is a **higher floor**
than our React Native testing harness pins (`minSdkVersion = 21`). If your app is
currently at 21 and you adopt this SDK, you will need to raise your own minSdk.
Raise it with us before you plan the work — talk to developer@glomopay.com.

## Installation

Not yet published. This section lands with the first release.

## Support

- Integration questions: developer@glomopay.com
- Security reports: security@glomopay.com — see [SECURITY.md](SECURITY.md), and
  do not open a public issue

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
