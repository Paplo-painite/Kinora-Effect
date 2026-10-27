# Kinora Effect for Android

This repository builds the open-source **EffectCraft** application for Android under the Kinora brand. The Android app is displayed as **Kinora Effect**, uses package ID `com.kinora.effect`, and includes a custom launcher icon. This repository fetches the official EffectCraft source during the build and packages it with a customized Android builder.

## Build

```bash
./build.sh       # Locally signed release APK
./build-debug.sh # Debug APK
```

APK files are written to `android-builder/output/`, with names such as `kinora-effect-<version>.apk`. Building requires Linux x86_64, Rust 1.95 or newer, Java 17–23, and an internet connection. The builder downloads the Android SDK and JDK when they are not already available. Pass `-appicon=/path/to/icon.png` to use a different launcher icon.

The Kinora artwork is used both for the Android launcher icon and for the EffectCraft application mark shown inside the UI and About dialog. The editor's tool icons are intentionally unchanged.

The builder stores its signing key locally at `android-builder/keys/kinora-effect.jks`. Keep this key to install future APKs signed with the same key, and do not commit it to GitHub. This local key is not an official Google Play release key.

## Android display mode

On Android, Kinora Effect keeps the desktop workspace layout: the desktop menu bar, toolbar, docked panels, and saved workspace arrangement. The Android activity opens in landscape orientation. It does not switch to a phone-only tab bar or rewrite the saved desktop dock.

Adjust the app's interface size at **Settings > Appearance > UI Scale**. The setting is available from 50% to 200%, is saved as an app preference, and changes the interface scale without changing the phone's system DPI. Lower values fit more of the desktop workspace on the phone; higher values make controls larger.

The desktop toolbar also provides **−** and **+** controls to decrease or increase UI Scale in 10% steps. **Fit** chooses a scale based on the available screen width and switches to the **Kinora Fit** workspace, which keeps the desktop panels while reserving more center width for Composition. Select another workspace, such as **Default**, to return to its layout.

## Scope and status

The application source is the [official EffectCraft repository](https://github.com/storytold/effectcraft), which is open source and still in alpha. Kinora applies Android-specific compatibility fixes and preserves the desktop workspace on Android; it does not add Adobe After Effects features or claim to fix every upstream issue. A successful build does not verify every feature on every device, so test the APK on a physical Android device.

The app checks for new versions on the [Kinora Effect Releases](https://github.com/Paplo-painite/Kinora-Effect/releases) page. Updates will not be offered until releases with `vX.Y.Z` tags and compatible APK files are published there.

## Licensing and sources

Kinora-specific source and documentation files are licensed under MIT. The launcher image at `assets/kinora-icon.png` was supplied by the repository owner; its license was not separately specified and it is excluded from the MIT grant. The `android-builder/` directory contains code from [artcraft-android](https://github.com/MostafaXS1/artcraft-android), with its original MIT notice retained. EffectCraft is fetched from its repository during builds and is licensed under MIT OR Apache-2.0. Dependencies retain their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included license files.
