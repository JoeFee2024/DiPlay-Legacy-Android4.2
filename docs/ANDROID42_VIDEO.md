# Android 4.2 and 4.3 video

Android 4.2 is API 17 and the fork's declared `minSdk`, but Android 4.4 was the oldest release
that had actually been exercised. Installation, settings, the AirPlay crypto handshake, manual
Bluetooth pairing, the VPN TUN and PCM audio were already covered on an API 17 emulator. Video
was not: of the ten instrumentation checks, the H.264 first frame was the one that never passed.

Four decoding changes address it, after the player split below. Each is confined to API 17/18 or
Android 4.x; every newer platform keeps its previous behaviour.

## The player on 4.2 and 4.3

The separate iOS 27 **video in car** sheet was also unavailable on 4.2: Media3 1.4.1 declares
minSdk 19, so its classes could not be loaded. `CarPlayVideo.show()` now picks
`CarPlayVideoLegacyActivity` below 19; it runs the same controls and the same iPhone URL policy on
ExoPlayer 2.19.1, which declares minSdk 16. API 19 and newer keep `CarPlayVideoActivity` with
Media3. Both activities implement `CarPlayVideoPlayer`, so `CarPlayVideo` does not branch per
platform; the legacy data source is `LegacyIphoneResolvingDataSource`, the ExoPlayer 2 port of
`IphoneResolvingDataSource`. The legacy activity draws into a plain `SurfaceView` instead of
ExoPlayer 2's `PlayerView`: both libraries define the same `exo_*` resources, and the resource
merger gives the APK Media3's `exo_player_view.xml`, whose views `PlayerView` cannot cast. Keeping
`exoplayer-ui` out of the dependency graph avoids that collision.

## Parameter sets inside the first frame

`AndroidMediaSink` prepended SPS/PPS in band only when the effective platform level was exactly
17, so Android 4.3 took the ordinary `csd-0`/`csd-1` path. Both releases ignore the
codec-specific data they are configured with and decode nothing when the parameter sets are not
inside the first access unit. `needsInBandSpsPps` now covers both. HEVC is untouched: these are
H.264 parameter sets.

The platform level is the effective one from `realSdkInt()`, not `Build.VERSION.SDK_INT`, so a
head unit that reports a newer version than it runs still takes the legacy branch.

## Trying every decoder the device lists

The fallback chain was the default decoder, then that same decoder with fewer format keys, then
the software decoder by name. No vendor decoder was ever tried by name, even though
`createDecoderByType` returns whichever codec the device lists first, which on these releases can
be one that configures and then never renders to a Surface.

On API 17/18 every remaining candidate is now tried in the order the device listed it, with
`OMX.google.*` last because its Surface support is the weakest there. A newer platform keeps the
single software fallback, where enumerating the rest buys nothing. Each `getCodecInfoAt` call is
isolated, so one broken vendor entry no longer aborts configuration for the whole codec.

## Waiting for the first frame

Output was drained with a zero timeout. This is an inference about Android 4.x behaviour rather
than a measured fact: a decoder that is still working looks permanently idle when polled, so its
first frame may only be reported from a dequeue that can wait. Before the first frame, API below
21 now drains with a 5 ms timeout. Later frames keep the zero-cost poll, because there the frame
is already decoded and waiting would only add latency.

A codec that never produces a frame cannot stall the worker: the wait is per dequeue and a drain
returns as soon as the codec is idle.

## Refusing to park before touching an absent class

A surface detach with `parkMain` built `ParkingOutput` first and checked the platform level
afterwards. `ImageReader` is API 19, so on API 17/18 constructing it raises
`NoClassDefFoundError`. That is a `LinkageError`, not an `Exception`: it passed both
`catch (error: Exception)` handlers in the detach path and killed the decoder worker, so no later
frame would ever decode. The check now runs before the construction.

This path is only reachable with **Smooth video (experimental)** enabled, which is off by
default.

## Verification

`LegacyVideoDecodeTest` covers the three level-dependent decisions. Robolectric cannot run API 17
or 18, so each function takes the platform level as an argument and the assertions cover the
branches those releases take. The level itself, and the whole decode path, belong to the
instrumentation run.

`aRefusedParkDoesNotKillTheDecoderWorkerBelowAndroidSix` performs two consecutive detaches with
`parkMain` set and requires both to be confirmed. A worker killed by the error above could not
confirm the second one. It returns immediately on Android 6 and newer.

Verified on this branch: 1,170 shared and 925 common unit cases, three debug lints and three
debug APK builds. The eight failing cases are identical on `main` and are not from this change.

**Not verified.** The first-frame drain is an inference, and neither an API 17 emulator nor a
4.2 head unit was run. The earlier failure record came from an x86 emulator, whose software
decoder is not the vendor OMX decoder a real head unit uses, so this path may already have
worked on hardware. If a 4.2 head unit still shows a black screen, the next step is an API < 21
ByteBuffer decode with `Surface.lockCanvas` rendering. Export a diagnostic report and read the
`decoder=` lines to see which decoder was selected and whether it configured.

Native libraries are a separate limit: they are still built with `APP_PLATFORM=android-19`, so
API 17 and 18 devices have no API 19 native guarantee.

## Calls that are missing on API 17

The video work above was not the only thing between Android 4.2 and a session. `desugar_jdk_libs`
backports java.time, streams, nio, `Objects`, `String` and `StandardCharsets`, but **no Android
framework method and almost no remaining JDK method**. Referencing one above the platform level
fails when the code runs, with a `LinkageError` that a surrounding `catch (error: Exception)`
cannot contain.

Five were found by scanning the main sources against the SDK's own `api-versions.xml`:

| Call | Needs | Where it ran | Symptom on 4.2 |
| --- | --- | --- | --- |
| `Long.toUnsignedString` / `Integer.toUnsignedString` | 26 | `CarPlayMediaEngine` rendering the AirPlay plist | **crash at video SETUP** |
| `MotionEvent.actionToString` | 19 | every touch forwarded to CarPlay | crash on first touch |
| `Math.floorDiv` / `Math.floorMod` | 24 | `EchoReference` window maths, `NtpClock` | crash when the window or clock is used |
| `InetAddress.getLoopbackAddress` | 19 | `BufferedAudioStream` default peer | crash when that stream is constructed |
| `Throwable.addSuppressed` | 19 | 13 sites in the teardown paths | crash during cleanup |

### Why the plist one crashed roughly half of all connections

`BplistCodec` returns every plist integer as a signed `Long`, built by shifting bytes. An iPhone
sends a 64-bit video seed, so the high bit is set about half the time and the value arrives
negative. `unsignedPlistDecimal` then called `Long.toUnsignedString` on it, which is API 26. The
call sits in `onDataStream`, the SETUP immediately before the picture, which is exactly when a
4.2 head unit died. `Long.unsignedDecimal` and `Int.unsignedDecimal` now render the same text
through `BigInteger`.

Note that Kotlin's own `ULong.toString()` is built on the same platform method, so it is not a
workaround.

### What replaced them

| Was | Now |
| --- | --- |
| `Long.toUnsignedString` / `Integer.toUnsignedString` | `Long.unsignedDecimal` / `Int.unsignedDecimal` |
| `MotionEvent.actionToString` | `touchActionName`, matching the platform's text |
| `Math.floorDiv` / `floorMod` | `floorDivBySecond` / `floorModBySecond` in `compat` |
| `InetAddress.getLoopbackAddress` | `InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))` |
| `Throwable.addSuppressed` | `Throwable.suppressSecondary` |

`suppressSecondary` deliberately swallows the linkage failure. Suppression is diagnostic, and
losing it is better than losing the session that was being torn down.

### Keeping them out

`LegacyApiGuardTest` scans the three minSdk-17 main source sets for this denylist and fails on a
match, ignoring comments so that prose describing a ban does not trip it. It is the guard that
found `NtpClock`, which a one-off scanner missed.

**Known limits of that guard.** It matches call text, so it cannot see a reference hidden behind
reflection, and it only covers the listed names. Lint's `NewApi` check covers the rest but is not
enabled for this source set, and it cannot tell a compile-time constant from a real reference: every
`MediaFormat.MIMETYPE_*`, `SoftApConfiguration.BAND_*`, `KeyEvent.KEYCODE_*` and
`PendingIntent.FLAG_*` in this tree is inlined by the compiler and costs nothing at runtime, but a
scan that does not distinguish them drowns in false positives. `Inet4Address.LOOPBACK` is the one
that would have been the obvious replacement and it is not public in Android's SDK.

## References that fail when the class loads

The calls above fail when they run. A second class of reference fails earlier. Dalvik resolves every
type in a class's `catch` table, superclass and interface list while it links the class, so a type
that is absent on API 17 rejects the *whole class* with `VerifyError` before any method runs. On
this branch that surfaced as:

```
FATAL thread=main java.lang.VerifyError: com/shilapi/xcertplay/orchestration/CarPlayController
  at CarPlayHostActivity.startCarPlay (CarPlayHostActivity.kt:4216)
```

`CarPlayController.isBluetoothDeviceConnected` caught `ReflectiveOperationException`, which is API
19. The handler could never run before the catch clause was resolved, so the controller died at
construction and the connection screen stayed on "getting CarPlay ready".

Scanning all four DEX files with `dexdump -d` against the SDK's `api-versions.xml` found five more
of the same shape:

| Reference | Needs | Where | Symptom on 4.2 |
| --- | --- | --- | --- |
| `ReflectiveOperationException` in a `catch` | 19 | `CarPlayController`, `ManualHotspotManager`, `LocalOnlyHotspotManager` | **the reported crash**; the other two would fail when the car-hotspot or LOHS path loaded |
| `android.system.ErrnoException` in a `catch` | 21 | `BydCarPlayCallTool` | `VerifyError` as soon as the BYD tool class loaded |
| `java.lang.AutoCloseable` as an interface | 19 | `Iap2Session`, `Iap2LinkChannel`, `Iap2CsmChannel`, `Iap2LocationProvider` | `VerifyError` when the iAP2 session or link was created |
| `ThreadLocal.withInitial` | 26 | `AirPlayCrypto` platform cipher | the desugared helper takes a `java.util.function.Supplier`, absent on 17 |
| `Map.removeIf`, three sites | 24 | `AndroidMediaSink` surface detach and decoder teardown | the predicate lambda implements `java.util.function.Predicate` |
| `Map.computeIfAbsent` | 24 | `AndroidMediaSink.onMicrophoneStarted` | the lambda implements `java.util.function.Function`; microphone start failed |

### What replaced them

| Was | Now |
| --- | --- |
| `catch (error: ReflectiveOperationException)` | `catch (error: Exception)`; `RuntimeException` is still logged, and `Throwable.isReflectiveFailure()` narrows where the original did |
| `catch (error: android.system.ErrnoException)` | `BydProcessSignals`, an `@RequiresApi(21)` object the API 17 path never loads |
| `: AutoCloseable` | `: java.io.Closeable`, which exists since API 1 |
| `ThreadLocal.withInitial { ... }` | an anonymous `ThreadLocal` overriding `initialValue()` |
| `entries.removeIf { ... }` | an `Iterator` loop |
| `map.computeIfAbsent(key) { ... }` | `get`, then `putIfAbsent` on the `ConcurrentHashMap`, closing the loser of a race |

Field and method-signature types are *not* fatal by themselves. `ExistingWifiManager` keeps
`android.net.Network` fields and `CarPlayMediaCallback` extends `MediaSession.Callback`, but both
classes are only instantiated on API 21 and newer. Only handler types, superclasses and interfaces
are resolved when the class loads.

### Finding the next one

`LegacyApiGuardTest` now also bans `ReflectiveOperationException`, `AutoCloseable`, `.removeIf`,
`.computeIfAbsent` and `ThreadLocal.withInitial`. A text guard cannot see a synthetic lambda's
interface, so the DEX check is the reliable one:

1. `unzip classes*.dex` from the built APK and run `dexdump -d` on each file.
2. Split the output on `Class descriptor` and keep the app packages.
3. In each block, check the `catches` table (`L...; -> 0x...`), the `Superclass`/`Interfaces`
   header, and every synthetic lambda's interface list.
4. Compare each type against the `since` attribute in `platforms/android-36/data/api-versions.xml`.
   A missing attribute means API 1.

A lambda that still implements `java.util.function.*` is a red flag even when the call itself was
desugared: D8 rewrote `removeIf` to `j$/util/Collection$-EL`, but the predicate it passed still
implemented `java.util.function.Predicate`.

### Verification

1,180 shared and 932 common unit cases: the same 3 `AirPlayCryptoChachaTest` and 5 UI cases fail on
`main`. Three debug lints and three debug APKs pass. The rebuilt APK was scanned again: no app class
implements `java.util.function.*`, and the only API-above-17 `catch` type left is the isolated
`BydProcessSignals`.

**Not verified.** There is still no API 17 hardware run. The next crash after this fix, if any, is
one of the references the scan cannot reach: reflection, a vendor class, or a type only resolved
when a specific instruction executes.