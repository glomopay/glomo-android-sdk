# Release Process

This document is for SDK maintainers. The sample APK is a QA artifact; the publishable artifact is the `glomo-android-sdk` AAR.

## Version

Update the library version in:

```text
glomo-android-sdk/build.gradle.kts
```

Keep the same version in `CHANGELOG.md` and the README dependency example. Never reuse a version after publishing.

## Verification

From the repository root:

```bash
./gradlew clean test
./gradlew :glomo-android-sdk:assembleRelease

# Confirm consumer ProGuard/R8 rules are packaged in the AAR.
unzip -p glomo-android-sdk/build/outputs/aar/glomo-android-sdk-release.aar proguard.txt

# Run wrapper QA from the standalone test app project when required.
cd ../glomopay-android-sdk-test-app
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Manually test Standard, LRS, subscription, validation-error, connection-error, and developer-mode scenarios in the sample app.

## Publishing credentials

Maven Central publishing requires a verified Sonatype namespace, deployment credentials, and signed Maven metadata. Store credentials and signing material outside source control using Gradle properties or CI secrets. Never commit passwords, private keys, or keystores.

The intended coordinates are:

```text
groupId:    com.glomopay
artifactId: glomo-android-sdk
version:    0.0.2
```

## Publish

Follow the detailed [Maven Central publishing guide](maven-central-publishing.md) to register the namespace, complete DNS verification, configure signing, and create the publishing credentials. After the Maven publishing plugin and repository credentials are configured:

```bash
./gradlew :glomo-android-sdk:publish
```

Validate the deployment in the Sonatype Central Portal, then verify that a clean consumer project can resolve the dependency from Maven Central.

## Tag and document

Update `CHANGELOG.md`, review `README.md`, and create a Git tag matching the library version:

```bash
git tag 0.0.2
git push origin 0.0.2
```

Use the standalone test app APK only for QA. Consumers should depend on the published AAR rather than an APK or unsigned local artifact.

## Checklist

- [ ] Version updated in `glomo-android-sdk/build.gradle.kts`.
- [ ] README dependency version updated.
- [ ] Changelog entry added.
- [ ] Unit and integration tests pass.
- [ ] Release AAR builds successfully.
- [ ] AAR contains the expected `proguard.txt` consumer rules.
- [ ] A minified wrapper release build completes and its checkout JavaScript bridge is smoke-tested.
- [ ] Final-app mapping upload is configured in protected merchant CI when Sentry deobfuscation is required.
- [ ] Sample app QA completed.
- [ ] Maven metadata and signatures validate.
- [ ] Deployment is visible in Central Portal/Maven Central.
- [ ] Git tag created and pushed.
