# Android 4.2 (API 17) API compatibility

DiPlay runs on head units whose framework is API 17 (Android 4.2), even when the ROM reports a
newer `Build.VERSION.SDK_INT`. This file records the API levels the project depends on, the
checks that make the newer calls safe, and the calls that must never run on 17/18.

Video-specific 4.2 behaviour is documented in [ANDROID42_VIDEO.md](ANDROID42_VIDEO.md).

## Build configuration

| Module | minSdk | compileSdk | targetSdk | File |
|---|---|---|---|---|
| `:mobile` | 17 | 37 | 37 | `mobile/build.gradle.kts:31` |
| `:common` | 17 | 37 | 37 | `common/build.gradle.kts:12` |
| `:shared` | 17 | 37 | 37 | `shared/build.gradle:9` |

`common` and `shared` enable `coreLibraryDesugaring(libs.desugar.jdk.libs)` (2.1.5). The
desugaring backports `java.time`, `java.util.stream`, `java.util.function`,
`java.util.Optional`, `java.util.Base64`, `Map.getOrDefault`, `Objects`, and similar JDK 8 APIs
to `j$`/`Lj$` classes in the APK. It does **not** backport Android framework methods or the rest
of the JDK (for example `Throwable.addSuppressed`, `AutoCloseable`, `List.removeIf`).

## Version checks

- `realSdkInt()` is defined in
  `shared/src/main/java/com/shilapi/xcertplay/compat/RealSdkInt.kt:15`. Use it for every
  runtime framework-API decision. Several head units (Allwinner T3 and other 4.4-framework
  ROMs) report API 27/28 while running a KitKat framework; `Build.VERSION.SDK_INT` would take
  the newer branch and throw `NoSuchMethodError`.
- `Build.VERSION.SDK_INT` is only acceptable where the declared level itself is the feature
  (for example BYD's `SDK_INT >= 36` tethering API) or where an unknown flag bit is harmless
  (for example `PendingIntent.FLAG_IMMUTABLE`). New code should use `realSdkInt()`.
- `common/src/test/java/com/shilapi/xcertplay/LegacyApiGuardTest.kt` fails the build when one
  of the calls that broke 4.2 is reintroduced. It is the enforced guard for hard failures.
- Lint does not understand `realSdkInt()`. `:shared:lintDebug` and `:common:lintDebug` therefore
  report every guarded call as `NewApi`. They are useful as an inventory, not as a gate; do not
  add them to CI until the false positives are annotated.

## Landmines fixed for 4.2

Dalvik rejects a whole class when a catch type, superclass or interface is missing. Field types
and method references fail only when executed. These were either hit on the 4.2 unit or found
by the API audit:

| Missing on 17/18 | API | Symptom | Fix and file |
|---|---|---|---|
| `FileLock.use` / implicit `AutoCloseable` cast | 19 | `VerifyError` when the class loads | `try/finally { lock.release() }` in `shared/.../network/HotspotJoinLock.kt:10` and `shared/.../hud/CarPlayCallWatchOwnership.kt:151` |
| `Socket`/`ServerSocket`/`DatagramSocket` used as `Closeable` | 19 | `NoSuchMethodError`/`ClassCastException` while closing | `closeQuietly()` helpers and concrete `close()` in `shared/.../airplay/AirPlaySocketBinder.kt` |
| `UsbDeviceConnection.bulkTransfer` 5-arg | 18 | `NoSuchMethodError` on USB transfer | `bulkTransferCompat()` in `shared/.../transport/UsbApiCompat.kt:34`; below 18 it uses the 4-arg `(length, timeout)` overload and slices with `copyOfRange`. Used by `IphoneUsbHost.kt:497` and `NcmUsbBridge.kt:88` |
| `NetworkInterface.getIndex()` | 19 | `NoSuchMethodError` while enumerating interfaces | `interfaceIndexOrZero()` in `shared/.../network/WirelessHostAddress.kt:12`; below 19 reads `/sys/class/net/<if>/ifindex`. Used by `LegacyExistingWifi`, `LegacyWifiP2pGroupManager`, `ManualHotspotInterfaces` and `AirPlaySocketBinder` |
| `FrameLayout.LayoutParams(FrameLayout.LayoutParams)` | 19 | `NoSuchMethodError` when laying out the cluster map | explicit width/height/gravity/margins in `common/.../ClusterMapPresentation.kt:69` |
| `BluetoothManager` class | 18 | `NoClassDefFoundError` in Bluetooth diagnostics | below 18 use `BluetoothAdapter.getDefaultAdapter()`: `common/.../BluetoothDiagnosticActivity.kt:117`, `common/.../BluetoothProbeService.kt:38` |
| `BluetoothDevice.getType()` | 18 | `NoSuchMethodError` | guarded in `common/.../BluetoothDiagnosticActivity.kt:157` |
| `AccessibilityNodeInfo.getViewIdResourceName()` | 18 | `NoSuchMethodError` in the USB auto-confirm service | guarded in `common/.../UsbAutoConfirmService.kt:49,60` |
| `MediaCodec.getName()` | 18 | `NoSuchMethodError` in decoder logging | `mediaCodecName()` in `shared/.../media/AndroidMediaSink.kt:762`, guarded with `realSdkInt()` |
| `AudioManager.registerMediaButtonEventReceiver(PendingIntent)` | 18 | `NoSuchMethodError` when starting media keys | `ComponentName` overload (API 8) in `common/.../CarPlayMediaKeys.kt:237`; `legacyButtonComponent` stores it for unregister |
| `AudioTrack.setVolume(float)` | 21 | `NoSuchMethodError` in the audio-channel preview | `setStereoVolume()` below 21 in `common/.../AudioChannelPreview.kt:75` |
| `AudioFocusRequest` type in a field | 26 | potential `NoClassDefFoundError` | keep the field as `Any?` and cast inside the `realSdkInt() >= 26` branch: `AndroidMediaSink.kt` and `CarPlayMediaKeys.kt` |
| `MediaSession.Callback` subclass | 21 | `VerifyError` if the callback class loads | callback is created lazily only in the `realSdkInt() >= 21` branch: `CarPlayMediaKeys.kt:224,348` |
| `FileLock.release()` without `use` | — | the same FileLock fix must also run on success | release in `finally`; `HotspotJoinLock` and `CarPlayCallWatchOwnership` |

`LegacyApiGuardTest` additionally bans `Long.toUnsignedString`, `Integer.toUnsignedString`,
`MotionEvent.actionToString`, `Math.floorDiv`, `Math.floorMod`, `addSuppressed`,
`getSuppressed`, `Objects.requireNonNull`, `InetAddress.getLoopbackAddress`,
`HandlerThread.quitSafely`, `ReflectiveOperationException`, `removeIf`, `computeIfAbsent`,
`ThreadLocal.withInitial`, `AutoCloseable`, `: Closeable>`, `as Closeable`, `Socket().use`
and `lock().use`.

## Runtime-gated framework calls

Every framework call above API 17 is inside a `realSdkInt()` (or `Build.VERSION.SDK_INT`)
branch. Line numbers come from the last `lintDebug` run; the exhaustive per-line list is in
`shared/build/reports/lint-results-debug.txt` and
`common/build/reports/lint-results-debug.txt`.

### API 18

| Call | Files |
|---|---|
| `BluetoothDevice.getType`, `BluetoothManager`, `addSuppressed`-related diagnostics | `BluetoothDiagnosticActivity.kt:117,157`, `BluetoothProbeService.kt:38` |
| `UsbDeviceConnection.bulkTransfer` (5-arg) | `transport/UsbApiCompat.kt:42` |
| `MediaCodec.getName` | `media/AndroidMediaSink.kt:764` |
| `AccessibilityNodeInfo.getViewIdResourceName` | `UsbAutoConfirmService.kt:49,60` |

### API 19

| Call | Files |
|---|---|
| `Context.getExternalFilesDirs` | `DiagnosticExportStore.kt:54,103` (inside `runCatching`) |
| `AudioTimestamp` / `AudioRecord.getTimestamp` | `media/MicrophoneUplink.kt:491,506-507` |
| `ImageReader`, `Image.close` | `media/AndroidMediaSink.kt:731-751` (only for `SURFACE_PARKING_MIN_SDK` = 23) |
| `View.isAttachedToWindow` | `CarPlayHostActivity.kt:4856` (window token below 19) |
| `View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY` | `CarPlayVideoLegacyActivity.kt:80` (constant; OR-ed only when `realSdkInt() >= 19`) |
| `NetworkInterface.getIndex` | `network/WirelessHostAddress.kt:14` (`/sys` fallback) |
| `CreateDocument` / `OpenDocument` contract names | `DiPlayActivity.kt:244,253`, `CarButtonImagePicker.kt:32`, `CarPlayHostActivity.kt:257` (constants only; no framework call) |

### API 20

| Call | Files |
|---|---|
| `Notification.Action.Builder` | `DiPlaySessionService.kt:49` (`realSdkInt() >= M` branch; legacy `addAction` below) |

### API 21

| Call | Files |
|---|---|
| `MediaSession`, `PlaybackState`, `MediaSession.Callback` | `CarPlayMediaKeys.kt:134,157,223-333,348` |
| `AudioAttributes`, `AudioAttributes.Builder` | `AudioChannelPreview.kt:48-53`, `CarPlayMediaKeys.kt:206-209`, `media/AndroidMediaSink.kt:162,1665,1672` |
| `AudioTrack.Builder` / `AudioFormat.Builder` | `media/AndroidMediaSink.kt:1593-1640`, `AudioChannelPreview.kt:46-64`, `media/MicrophoneUplink.kt:160-164` |
| `MediaCodec.getInputBuffer/getOutputBuffer/releaseOutputBuffer` | `media/AndroidMediaSink.kt:1195,1280,1906,1967`, `media/OpusEncoder.kt:61,103` (legacy `inputBuffers` below 21) |
| `MediaCodecList()/#getCodecInfos`, `CodecCapabilities#getVideoCapabilities`, `VideoCapabilities.*` | `media/AndroidMediaSink.kt:1039`, `CarPlayHostActivity.kt:3550-3575`, `CarPlayRotation.kt:51-73` |
| `ConnectivityManager.getAllNetworks/getLinkProperties/getNetworkCapabilities`, `LinkProperties#getInterfaceName`, `NetworkCapabilities.*` | `network/ManualHotspotInterfaces.kt:35-42`, `network/ExistingWifiManager.kt:214`, `CarPlayVideoActivity.kt:337-344`, `CarPlayVideoLegacyActivity.kt:324-326` |
| `VpnService.Builder#setBlocking/addAllowedApplication` | `network/CarPlayVpnService.kt:160,186` |
| `NsdServiceInfo#setAttribute/getAttributes` | `network/CarPlayBonjour.kt:441,509` |
| `WifiInfo#getFrequency` | `network/ManualHotspotManager.kt:238` |
| `Context.getNoBackupFilesDir` | `adb/AdbKeys.kt:36`, `orchestration/CarPlayController.kt:827`, `DiPlayBootstrap.kt:19` |
| `Activity.finishAndRemoveTask` | `CarPlayHostActivity.kt:4815` |
| `ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy` | `orchestration/CarPlayController.kt:279` |
| `UsbDeviceConnection.setInterface`, `UsbInterface.getAlternateSetting` | `transport/LegacyUsbHostCompat.kt:162`, `transport/UsbApiCompat.kt:96`, `transport/IphoneCarPlayConfiguration.kt:107` |
| UI: `RippleDrawable`, `Outline`, `ViewOutlineProvider`, `setClipToOutline`, `setOutlineProvider`, `setStateListAnimator`, status/navigation bar colours, `setLetterSpacing`, `setFontFeatureSettings`, tint lists, `Switch.setShowText`, `AbsSeekBar.setSplitTrack` | `CenterMapOverlay.kt:142-146`, `DiPlayActivity.kt:298-299,659-714,778,979-1031,3811,4550,4699`, `CarPlayHostActivity.kt:2520-2632`, `CarPlayVideoActivity.kt:172-181`, `CarPlayVideoLegacyActivity.kt:161-170`, `settings/SettingsWidgets.kt:80-303` |

### API 23

| Call | Files |
|---|---|
| `Context.getSystemService(Class)`, `checkSelfPermission`, `Settings.canDrawOverlays`, `Settings.System.canWrite` | `media/AndroidMediaSink.kt:241`, `compat/BluetoothCompat.kt:44`, `network/CarHotspotTethering.kt:31`, `CarHotspotSetup.kt:25-26`, `CenterMapOverlay.kt:48`, `FloatingDotOverlay.kt:33`, `UsbPermissionSetup.kt:23`, `CarPlayVideoLegacyActivity.kt:323`, and the other `getSystemService` sites |
| `AudioTrack.Builder`/`AudioRecord.Builder` (full paths above) | `media/AndroidMediaSink.kt:1593-1640`, `media/MicrophoneUplink.kt:157-167`, `AudioChannelPreview.kt:46-64` |
| `AudioTrack.getBufferSizeInFrames/getRoutedDevice`, `AudioRecord.getRoutedDevice/read(ByteBuffer)`, `AudioDeviceInfo#getType` | `media/AndroidMediaSink.kt:1668,2012,2108,2111`, `media/MicrophoneUplink.kt:276,376` |
| `MediaCodec.setOutputSurface` | `media/AndroidMediaSink.kt:1115,1152` |
| `ConnectivityManager.getActiveNetwork` | `network/ManualHotspotInterfaces.kt:42,65`, `CarPlayVideoActivity.kt:333`, `CarPlayVideoLegacyActivity.kt:324` |
| `View.setForeground`, `Switch` tint lists | `DiPlayActivity.kt:778,979-1030,4701`, `CarPlayHostActivity.kt:2525-2529`, `settings/SettingsWidgets.kt:86-140` |

### API 24

| Call | Files |
|---|---|
| `Configuration.getLocales/setLocales`, `LocaleList.get` | `AppLocale.kt:101-108`, `InterfaceSize.kt:56-76` |
| `Activity.isInMultiWindowMode` | `CarPlayHostActivity.kt:4342` |
| `AudioRecord.getTimestamp` | `media/MicrophoneUplink.kt:503` |
| `AudioTrack.getUnderrunCount` | `media/AndroidMediaSink.kt:2209` |

### API 26

| Call | Files |
|---|---|
| `NotificationChannel`, `Notification.Builder(Context, channel)` | `DiPlaySessionService.kt:29-38` |
| `startForegroundService` | `CarPlayHostActivity.kt:4255` |
| `PackageManager.canRequestPackageInstalls` | `DiPlayUpdater.kt:195,255` |
| `UsbDeviceConnection.requestWait`, `UsbRequest.queue` | `transport/UsbApiCompat.kt:20,75`, `transport/UsbRequestCompat.kt:38` |
| `AudioFocusRequest`, `AudioManager.request/abandonAudioFocusRequest` | `media/AndroidMediaSink.kt:146-167`, `CarPlayMediaKeys.kt:173,204-214,302` |
| `LocalOnlyHotspot` (`LocalOnlyHotspotManager`) | `orchestration/CarPlayController.kt:2633-2639` |

### API 27

| Call | Files |
|---|---|
| `WifiP2pManager.Channel#close` | `DiPlayActivity.kt:4231` |

### API 29

| Call | Files |
|---|---|
| `Service.startForeground(id, notification, type)` | `DiPlaySessionService.kt:66` |
| `MediaCodecInfo.isHardwareAccelerated` | `CarPlayHostActivity.kt:3556` |
| `MediaCodecInfo.isSoftwareOnly` | `media/AndroidMediaSink.kt:1040` |

## Feature gates

- **Video player.** `CarPlayVideo.show()` picks `CarPlayVideoActivity` (Media3) when
  `realSdkInt() >= 19` and `CarPlayVideoLegacyActivity` (ExoPlayer 2.19.1) below 19. Both implement
  `CarPlayVideoPlayer`, share `CarPlayVideo`'s state and apply the same iPhone URL policy
  (`LegacyIphoneResolvingDataSource` is the ExoPlayer 2 copy of `IphoneResolvingDataSource`). The
  Media3 classes are never loaded on 4.2; ExoPlayer 2 declares minSdk 16.
- **Wi-Fi P2P group management.** `WifiP2pGroupManager` (API 28+) is only used through
  `LegacyWifiP2pGroupManager`; below 28 the legacy reflection path is used.
- **Local-only hotspot.** Below 26 `LocalOnlyHotspotManager` is not created; the manual
  hotspot/ADB path is used.
- **HEVC.** `MediaFormat.MIMETYPE_VIDEO_HEVC` is an inlined string constant, safe on 17, but a
  decoder is only selected when the device lists one; the software preference is API 29+.
- **Square picture / cluster.** `CarPlayRotation` returns `null` below 21, so the square
  canvas path is never entered on 4.2.

## Libraries linked into the minSdk 17 APK

`mobile/src/main/AndroidManifest.xml:5` force-links these with `tools:overrideLibrary`:

| Library | Version | Declared minSdk | Notes |
|---|---|---|---|
| `androidx.media3` (common, exoplayer, exoplayer-hls, ui, datasource, extractor, decoder, container, database) | 1.4.1 | 19 | Only loaded by `CarPlayVideoActivity` on API 19+ |
| `com.google.android.exoplayer` (exoplayer-core, exoplayer-hls) | 2.19.1 | 16 | 4.2/4.3 player, rendered into a plain `SurfaceView`; `exoplayer-ui` is deliberately absent so its `exo_*` resources cannot collide with Media3 |
| `androidx.core` / `core-ktx` | 1.13.1 | 19 | App runs on 17; used `WindowCompat`/`ContextCompat`/`ViewCompat` paths have their own pre-19 fallbacks. If a core method ever fails on 17, downgrade core to 1.12.0 |
| `androidx.annotation.experimental` | via core | — | annotations only |
| `androidx.appcompat` | 1.6.1 | 14 | Settings UI |
| `androidx.activity` | 1.8.2 | 14 | Activity result contracts |
| `androidx.lifecycle` | 2.6.1 | 14 | Lifecycle |
| `org.bouncycastle:bcprov-jdk18on` | 1.79 | Java 8 | Java 8 APIs are desugared |
| `org.jmdns:jmdns` | 3.6.3 | Java 6 | mDNS |
| `desugar_jdk_libs` | 2.1.5 | — | `java.time`/stream/function/Optional/Base64/Objects |

## Verification

```sh
./gradlew :common:testDebugUnitTest --tests '*LegacyApiGuardTest'

# Inventory is written even though NewApi errors make the task exit non-zero:
sh ./gradlew --no-configuration-cache :shared:lintDebug
sh ./gradlew --no-configuration-cache :common:lintDebug

sh ./gradlew --no-configuration-cache :mobile:assembleStandaloneDebug
```

The last report files are `shared/build/reports/lint-results-debug.txt` and
`common/build/reports/lint-results-debug.txt`.

## Known remaining work

- The 4.2 unit's H.264 decoder still reports `native_configure: -2` for the tuned attempt. This
  is a vendor codec/format issue, not an API-level call: `configureDecoder`
  (`media/AndroidMediaSink.kt:924`) already retries the minimal format, every decoder the
  device lists, and in-band SPS/PPS. The next step is a log of every attempt's codec name and
  error from `tryConfigure` (`AndroidMediaSink.kt:1004`).
- Keep the API inventory current: after adding a call above 17, add the guard, extend the table
  above, and (if it is a hard-failure pattern) add it to `LegacyApiGuardTest`.
