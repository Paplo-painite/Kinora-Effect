# Third-party notices

## artcraft-android

The files under `android-builder/` are based on the MIT-licensed [MostafaXS1/artcraft-android](https://github.com/MostafaXS1/artcraft-android). Its original copyright and license notice are retained in `android-builder/LICENSE`. Kinora-specific modifications are identified in this repository's history and documentation.

## EffectCraft

Build scripts fetch [storytold/effectcraft](https://github.com/storytold/effectcraft) as the application source. The upstream project is dual-licensed under MIT OR Apache-2.0. Its source is not vendored in this repository; it is downloaded into the ignored `android-builder/source/effectcraft/` directory when building.

## Bundled builder dependencies

Licenses for vendored components under `android-builder/vendor/` remain alongside those components. The Android SDK, JDK, Rust crates, Gradle plugins, and upstream application dependencies are obtained as part of the build and remain subject to their respective licenses.
