# Kinora Effect for Android

This repository builds the open-source **EffectCraft** application for Android under the Kinora brand. The Android app is displayed as **Kinora Effect**, uses package ID `com.kinora.effect`, and includes a custom launcher icon. This repository does not rewrite the desktop application; it fetches the official EffectCraft source during the build and packages it with a customized Android builder.

## Build

```bash
./build.sh       # Locally signed release APK
./build-debug.sh # Debug APK
```

APK files are written to `android-builder/output/`, with names such as `kinora-effect-<version>.apk`. Building requires Linux x86_64, Rust 1.95 or newer, Java 17–23, and an internet connection. The builder downloads the Android SDK and JDK when they are not already available. Pass `-appicon=/path/to/icon.png` to use a different launcher icon.

The builder stores its signing key locally at `android-builder/keys/kinora-effect.jks`. Keep this key to install future APKs signed with the same key, and do not commit it to GitHub. This local key is not an official Google Play release key.

## Scope and status

The application source is the [official EffectCraft repository](https://github.com/storytold/effectcraft), which is open source and still in alpha. Changes here cover the Android package, branding, build flow, and update checks. They do not claim to fix every upstream issue or rewrite the application's functionality. A successful build does not verify every feature on every device, so test the APK on a physical Android device.

The app checks for new versions on the [Kinora Effect Releases](https://github.com/Paplo-painite/Kinora-Effect/releases) page. Updates will not be offered until releases with `vX.Y.Z` tags and compatible APK files are published there.

## Licensing and sources

Kinora-specific source and documentation files are licensed under MIT. The launcher image at `assets/kinora-icon.png` was supplied by the repository owner; its license was not separately specified and it is excluded from the MIT grant. The `android-builder/` directory contains code from [artcraft-android](https://github.com/MostafaXS1/artcraft-android), with its original MIT notice retained. EffectCraft is fetched from its repository during builds and is licensed under MIT OR Apache-2.0. Dependencies retain their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included license files.
