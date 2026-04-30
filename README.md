# MysteriumVPN: a decentralized VPN

Mobile VPN app for Mysterium Network.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=network.mysterium.vpn)

## Getting started (development)

1. Install Android Studio
2. `brew install --cask android-studio`
3. Download project's firebase crashlytics config - `google-services.json` from https://console.firebase.google.com/u/1/project/mysterium-vpn/overview and place it in `android/app`

## Mysterium Node dependency

The Android app depends on the Mysterium Node SDK (`network.mysterium:provider-mobile-node`). After version `1.30.3` (March 2024), Mysterium stopped publishing this artifact to Maven Central — newer versions are published only as assets on the [`mysteriumnetwork/node` GitHub releases](https://github.com/mysteriumnetwork/node/releases).

To handle that without bloating the repo with an ~85MB AAR, the build downloads the artifact on demand.

### How it works

- The version is declared in [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml) as `node = "x.y.z"`.
- A Gradle task `downloadProviderMobileNode` (defined in [`android/build.gradle.kts`](android/build.gradle.kts)) downloads `provider-mobile-node-{version}.aar` and `.pom` from the matching GitHub release into `android/libs/network/mysterium/provider-mobile-node/{version}/`.
- The task is wired as a dependency of `preBuild` for every Android module, so it runs automatically before any build.
- It's cached: re-runs are `UP-TO-DATE` and make no network calls when the files already exist.
- [`android/settings.gradle.kts`](android/settings.gradle.kts) declares `android/libs/` as a local Maven repository (`maven { url = uri("libs") }`), so the dependency resolves through standard Gradle mechanisms — no `@aar` hacks.
- `android/libs/` is gitignored — the AAR never enters git history.

### Developer onboarding

A fresh clone + `./gradlew assembleDebug` (or opening the project in Android Studio and syncing) just works. The AAR is downloaded on the first build automatically. No manual setup required.

### Bumping the node version

1. Edit [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml) and change `node = "x.y.z"` to the desired version. Available versions: https://github.com/mysteriumnetwork/node/releases
2. Sync / build the project. The new AAR + POM will be downloaded automatically.
3. Verify the app builds and launches. API changes between minor versions can require code adjustments in the `:node` module.

### Manual download (offline / debugging)

If you need to download the artifact without running a build:

```bash
cd android && ./gradlew downloadProviderMobileNode
```

The files land in `android/libs/network/mysterium/provider-mobile-node/{version}/`.

### Local development

- Build Mysterium Node from source code:
    ```bash
    util_scripts/build-node.sh
    ```

- Uncomment local dependency in `android/app/build.gradle`:
    ```bash
    //implementation 'network.mysterium:mobile-node:0.8.1'
    implementation files('libs/Mysterium.aar')
    ```


### Building release APK

- Install Fastlane (if don't have it yet)
    ```bash
    brew install fastlane
    ```
    
- Make release build:
    ```bash
    source fastlane/.env.local && fastlane android build
    ```
    
APK will be available under `android/app/build/outputs/apk/release/app-release.apk`

You can install this APK by:
- uploading it to phone, or
- using `adb install android/app/build/outputs/apk/release/app-release.apk`

### Creating releases locally

- Get `google-services.json`:
    - Go to https://console.firebase.google.com
    - Open android project
    - Download `google-services.json`
    - Put it to `android/app/google-services.json`

- Create signing key:
    ```bash
    keytool -genkey -v -keystore my-release-key.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
    ```
    More info: https://facebook.github.io/react-native/docs/signed-apk-android#generating-a-signing-key

- Setup values in environment:
```bash
cp fastlane/.env.local.dist fastlane/.env.local
vim fastlane/.env.local
```

- Setup Fastlane, more info in *fastlane/README.md*

## Releases

### Internal release

1. Create a PR with bumped fastlane/android_version_code (Google play store requires new version code for each release).
2. Ater merge to master create new tag in github repository.
See [example](https://github.com/mysteriumnetwork/mysterium-vpn-mobile/commit/6111eb183e6aa9c5b2d12ed7bdc55eb598166c5a) commit.

### Public release

Public releases are promoted and managed from the Google Play Console.

## Contributing

#### Bump mobile-node version

See the [Bumping the node version](#bumping-the-node-version) section above.
