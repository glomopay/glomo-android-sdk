# Changelog

All notable changes to the Glomo Android SDK are documented here.
This project follows [Semantic Versioning](https://semver.org/).

The public API is not stable until 1.0.0. Breaking changes may land in any
0.x release.

## Unreleased

### Added

- Added dependency-free Mixpanel REST analytics for SDK, compliance, checkout,
  WebView, redirect, payment, error, and file-upload lifecycle events.
- Added build-time `MIXPANEL_TOKEN` injection, PII filtering, external URL
  sanitization, and fire-and-forget delivery that cannot block checkout.
- Added an isolated SDK-owned Sentry client for explicitly captured SDK and
  analytics delivery failures, with build-time `SENTRY_DSN` injection.
- Kept global Sentry initialization, app-wide crash/ANR capture, NDK, Session
  Replay, default PII, and performance tracing disabled.
- Added consumer ProGuard/R8 rules that preserve JavaScript bridge callbacks and
  source line metadata while allowing the remaining SDK code to be optimized.
- Aligned Mixpanel telemetry with the mobile analytics v1.1 contract: hostname-only
  bank URLs, special device/app properties, network transport signals, IP-derived
  geolocation, explicit null values, session insert IDs, and `main`/`flow` WebView types.
- Added the normal `ACCESS_NETWORK_STATE` permission for Wi-Fi/cellular telemetry;
  it does not require a runtime permission prompt.

## [0.0.2]

### Changed

- All 4 points bugs/changes of android-sdk is completed with version 0.0.2
- Restored WebView loading percentages for the checkout and secure bank-flow
  overlays without restoring the removed top progress bar.

## [0.0.1] - Initial native Kotlin SDK

### Added

- Native Kotlin SDK module with public GlomoPay configuration and listener APIs.
- Standard, LRS, subscription, and automatic order-type checkout handling.
- Hosted checkout WebView with JavaScript bridge event routing.
- Separate secure flow WebView overlay for bank and 3DS redirects.
- Payment success, failure, pending, cancellation, SDK error, and connection
  error callbacks.
- Root, debugger, developer-mode, and device compliance checks.
- Loading, retry, error, back-navigation, and checkout termination handling.
- Android native file chooser support for hosted bank document uploads.
- Local test-wrapper integration and SDK unit-test coverage.

### Fixed

- Android hosted bank upload controls now open the native file picker and pass
  selected `content://` document URIs back to the WebView.
- Bank/3DS pages are rendered above the main checkout instead of replacing the
  main WebView layer.

### Changed

- Renamed the Android artifact to `glomo-android-sdk`.
- Moved Kotlin and Java APIs to the `com.glomopay.sdk.android` package.

### Distribution

- Maven coordinates are planned as `com.glomopay:glomo-android-sdk:0.0.1`.
- Maven Central publication and signed release metadata are still pending.
