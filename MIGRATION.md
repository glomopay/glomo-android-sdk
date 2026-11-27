# Migration Guide

## 2.2.x to 2.3.0

**No changes are required.** Both new `GlomoPayListener` methods have default implementations and
compile as Java default methods, so an existing listener keeps compiling and behaving exactly as
before whether it is written in Kotlin or in Java.

### Java listeners gained five optional methods

This release builds with `-Xjvm-default=all-compatibility`, so every defaulted method on
`GlomoPayListener` is a real Java default method rather than an abstract one backed by
`DefaultImpls`. A Java class implementing the interface no longer has to declare
`onPaymentTerminate`, `onUserRefusedDevicePermissions` or `onEvent`, and does not have to declare
the two new methods either. Classes that already declare them are unaffected, and the
`DefaultImpls` bridges are still emitted, so anything compiled against 2.x still links.

### Opting in to checkout progress

Override either or both:

```java
@Override
public void onCheckoutStageChanged(@NonNull GlomoCheckoutStageChangedEvent event) {
    // event.getStage(), event.getStep(), event.getProvider()
}

@Override
public void onCheckoutClosed(@NonNull GlomoCheckoutClosedEvent event) {
    // event.getLastStage(), event.getReason()
}
```

Neither fires until checkout progress events are switched on for your business, and both are LRS
only. Talk to your Glomo contact to have it enabled. `onPaymentTerminate` is unchanged - it still
reports the terminations it always did, and as before it does not fire once a payment has reported
success or failure; `onCheckoutClosed` adds where the customer stopped.

### Treat the string fields as open

`stage`, `step`, `provider`, `method` and `reason` are plain strings, not enums, because the event
contract adds values without a new payload version. Match on the values you care about and ignore
the rest. `GlomoCheckoutStages` and `GlomoCheckoutCloseReasons` name today's values for comparison,
but neither is a closed set, and a value absent from them is still delivered.

Anything the contract adds after this SDK version is reachable through
`GlomoCheckoutStageChangedEvent.getRawPayload()`, which carries the payload exactly as it arrived,
including fields that have no typed getter yet. The same map is on `GlomoCheckoutClosedEvent` for a
close the page sent; it is null on a close the SDK wrote itself, because there was no payload.

### The event types are not yours to construct

`GlomoCheckoutStageChangedEvent` and `GlomoCheckoutClosedEvent` have internal constructors: the SDK
builds them, you read them. Kotlin callers cannot construct one at all; from Java the constructor
is technically reachable, but it is not part of the published API and its parameter list will grow
as contract v1 adds fields, so a call site that uses it will not keep compiling. They are also
deliberately not data classes, so that adding a field stays binary compatible. If you need to
exercise your listener in a unit test, call it through the SDK or hold the values you care about
rather than constructing an event.
