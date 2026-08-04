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

Pending decisions, both marked open in the internal repo checklist:

- **minSdk** — not yet fixed. Do not assume a value.
- **Maven coordinates** — intended `com.glomopay`, pending DNS namespace
  verification on Maven Central.

## Installation

Not yet published. This section lands with the first release.

## Support

- Integration questions: developer@glomopay.com
- Security reports: security@glomopay.com — see [SECURITY.md](SECURITY.md), and
  do not open a public issue

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
