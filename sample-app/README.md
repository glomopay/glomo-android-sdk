# GlomoPay Android SDK Sample App

In-repository Kotlin application for manually testing the native Android SDK.
It consumes the local `:glomo-android-sdk` module and provides:

- Public key and Order ID / Subscription ID inputs.
- Automatic Standard/LRS checkout detection.
- Developer mode control.
- Payment result, SDK error, connection error, termination, and event output.

No credentials or test identifiers are stored in the application. Enter sandbox
values at runtime when testing.

## Run

From the repository root:

```bash
./gradlew :sample-app:testDebugUnitTest
./gradlew :sample-app:assembleDebug
./gradlew :sample-app:installDebug
```

Open the repository root in Android Studio and run the `sample-app` configuration
on an emulator or physical Android device.

The sample APK is a QA tool only. Merchants integrate the published
`com.glomopay:glomo-android-sdk` dependency, not this application.
