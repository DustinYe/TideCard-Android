# Subscription-ID compatibility diagnosis

On 2026-10-29 a reporter using the latest released app supplied a screenshot showing an activated backend, no SIM readback, and an `Integer.intValue()` null-pointer error. The phone model, OS version and SIM-slot state were unavailable. This is a diagnosis from source and a JVM reproducer, not a reproduction on that phone.

The old `CarrierBackend.subId` cast the reflected `ISub.getSubId(slot)` result to `Integer` and returned primitive `int`. A null result is unboxed and throws a null-pointer exception. Historical ISub APIs return `int[]`; newer APIs return `int`. A legacy array on an active slot also failed the old cast. `inspect` always visits both slots, independent of the selected write mask, so an empty second slot could prevent the entire readback.

The 2.4.1 fix accepts scalar Integer and legacy int[] results, treats null / empty / invalid sentinel results as no active subscription, and preserves slot identity. Multiple distinct valid IDs or unsupported formats fail closed instead of selecting another SIM. Apply/restore still verify that the original slot's subscription ID matches the saved snapshot. No arbitrary subscription-ID fallback or additional write permission was introduced.

The plain JVM regression reproduces the original null-unboxing and array-cast failures, then covers modern and legacy reflected services, single/dual-slot masks, empty results, sentinel IDs, duplicate IDs, ambiguous IDs and unsupported types. It performs no device access or carrier writes:

```text
javac -encoding UTF-8 -d build/host-subscription-checks app/src/main/java/app/tidecard/SubscriptionIdResolver.java app/src/androidTest/java/app/tidecard/SubscriptionIdChecks.java
java -cp build/host-subscription-checks app.tidecard.SubscriptionIdChecks
```

Result: 22 checks passed. This patch still needs testing on the affected phone before claiming that it resolves that device's entire activation/write path.

References:

- Historical AOSP ISub array signature: https://android.googlesource.com/platform/frameworks/base/+/a952d2018bf9872565d4ac967ea433b6e9161543/telephony/java/com/android/internal/telephony/ISub.aidl
- Current AOSP ISub scalar signature: https://android.googlesource.com/platform/frameworks/base/+/master/telephony/java/com/android/internal/telephony/ISub.aidl
- Historical null/no-active-subscription contract: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-29/+/refs/heads/main/android/telephony/SubscriptionManager.java
