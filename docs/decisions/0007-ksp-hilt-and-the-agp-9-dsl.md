# 0007 - KSP, Room's compiler and Hilt on AGP 9

Status: accepted, build green. Took four build cycles, inside the timebox.

## What fought, in order

AGP 9.4.1 and the annotation-processing ecosystem disagree, and each step named its own fix.

**1. KSP refuses AGP's built-in Kotlin.** Verbatim:

    KSP is not compatible with Android Gradle Plugin's built-in Kotlin. Please disable by
    adding android.builtInKotlin=false to gradle.properties and apply kotlin("android") plugin

So `android.builtInKotlin=false`, and the standard Kotlin Gradle plugin comes back, which is the
same plugin decision 0004 had removed because AGP 9 refused it. Both refusals are real; they just
apply in different configurations.

**2. The Kotlin plugin then fails on AGP 9's new DSL.** Verbatim:

    The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0 new DSL
    (`android.newDsl=true` is enabled by default).
    Solution: Set `android.builtInKotlin=true` ... or set `android.newDsl=false` ... to
    temporarily bypass this issue.

Those two options are mutually exclusive with step 1: built-in Kotlin is exactly what KSP refuses.
Room's compiler and Hilt both need KSP, so the only path is `android.newDsl=false`.

**3. Kotlin 2.4.20 does not help.** Tried, same refusal. The incompatibility is with the DSL, not
the Kotlin version, so the project stays on 2.2.20, whose KSP (2.2.20-2.0.4) was already resolved
from the repository and whose distribution is already on this machine.

## Final configuration

    AGP            9.4.1
    Gradle         9.6.0
    Kotlin         2.2.20      (KGP applied, AGP's built-in Kotlin off)
    KSP            2.2.20-2.0.4
    Room           2.8.5       runtime, ktx and compiler
    Hilt           2.60.1
    gradle.properties: android.builtInKotlin=false
                       android.newDsl=false

## This configuration has an expiry date

AGP's own wording is that `android.newDsl=false` is there to "temporarily bypass this issue". A
future AGP will remove it, and when it does there are two options and no third:

- move to AGP's built-in Kotlin and drop KSP, which means replacing Room's compiler and Hilt with
  something that does not need annotation processing, or
- stay on the last AGP that honours the flag.

Either is a real piece of work. It is recorded here so that whoever hits it finds the reasoning
rather than rediscovering it under time pressure. Nothing about this is urgent for the hackathon.

An alternative considered and rejected: retreat to AGP 8.13.2 with downgraded androidx
dependencies. Rejected because the dependency versions the project needs require AGP 9.1 or
higher (decision 0004), so that retreat trades one blocked path for another.

## What was added

- `KatoriDatabase`, the user's writable database, with all twelve entities and nine DAOs.
- `KatoriConverters`, which stores enums **by name, never by ordinal**. An ordinal silently
  changes meaning when somebody reorders an enum, and here that would relabel the confidence band
  on every meal the user has ever logged.
- **No `fallbackToDestructiveMigration`, anywhere.** This database holds meals, lab values and
  declared conditions that the user typed in and cannot get back. A missing migration must fail
  loudly in development rather than delete a person's health history on upgrade.
- Room schema export to `app/schemas`, committed, so a schema change is a reviewable diff instead
  of a crash somebody finds on their phone.
- `KatoriApp` with `@HiltAndroidApp`, deliberately empty. Work in `Application.onCreate` runs on
  the main thread before the first frame, which is how an app that loads a multi-gigabyte model
  earns an ANR at launch (spec 9.3).
- `AppModule` providing the database, the food lookup and the rules engine, and wiring the
  unmatched-utterance sink so every failed match is recorded.

**No ml/ engine is provided by the module**, on purpose. Those need the ModelArbiter and a real
runtime, and none is implemented. Binding a stub there would hand the UI something that compiles
and returns nothing useful, which is how a not-implemented state quietly becomes a fake one.

## Verified

48 tests still passing, APK 34.5 MB, permissions still asserted against the whitelist.
