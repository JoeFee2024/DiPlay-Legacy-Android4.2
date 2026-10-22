# Build DiPlay

Use this procedure to build the DiPlay app for an Android head unit.
Run the commands from the repository root, where `gradlew` and `settings.gradle.kts` are located.

## Select the application

This repository contains several Android applications.
Select `mobile` to build the main DiPlay app.

| Gradle module | Purpose | Debug APK |
| --- | --- | --- |
| `:mobile` | Main DiPlay app for Android head units | `mobile/build/outputs/apk/debug/mobile-debug.apk` |
| `:maphost` | Sample application that displays a map from DiPlay | `samples/maphost/build/outputs/apk/debug/maphost-debug.apk` |
| `:home` | Optional DiPlay Home launcher | `samples/home/build/outputs/apk/debug/home-debug.apk` |
| `:automotive` | Separate Android Automotive application inherited from xcertplay | `automotive/build/outputs/apk/debug/automotive-debug.apk` |

The `:common` and `:shared` modules are libraries.
The applications use these libraries.

If the installed app is **DiPlay map host**, you selected `:maphost` or its APK.
Build `:mobile` and select `mobile-debug.apk` to install the main app.
A command such as `./gradlew assembleDebug` can build more than one application.
Use the module name in each build command to select the required application.

## Prepare the build environment

Install these tools:

- JDK 25.
- Android SDK Platform 37 (`platforms;android-37.0`).
- Android SDK Build-Tools 36.0.0.
- Android SDK Platform-Tools.
- Android NDK 25.2.9519653 (r25c), required for Android 4.4 / API 19 native compatibility.

Use the Gradle wrapper included in this repository.
The first build needs an internet connection to download dependencies.

1. Open the repository root in Android Studio.
2. Install the Android SDK packages with **Tools → SDK Manager**.
3. Set **Gradle JDK** to JDK 25 in the Android Studio Gradle settings.
4. Wait for Gradle sync to finish.

For a terminal build, set `JAVA_HOME` to your JDK 25 directory.
Set `ANDROID_HOME` to your Android SDK directory.
You can also specify the SDK directory in the root `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Keep `local.properties` outside Git.
Check the Java version before the build:

```sh
java -version
```

The output must identify Java 25.
On Windows, use `gradlew.bat` in place of `./gradlew` in the commands below. The shared native build explicitly passes `HOST_OS=windows` to NDK r25c, so Git for Windows `uname` output cannot break host detection.

## Prepare the native and test dependencies

Two build inputs are fetched outside Gradle and are not obvious from a failure message.

- `scripts/prepare-libopus.py` restores the pinned libopus source the microphone encoder compiles.
  Run it once with `python3 scripts/prepare-libopus.py` before a native build. Without it, the
  shared native build stops at `libopus sources missing: run scripts/prepare-libopus.py`.
  The checkout is gitignored and reused afterwards.
- Robolectric downloads its `android-all-instrumented` jars, 100-200 MB per SDK level, at test
  time and caches them in `~/.m2`. This is separate from Gradle dependency resolution. On a slow
  network, point it at a mirror for every `Test` task:

  ```groovy
  // ~/.gradle/init.d/<name>.gradle
  allprojects {
      tasks.withType(Test).configureEach {
          systemProperty 'robolectric.dependency.repo.url', 'https://maven.aliyun.com/repository/public'
          systemProperty 'robolectric.dependency.repo.id', 'aliyun-public'
      }
  }
  ```

  Keep that in a machine-local init script rather than the repository, so CI and other
  contributors keep the default source.

JDK 25, the SDK packages and the NDK are downloaded automatically on the first build. If the
Gradle configuration cache cannot initialise its keystore on your machine, add
`--no-configuration-cache` to every command; nothing in this repository depends on it.

## Build the main app

For a source build without runtime authentication assets:

```sh
./gradlew :mobile:assembleDebug
```

Check that this file exists:

```text
mobile/build/outputs/apk/debug/mobile-debug.apk
```

The debug application ID is `com.shihab.diplay.hudtest`.
The release application ID is `com.shihab.diplay`.

The source APK contains no accessory identity unless you supply runtime authentication assets.
Standalone CarPlay needs these assets to connect to an iPhone.
Use the car-test procedure below for that purpose.
Tests generate synthetic identities at runtime.
Keep test private keys outside Git.

### Use Android Studio

1. Open **Run → Edit Configurations**.
2. Select an **Android App** configuration, or create one.
3. Set **Module** to `mobile`.
4. Select this configuration in the toolbar.
5. Select the Android device.
6. Click **Run** to install the main debug app.

To build an APK without installation, run `:mobile:assembleDebug` in the Android Studio terminal.
Select the APK from the `mobile` output directory.

## Check the source build

Run the unit tests, lint checks, and main debug build:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

Check that Gradle reports `BUILD SUCCESSFUL`.
For the complete CI procedure, see [the Android workflow](../.github/workflows/android.yml).
That workflow also checks and builds the `home` and `maphost` applications.

## Build a standalone car-test APK

Prepare an external directory with these runtime authentication files:

```text
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
```

Use only the intended runtime files in this directory.
Keep the directory and its private key outside Git.
The build rejects unexpected credential containers in APK assets.

1. Set `DIPLAY_AUTH_ASSETS_DIR` to the absolute directory path.
2. Run the standalone debug task.

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

The task stops if either required file is absent, empty or invalid. It loads the
key and certificate, checks P-256 key matching, and verifies repeated challenge
signatures and rejection of changed challenges before packaging. This checks local
identity consistency, not iPhone trust or a successful CarPlay session.
Its APK output is `mobile/build/outputs/apk/debug/mobile-debug.apk`.
Check both files in the APK against your selected local inputs:

- `assets/offline-mfi/identity.pk8`.
- `assets/offline-mfi/certificate.p7b`.

Use this APK for a standalone connection test.
Update the existing test app with the same signing key to keep its settings.
Different signing keys cannot update the same installed application.

## Build a release APK

Prepare the runtime assets as described in the car-test procedure.
Set these environment variables locally:

| Variable | Value |
| --- | --- |
| `DIPLAY_AUTH_ASSETS_DIR` | Absolute path to the runtime assets directory |
| `ANDROID_KEYSTORE_PATH` | Path to your Android signing keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Keep the keystore and passwords outside Git.
Run the release checks and build:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleStandaloneRelease
```

The output is `mobile/build/outputs/apk/release/mobile-release.apk`.
The APK contains the runtime identity you supplied.
Recipients can extract that identity from the APK.
The APK excludes the Android signing key.
See [the project notices](THIRD_PARTY_NOTICES.md) for the experimental identity used in public releases.

### Test the signed release on legacy Android

Build instrumentation for the production application instead of the debug app:

```sh
./gradlew :mobile:assembleStandaloneRelease :mobile:assembleReleaseAndroidTest -PlegacyInstrumentationBuildType=release
adb install -r mobile/build/outputs/apk/release/mobile-release.apk
adb install -r mobile/build/outputs/apk/androidTest/release/mobile-release-androidTest.apk
adb shell am instrument -w -r com.shihab.diplay.test/com.shilapi.xcertplay.T3LegacyInstrumentation
```

With multiple connected devices, add `-s SERIAL` to each adb command. Check both
`failures: 0` and `INSTRUMENTATION_CODE: -1`; adb's exit code alone is insufficient.
The default test build type remains debug when the property is omitted.
API 19 cold-start classes are explicitly retained in `mobile/multidex-config.pro`.
Run fresh and same-key upgrade installations as well as instrumentation; a
successful debug launch does not establish that the release main DEX is correct.
These emulator checks do not establish a physical CarPlay session.

The public source archive matches the release tag.
It excludes runtime identities, signing keys, local configuration, and build output.
The retired `build-beta.py` helper is no longer part of the build procedure.

## Correct build problems

| Symptom | Action |
| --- | --- |
| Installed app is DiPlay map host | Select `mobile` in Android Studio. Build and install the APK from the `mobile` output directory. |
| Gradle cannot find the Android SDK | Set `ANDROID_HOME` or the `sdk.dir` value in `local.properties`. |
| A required SDK or NDK package is absent | Install the listed package with SDK Manager. |
| Java or Gradle JDK version is incorrect | Set the terminal JDK and Android Studio Gradle JDK to Java 25. |
| Source APK cannot start standalone CarPlay | Supply runtime authentication assets. Use `:mobile:assembleStandaloneDebug`. |
| Standalone task reports missing authentication files | Check the external directory path and both required files. |
| Android rejects an app update | Use the same application ID and signing key as the installed app. |
| Native build stops at `libopus sources missing` | Run `python3 scripts/prepare-libopus.py`, then build again. |
| Unit tests stall while downloading | Robolectric is fetching its android SDK jars. See [Prepare the native and test dependencies](#prepare-the-native-and-test-dependencies). |
| `Could not open Gradle Configuration Cache keystore` | Add `--no-configuration-cache`. This is a local Gradle cache problem, not a source problem. |

## Known failing checks

The `:shared:testDebugUnitTest` and `:common:testDebugUnitTest` tasks do not currently pass, on
`main` as well as on feature branches. A failing run that matches the list below is not a
regression.

| Task | Failing cases |
| --- | --- |
| `:shared` | `AirPlayCryptoChachaTest.platformAndBouncyCastleOpenEachOther`, `.aChangedByteOrAadFailsToOpen`, `.concurrentCallsKeepKeysAadAndBuffersOnTheirOwnThread` |
| `:common` | `AdaptiveSettingsUiTest.selectingTheBottomRailDestinationKeepsItVisibleAndFocusedOnAShortScreen`, `BydVehicleDataSettingsTest.samePageRenderKeepsScrollAndPageChangeStartsAtTop`, `.rendersBeforeTheNextLayoutKeepTheScroll`, `DiagnosticExportFallbackTest.selectedWritableUsbVolumeReceivesTheSameUtf8Report`, `DiagnosticExportUiTest.missingPickerSavesAReportAndProvidesSelectableTextInsideDiPlay` |

The three `AirPlayCryptoChachaTest` cases fail in a plain JVM test because `Build.VERSION.SDK_INT`
is `0` there, so `AirPlayCrypto.platformChacha` returns null whenever the input carries AAD
instead of sealing with the platform ChaCha20 implementation. Those cases need a platform level
of 19 or above, which means a Robolectric runner at that SDK.

`scripts/check_public_tree.py` also fails, on `common/src/main/res/raw/update_ca_bundle.pem`.
That file is the public certificate bundle used to verify update checks, added by `639af90`; the
script bans every `.pem` in the tracked tree, so it does not distinguish a public bundle from a
private key container. No private key material is tracked.
