# Security policy

## Reporting a vulnerability

Do not open a public issue for security reports.

Email **security@glomopay.com** with a description, affected SDK versions, and
reproduction steps. We aim to acknowledge within two business days.

## Scope

This SDK is a thin wrapper that presents GlomoPay's hosted checkout inside a
WebView. It does not capture, store, or transmit card numbers, CVVs, or KYC
document contents. Card data is handled entirely by the hosted checkout page.

Reports that depend on modifying the host application, rooting the device, or
running under a debugger are generally out of scope — the SDK's device
compliance checks are a deterrent, not a security boundary.

## What is not a vulnerability

The SDK ships a Segment analytics **write key** and the merchant's own
**publishable key** in client code. Both are publishable by design and are
present in every distributed build of every GlomoPay SDK. Neither grants read
access to any data. Reports about their visibility will be closed as intended
behaviour.
