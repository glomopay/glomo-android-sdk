# Migration Guide

## 2.2.x to 2.3.0

Two optional callbacks were added to `GlomoPayListener`. **Kotlin integrations need no changes.**
Java integrations that implement the interface directly need the one change below.

### Java: implement the two new methods

`GlomoPayListener` gained `onCheckoutStageChanged` and `onCheckoutClosed`. Both have default
implementations in Kotlin, so a Kotlin class that implements the interface keeps compiling and
behaving exactly as before.

Kotlin's default interface methods do not compile to Java default methods in this SDK, so **from
Java the two methods are abstract**.

Bumping the dependency recompiles your own sources against the new AAR, so the normal outcome is a
**compile error** at build time - `does not override abstract method onCheckoutStageChanged` - and
not a runtime surprise. Add the two methods and you are done.

The runtime case is narrower and worth knowing about: a **prebuilt** Java artifact that implements
`GlomoPayListener` - your own internal AAR, or a wrapper library - is not recompiled when the app
bumps this SDK. Its compiled class still declares only the old methods, so the first time the SDK
delivers one of the new ones it fails with `AbstractMethodError`. Rebuild that artifact against
2.3.0 as well.

Add the two no-op methods:

```java
@Override
public void onCheckoutStageChanged(@NonNull GlomoCheckoutStageChangedEvent event) {
    // no-op, or read event.getStage() / event.getStep() for your funnel
}

@Override
public void onCheckoutClosed(@NonNull GlomoCheckoutClosedEvent event) {
    // no-op, or read event.getLastStage() / event.getReason()
}
```

This applies to the same three methods that were already in this position - `onPaymentTerminate`,
`onUserRefusedDevicePermissions` and `onEvent` - so a Java listener written against 2.x already
declares those. Nothing else about the interface changed.

### Everyone: nothing fires until checkout progress events are switched on

Both callbacks need checkout progress events enabled for your business, and they only ever fire on
LRS checkouts. Until then the checkout behaves exactly as it does today and neither callback runs.
Talk to your Glomo contact to have it switched on.

`onPaymentTerminate` is unchanged and still reports every termination. `onCheckoutClosed` adds
where the customer stopped; it does not replace it, and both fire for the same close.

### Treat the string fields as open

`stage`, `step`, `provider`, `method` and `reason` are plain strings, not enums, because the event
contract adds values without a new payload version. Match on the values you care about and ignore
the rest. `GlomoCheckoutStages` and `GlomoCheckoutCloseReasons` name today's values for comparison,
but neither is a closed set, and a value absent from them is still delivered.

Anything the contract adds after this SDK version is reachable through
`GlomoCheckoutStageChangedEvent.rawPayload`, which carries the payload exactly as it arrived.
