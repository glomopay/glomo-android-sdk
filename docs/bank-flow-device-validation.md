# Bank-flow device validation (pre-signoff)

Two behaviours in the bank/3DS flow overlay cannot be validated by unit tests,
because they only exist against a real bank page on a real device. Both are code
complete; this document is the evidence that has to exist before signoff.

## 1. Forced viewport on bank pages (feedback item 18)

`GlomoPayInjectionScripts.bankViewportFit()` is injected into the flow WebView
without a platform check, mirroring the Flutter SDK. It rewrites the bank page's
own `viewport` meta and normalises `documentElement`/`body` zoom, and it re-asserts
that on `resize` and `orientationchange`.

Why it needs device evidence: the Flutter comment records that making this iOS-only
was tried and reverted, because Android bank pages have been force-fit to the screen
all along. Removing it changes layout for pages that declare a wide or scaled
viewport; keeping it rewrites the page's own meta mid-flow. Only completed payments
across banks show which is correct.

What to watch on each run:

- Page is not horizontally scrollable and not zoomed out on first paint.
- Opening the software keyboard on an input, then closing it, does not shrink or
  re-scale the page (this is the unguarded `resize` listener re-running).
- A bank SPA that navigates between steps in-place keeps the correct layout on
  step 2 and later, not just on the first step.
- Rotation is locked to portrait, so orientation only matters in multi-window.

## 2. Predictive back (feedback item 9)

The Activity registers an `OnBackInvokedCallback` on API 33+ and keeps
`onBackPressed()` for older releases. This must be exercised as a *gesture*, not
as a button press, across the supported merchant `targetSdk` range (34–36).

What to watch:

- Inside the bank overlay: back gesture closes the overlay only, checkout stays open.
- On the main checkout: back gesture delivers `onPaymentTerminate(BACK_BUTTON)`
  and emits the Payment Terminated analytics event.
- During a pending payment: back still works (the old `paymentInProgress` lock is gone).
- Two gestures are needed to leave from inside the overlay - that is intended.

## Matrix to fill before release

Run each row to a completed or explicitly declined payment. Attach this table to
the signoff. A blank row means **pending**, never passed.

| Bank | Order type | Device / OS | Host targetSdk | Viewport OK | Keyboard OK | Multi-step OK | Predictive back OK | Rotation keeps checkout | Evidence / notes |
|---|---|---|---|---|---|---|---|---|---|
| | standard | | 34 | | | | | | |
| | standard | | 35 | | | | | | |
| | standard | | 36 | | | | | | |
| | lrs | | 34 | | | | | | |
| | lrs | | 35 | | | | | | |
| | lrs | | 36 | | | | | | |
| | standard | large-screen / Android 16+ | 36 | | | | | | |

Minimum coverage: at least 3 different banks, at least one Android 13 (API 33)
device and one Android 15+ device. Cover targetSdk 34, 35 and 36, and include one
large-screen Android 16+ rotation run because the system may ignore the portrait
request there. Build the in-repo sample host with `-PMERCHANT_TARGET_SDK=<34|35|36>`.

## Prerequisite

The standalone `glomopay-android-sdk-test-app` repository must be on a build that
no longer passes `devMode` to `GlomoPayConfig`, otherwise it will not compile
against this SDK. The in-repo `sample-app` is already updated.
