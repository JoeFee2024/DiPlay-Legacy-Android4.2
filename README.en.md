# DiPlay-Legacy-Android4.2

[简体中文](README.md) · [English](README.en.md)

DiPlay-Legacy-Android4.2 is an independent CarPlay receiver for Android head units. It runs on
the car, not the iPhone: the core experience needs no jailbreak, dongle, Mac, account or
authentication server. Wired USB and wireless CarPlay are supported through a DiAuto-style
interface.

This repository is a deep modification of
[programmerguohuajing/DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android),
which is itself based on [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) and
[xcertplay](https://github.com/shilapi/xcertplay).

New repository:
[JoeFee2024/DiPlay-Legacy-Android4.2](https://github.com/JoeFee2024/DiPlay-Legacy-Android4.2)

> **Current release: V1.0**
>
> V1.0 is built from the `0.2.64` source and has passed a physical Android 4.2 head-unit test,
> including the ExoPlayer 2 video-in-car path used on Android 4.2/4.3. Other head units, firmware
> and iOS versions still vary.
>
> This APK is a `standalone debug` build: the in-app version is `0.2.64-hud-test`, the package is
> `com.shihab.diplay.hudtest`, and it is signed with the debug certificate. The hash file checks
> download integrity only; it is not a release signature.

## Head-unit test screenshots

These show the car-side UI and the test environment:

| DiPlay connection home | Head-unit desktop |
| :---: | :---: |
| <img src="site/assets/home.png" alt="DiPlay connection home" width="480"> | <img src="site/assets/android42-headunit.jpg" alt="Head-unit desktop test environment" width="480"> |

## Download and verify

| Item | Value |
| --- | --- |
| Release file | `DiPlay-Legacy-Android4.2-V1.0.apk` |
| SHA-256 | `fa3172e88687b7b588c476058352acb1f3f56385523a4f10b539b3b0aaceb2ee` |
| Hash file | [DiPlay-Legacy-Android4.2-V1.0.apk.sha256](DiPlay-Legacy-Android4.2-V1.0.apk.sha256) |
| Downloads | [GitHub Releases](https://github.com/JoeFee2024/DiPlay-Legacy-Android4.2/releases) |

Put the APK and its `.sha256` file in the same directory, then run one of:

```sh
# macOS / Linux
shasum -a 256 DiPlay-Legacy-Android4.2-V1.0.apk
sha256sum DiPlay-Legacy-Android4.2-V1.0.apk

# Windows
certutil -hashfile DiPlay-Legacy-Android4.2-V1.0.apk SHA256
```

The result must match the `.sha256` file exactly. **Do not install the APK if it does not match.**
The hash verifies the download; the APK signing certificate is a separate check. V1.0 uses a debug
certificate, not the production release certificate.

## At a glance

| Area | This build |
| --- | --- |
| Minimum Android | 4.2 Jelly Bean (API 17) |
| CPU architectures | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |
| Legacy USB | Raw descriptors and `usbfs`, shared USBMUX/NCM device descriptor |
| Legacy Bluetooth | AOSP RFCOMM plus HSAE/Hybrid adapter probing |
| Legacy Wi-Fi | KitKat interface discovery, Wi-Fi Direct, car hotspot, Same LAN |
| Legacy runtime | Dalvik, multidex, core-library desugaring, API guards |
| Installer compatibility | Release APK supports v1 + v2 signatures (V1.0 is a debug build) |
| Video in car | ExoPlayer 2.19.1 on API 17/18; Media3 on API 19+ |
| Source version | `0.2.64`, versionCode `84` |

## Features

- **Wired CarPlay:** USB data port, USB host/NCM transport, local VPN link and touch forwarding.
- **Wireless CarPlay:** built-in car hotspot, Wi-Fi Direct and Existing Wi-Fi / Same LAN.
- **Offline authentication:** bundled experimental accessory identity; no external auth server.
- **BYD integration:** HUD turn guidance, cluster map, car hotspot automation and optional call
  keys on verified firmware.
- **Display and audio controls:** resolution, frame rate, icon/text size, day/night appearance,
  picture adjustments, dock, system bars, interface size, audio routing and music buffering.
- **Vehicle controls:** wheel keys, Siri key, BYD joystick, map zoom, car button and CarPlay
  gestures.
- **Diagnostics:** local diagnostic reports and an in-app updater for older Android releases.
- **Languages:** English, Arabic, Spanish, Russian, Ukrainian, Simplified Chinese and Traditional
  Chinese. Android 13+ per-app language selection is supported.

Some features are experimental and off by default. Experimental vehicle-data or ADB-backed
features require a compatible firmware and explicit opt-in.

## What was adapted for Android 4.2

This repository builds on
[programmerguohuajing/DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android)
and takes Android 4.2/4.3 beyond "it installs and starts" to a working session, display and
video-in-car path on a physical head unit. This is not only a lower `minSdk`; the work covers the
build toolchain, Dalvik class linking, missing framework APIs, the old USB/Bluetooth/Wi-Fi stacks,
video decoding and the legacy package installer.

### 1. Build and runtime baseline

- All three modules use `minSdk 17` with `compileSdk 37` / `targetSdk 37`.
- Core-library desugaring and multidex bring `java.time`, streams, Optional and other Java 8 APIs
  to Dalvik, while explicit fallbacks cover framework/JDK calls that desugaring does not.
- `realSdkInt()` is used for runtime framework decisions. Some Allwinner T3 and modified ROMs report
  API 27/28 while running a KitKat framework; trusting `Build.VERSION.SDK_INT` selects a newer
  branch and throws `NoSuchMethodError`.
- `LegacyApiGuardTest` scans the three `minSdk 17` main source sets and rejects known crash
  patterns.

### 2. Dalvik class linking and missing APIs

Dalvik resolves catch types, superclasses and interfaces when a class links. One type missing on
API 17 rejects the whole class with `VerifyError`. The main fixes are:

| Problem on Android 4.2 | Current handling |
| --- | --- |
| `catch (ReflectiveOperationException)` broke classes such as `CarPlayController` | catch `Exception`; narrow with type checks where needed |
| `AutoCloseable` interfaces / implicit `use` casts | use `java.io.Closeable` (API 1) and release locks in `finally` |
| `ThreadLocal.withInitial`, `removeIf`, `computeIfAbsent` lambdas requiring `java.util.function` | anonymous `ThreadLocal`, iterator loops and `get`/`putIfAbsent` |
| `Long.toUnsignedString`, `MotionEvent.actionToString`, `Math.floorDiv`, `InetAddress.getLoopbackAddress`, `Throwable.addSuppressed` | local compatibility helpers; the guard test forbids the originals |
| Five-argument USB `bulkTransfer` (API 18) | four-argument overload plus `copyOfRange` on API 17 |
| `NetworkInterface.getIndex()` (API 19) | read `/sys/class/net/<if>/ifindex` below 19 |
| `BluetoothManager`, `BluetoothDevice.getType()`, `MediaCodec.getName()`, `AccessibilityNodeInfo.getViewIdResourceName()` | version guards or an API 17-safe path |
| Media-button `PendingIntent` overload, `AudioTrack.setVolume()`, `FrameLayout.LayoutParams` copy constructor, `FileLock.use` | `ComponentName`, `setStereoVolume()`, explicit layout params and `finally` |

### 3. Legacy USB, Bluetooth and Wi-Fi

- USB Host handles raw device descriptors and `usbfs`, including head units that share the
  USBMUX/NCM descriptor.
- Legacy transfer lengths, timeouts and retries are tolerated; USB permission and auto-confirm
  services have API 17 paths.
- Bluetooth uses AOSP RFCOMM for pairing/connections and keeps optional HSAE/Hybrid adapter
  probing.
- Wi-Fi supports KitKat interface discovery, Wi-Fi Direct, the car hotspot and Same LAN. The API
  28+ `WifiP2pGroupManager` is never loaded on 4.2; the legacy reflection path is used instead.
- VPN TUN, the AirPlay crypto handshake, PCM audio and touch forwarding have API 17 guards.

### 4. Video in car on Android 4.2/4.3

This is the main addition over the parent project. The parent already covered installation,
settings, the AirPlay crypto handshake, manual Bluetooth pairing, VPN TUN and PCM audio on 4.2,
but video did not pass. The current implementation:

- Picks `CarPlayVideoLegacyActivity` below API 19 and keeps Media3's `CarPlayVideoActivity` on
  API 19+. Both implement the same `CarPlayVideoPlayer` interface.
- Uses ExoPlayer 2.19.1 `core` + `hls` on 4.2/4.3. It declares `minSdk 16`, so it can load on API
  17 without pulling in Media3.
- Draws into a plain `SurfaceView` with a custom aspect layout. `exoplayer-ui` is deliberately
  absent: ExoPlayer 2 and Media3 both generate `exo_*` resources, and the merged APK would give
  `PlayerView` a Media3 layout it cannot cast.
- Ports the iPhone URL policy to `LegacyIphoneResolvingDataSource`, so controls, aspect handling
  and player state stay consistent with the newer player.
- Adds four H.264 compatibility steps for API 17/18: SPS/PPS inside the first access unit;
  per-device decoder enumeration with `OMX.google.*` last; a 5 ms drain before the first frame; and
  a platform check before constructing API 19's `ImageReader` so a detach cannot kill the decoder
  worker with `NoClassDefFoundError`.
- Registers the legacy activity in the manifest and adds the forward/rewind strings in all six
  locale folders.

### 5. Legacy installer and in-app updates

- When an old PackageInstaller cannot handle `content://`, updates fall back to a `file://` URI and
  add `FLAG_ACTIVITY_NEW_TASK`.
- The APK and parent directories are made world-readable before installation, with fallbacks for
  `/sdcard/Download`, `/storage/emulated/0` and other common storage paths.
- Release builds keep v1 + v2 signatures for old installers. V1.0 itself is still a standalone
  debug build.

### 6. Verification and limits

`LegacyApiGuardTest`, `:common:compileDebugKotlin` and `:mobile:assembleStandaloneDebug` pass.
V1.0 has been tested on a physical Android 4.2 head unit for connection, display and the
video-in-car path; see the screenshots above. Native drivers, H.264 decoders and vendor ROMs still
vary, especially devices that expose only non-standard OMX decoders.

Native libraries are built with NDK r25c against `APP_PLATFORM=android-19`, the oldest platform
that toolchain supports. API 17/18 framework compatibility does not make the native libraries
API 17-targeted. V1.0 is debug-signed; use a release certificate before public distribution. See
[Android 4.2 API compatibility](docs/ANDROID42_API_COMPAT.md) and
[Android 4.2 video](docs/ANDROID42_VIDEO.md).

## Install

1. Download `DiPlay-Legacy-Android4.2-V1.0.apk` and its `.sha256` file from the
   [new repository releases](https://github.com/JoeFee2024/DiPlay-Legacy-Android4.2/releases).
   Verify the hash before installing.
2. Install it **on the head unit**, not on the iPhone. V1.0 uses the package
   `com.shihab.diplay.hudtest`; it can coexist with the official `com.shihab.diplay` app, but
   settings and pairing records are separate.
3. If installation fails with a signature conflict, fully uninstall any older build with the same
   package name and a different certificate, then install this one. Keep the same signer for
   future in-place updates.
4. If Android reports "There was a problem parsing the package", the head unit may be reporting a
   spoofed Android version. Check the real API level with
   [headunit-version-inspector](https://github.com/493330227-wq/headunit-version-inspector).
5. Open DiPlay and grant the permissions used by your connection mode.

For USB, use a **data-capable** cable and the head unit's USB data port, not a charge-only port.
For wireless, configure the car hotspot, Wi-Fi Direct or Same LAN under
**Settings → Connection → Connection setup**, and keep Bluetooth enabled on the iPhone. See
[Connection setup](docs/CONNECTION_SETUP.md) and
[Existing Wi-Fi / Same LAN](docs/EXISTING_WIFI.md).

If the iPhone does not join the car hotspot automatically, check that Auto-Join is enabled for
that hotspot and see [hotspot join notes](docs/WIRELESS_HOTSPOT_JOIN.md).

## Get the source

```sh
git clone git@github.com:JoeFee2024/DiPlay-Legacy-Android4.2.git
cd DiPlay-Legacy-Android4.2
```

## Build

### Requirements

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Android SDK Platform | 37 |
| Android SDK Build-Tools | 36.0.0 |
| Android NDK | 25.2.9519653 (r25c) |
| Python | 3.x, for `scripts/prepare-libopus.py` |

Set `ANDROID_HOME` or create `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Restore the pinned Opus sources once before the first native build:

```sh
python3 scripts/prepare-libopus.py
```

### Debug build

```sh
./gradlew :mobile:assembleDebug
```

The output is `mobile/build/outputs/apk/debug/mobile-debug.apk`. A plain debug build contains no
runtime authentication identity, so it can start and be diagnosed but cannot complete a CarPlay
session with an iPhone.

### Standalone car-test build

Place the runtime authentication files in a directory outside Git:

```text
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
```

Then build:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets \
  ./gradlew :mobile:assembleStandaloneDebug
```

The task validates the key/certificate pair and challenge signatures before packaging. Treat the
private key as a secret and never commit it.

To produce the V1.0 release file and its hash:

```sh
cp mobile/build/outputs/apk/debug/mobile-debug.apk \
  DiPlay-Legacy-Android4.2-V1.0.apk
shasum -a 256 DiPlay-Legacy-Android4.2-V1.0.apk \
  > DiPlay-Legacy-Android4.2-V1.0.apk.sha256
```

### Release build

Set the runtime assets and the release signing variables:

```sh
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets
export ANDROID_KEYSTORE_PATH=/absolute/path/to/release-signing.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...

./gradlew :mobile:verifyReleaseSigningIdentity :mobile:assembleStandaloneRelease
```

The release task refuses to package an APK whose signing certificate does not match the historical
DiPlay certificate. See [release signing](docs/RELEASE_SIGNING.md).

### Checks

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
  :mobile:lintDebug :mobile:assembleDebug
```

The full project check also covers the sample applications:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug \
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

If the Gradle configuration cache cannot initialize on your machine, add
`--no-configuration-cache`.

## Project layout

| Path | Purpose |
| --- | --- |
| `shared/` | AirPlay, iAP2, USB, Bluetooth, Wi-Fi, media and native JNI code |
| `common/` | Shared Android UI, settings, connection screens, video players |
| `mobile/` | Main DiPlay application |
| `samples/home/` | Optional DiPlay Home launcher (API 30+) |
| `samples/maphost/` | Sample app that displays DiPlay's dashboard map (API 30+) |
| `docs/` | Setup, compatibility, feature and release documentation |
| `scripts/` | Build helpers, including the pinned Opus source preparation |

## Documentation

- [Build from source](docs/BUILD.md)
- [Android 4.2 API compatibility](docs/ANDROID42_API_COMPAT.md)
- [Android 4.2 video](docs/ANDROID42_VIDEO.md)
- [Connection setup](docs/CONNECTION_SETUP.md)
- [Compatibility and known limits](docs/COMPATIBILITY.md)
- [BYD navigation and HUD](docs/BYD_NAVIGATION.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [0.2.64 release notes](docs/RELEASE-NOTES-0.2.64.md)
- [Credits and third-party notices](docs/THIRD_PARTY_NOTICES.md)

Some documents preserve the validation limits of earlier releases. When a document and the
current release notes disagree, the current release notes and the tested build take precedence.

## Acknowledgements

This repository is a deep modification of the following projects. Thanks to their authors and
contributors:

- [programmerguohuajing/DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android)
  — the direct base for this repository, including Android 4.x compatibility, T3/MTK/AC8227L
  work, build tooling and diagnostics.
- [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) — the upstream DiPlay project.
- [shilapi/xcertplay](https://github.com/shilapi/xcertplay) — the independent CarPlay receiver.
- [shihabal3amri/DiAuto](https://github.com/shihabal3amri/DiAuto) — visual and interaction
  reference for the home and settings UI.
- [AndyShaman/BYDMate](https://github.com/AndyShaman/BYDMate) — BYD HUD maneuver icon assets.
- [LIVI](https://github.com/f-io/LIVI) and [Showcase](https://github.com/amineross/showcase)
  — protocol research references.
- Everyone who contributed issues, logs, test reports and pull requests.

If a contribution is missing, please open an issue so it can be added.

## License

DiPlay is based on [xcertplay](https://github.com/shilapi/xcertplay), licensed under GPL-3.0.
The home/settings UI adapts [DiAuto](https://github.com/shihabal3amri/DiAuto), licensed under
AGPL-3.0. Preserve the upstream notices when distributing modifications. See
[docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md) and [LICENSE](LICENSE).

CarPlay and the CarPlay icon are Apple Inc. marks. This project is independent and is **not**
Apple-certified. The bundled accessory identity is experimental and extractable; acceptance by
current or future iOS versions is not guaranteed.

## Community

- [Issues](https://github.com/JoeFee2024/DiPlay-Legacy-Android4.2/issues) for questions and bug
  reports.
- Attach a diagnostic report from **Settings → Diagnostics → Save diagnostic report** and include
  the head-unit model, real Android version, iPhone model and iOS version.
- Pull requests that improve legacy hardware compatibility are welcome.
