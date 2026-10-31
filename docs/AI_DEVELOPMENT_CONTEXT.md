# Kinora Effect — AI Development Context

This document is the canonical handoff for an AI assistant that needs to understand, modify, build, or review Kinora Effect. It is intentionally detailed so future feature requests can be handled without rediscovering the project history.

## 1. Product identity

- **Application name:** Kinora Effect
- **Repository:** `Paplo-painite/Kinora-Effect`
- **Repository URL:** https://github.com/Paplo-painite/Kinora-Effect
- **Platform:** Android, landscape-oriented
- **Package identity:** generated Android package uses the Kinora Effect Android configuration; preserve the existing package and signing behavior unless the user explicitly requests a migration.
- **Upstream application:** EffectCraft, fetched from the upstream project during the controlled build.
- **Project owner:** Paplo-painite
- **AI collaboration credit:** Manus AI
- **Primary user-facing language in project documentation:** English

Kinora Effect is a touch-oriented Android build of a desktop-style motion graphics and visual effects editor. The product keeps the composition viewer, timeline, project browser, properties, effects, render queue, and docked workspace together instead of replacing them with a phone-only navigation model.

## 2. Important product requirements

Preserve these requirements unless the user explicitly changes them:

1. Do not redesign the existing color system, typography, panel arrangement, or desktop-style workspace.
2. Keep the application name as **Kinora Effect**.
3. Keep the current Kinora artwork and in-app branding.
4. Keep the Android activity landscape-oriented.
5. Keep normal Android touch input working.
6. Keep UI Scale behavior and the Fit/Kinora Fit workspace working.
7. New features must not silently remove existing project, timeline, composition, file, or render workflows.
8. User-facing documentation and release notes should be written in English.
9. Do not add implementation details to the ordinary end-user guide unless they are useful to users.
10. Never commit credentials, signing keys, private media, or user data.

## 3. Repository map

### Root documentation

- `README.md` — public project overview, download links, product features, preview artwork, CI badge, and user-facing navigation.
- `docs/USER_GUIDE.md` — English end-user guide. Explain how to use the app, not internal packaging details.
- `CONTRIBUTING.md` — contribution expectations.
- `SECURITY.md` — vulnerability reporting policy.
- `THIRD_PARTY_NOTICES.md` — upstream and dependency notices.
- `LICENSE` — project license information.

### Branding and visual assets

- `assets/kinora-icon.png` — launcher/application artwork used by the Android build.
- `assets/kinora-effect-repository-icon.png` — README repository identity image.
- `assets/screenshots/kinora-effect-preview.png` — README project preview.
- Matching `.attribution` files document user-supplied artwork sources. Do not change or relicense those assets without the owner’s instruction.

### Android builder

- `android-builder/build.sh` — release build entry point.
- `android-builder/build-debug.sh` — debug build entry point.
- `android-builder/clean.sh` — builder cleanup utility.
- `android-builder/lib/build-common.sh` — central builder logic. It fetches the pinned upstream source, applies Kinora patches in order, generates the Android project, embeds assets, and packages/signs the APK.
- `android-builder/templates/android/SafActivity.java` — generated Android Activity template for SAF file operations, immersive mode, file staging, and update checking.
- `android-builder/templates/android/UpdateCheck.java` — release update checker used by the Android Activity.
- `android-builder/patches/` — persistent patches applied to the pinned EffectCraft source.
- `android-builder/output/` — local build outputs; these are not the canonical public release location.
- `android-builder/releases/` — selected release artifacts and verification files kept with the repository.

### Patches and application order

The build applies the Kinora patches sequentially. Patch order matters because some patches touch related UI files:

1. `kinora-new-comp-from-footage.patch`
2. `kinora-desktop-fixed.patch`
3. `kinora-workspace-fit.patch`
4. `kinora-touch-pointer.patch`
5. `kinora-branding.patch`
6. `kinora-name.patch`

If a patch fails, do not force-apply it blindly. Inspect the upstream source revision and the preceding patch state. A patch conflict may mean that the patch needs to be regenerated against the pinned source, not that the source should be overwritten.

## 4. Current TP virtual pointer implementation

The working TP implementation is the pointer implementation recovered from the reference APK and the historical project commit `0b2a784`. The persistent source of this feature is:

`android-builder/patches/kinora-touch-pointer.patch`

The patch adds or updates the following upstream files:

- `crates/ui-egui/src/header.rs` — TP control in the top header, TP menu, and cursor selection.
- `crates/ui-egui/src/state.rs` — serializable `VirtualPointerState` storage.
- `crates/ui-egui/src/lib.rs` — pointer processing and drawing hooks.
- `crates/ui-egui/src/virtual_pointer.rs` — pointer state, input conversion, rendering, settings, L/R controls, and magnifier.

There is intentionally no Android Java overlay in the current working implementation. The working implementation is rendered and processed in the egui foreground layer, which keeps drawing and click coordinates in the same logical coordinate system.

### TP behavior

- TP is enabled from the `TP` control in the application header.
- The pointer default is a white circle with an outline.
- One-finger movement is converted into virtual pointer movement.
- A one-finger tap produces a primary click.
- A long press can begin a primary drag.
- The floating `L` and `R` controls queue primary and secondary clicks.
- The pointer is clamped to the content rectangle when `keep_in_bounds` is enabled.
- Drawing uses `ctx.layer_painter(LayerId::new(Order::Foreground, ...))` rather than a transformed panel-local Area.
- The same logical `center` is used for pointer drawing, click events, magnifier reticle, and debug coordinates.
- `ctx.request_repaint_after(Duration::from_millis(16))` keeps the pointer responsive while enabled.
- The magnifier is a visual loupe/reticle around the pointer. It does not change the click location.
- `debug_coordinates` can display the current logical x/y position for diagnosing scale or offset problems.
- `magnifier_on_move_only` limits the loupe to recent movement.

### TP state fields

`VirtualPointerState` contains persistent user settings such as:

- `enabled`
- `pos`
- `sensitivity`
- `opacity`
- `size`
- `color`
- `outline`
- `acceleration`
- `magnifier`
- `magnifier_on_move_only`
- `magnification`
- `haptic`
- `auto_hide_seconds`
- `left_handed`
- `keep_in_bounds`
- `drag_lock`
- `cursor`
- `debug_coordinates`

Transient fields such as active touch state, drag state, click flash timing, and pending button state are skipped during serialization.

### TP coordinate rules

These rules are critical:

1. Treat `RawInput.screen_rect` and `ctx.input(|i| i.content_rect())` as the logical coordinate space.
2. Do not mix Android physical pixels with egui logical points.
3. Do not put the pointer inside a panel or any container that applies zoom, transform, or UI Scale.
4. Draw with a foreground `LayerId` and calculate the center once per frame.
5. Use the same clamped center for visual rendering and generated `PointerMoved`/`PointerButton` events.
6. If a UI scale change affects the pointer, inspect coordinate conversion before changing the pointer size or sensitivity.
7. Keep the pointer away from the touch-control zones used by the L/R buttons.

### TP safety rules for future changes

- Do not replace this implementation with an Android system mouse cursor; Android does not guarantee a visible system cursor without a physical pointing device.
- Do not reintroduce the old blue Java overlay unless the user explicitly asks for a separate overlay experiment.
- Do not duplicate click delivery through both egui synthetic events and Android MotionEvent dispatch. Duplicate delivery causes missed or double clicks.
- Do not change the default pointer color away from white unless requested.
- Keep L/R buttons as normal egui controls separate from the non-interactive pointer drawing layer.
- When changing pointer drawing, test the four corners, center, UI Scale changes, TP off/on, L click, R click, tap, drag, magnifier, and auto-hide.

## 5. Build and verification workflow

From the repository root:

```bash
./build.sh
```

For a debug build:

```bash
./build-debug.sh
```

The release APK is normally written to:

`android-builder/output/kinora-effect-0.16.0.apk`

A successful build is not enough by itself. Verify:

```bash
sha256sum android-builder/output/kinora-effect-0.16.0.apk
```

Also inspect the build log and confirm the Android bridge verification completed successfully. For TP changes, inspect the generated source under:

`android-builder/source/effectcraft/crates/ui-egui/src/virtual_pointer.rs`

Confirm that the generated source contains the intended implementation, not only the patch file.

## 6. Testing checklist for any TP change

Test on a physical Android device whenever possible:

1. Install the APK and open the app in landscape.
2. Open TP from the header.
3. Confirm the white pointer is visible near the content center.
4. Move it to the center and all four corners.
5. Tap a small toolbar control with the pointer.
6. Press `L` and confirm one primary click at the visible pointer location.
7. Press `R` and confirm one secondary click at the visible pointer location.
8. Test a long press and drag.
9. Change UI Scale to low, default, and high values, then repeat click tests.
10. Enable the magnifier and confirm that it follows the pointer without moving the click point.
11. Enable Debug coordinates and compare the displayed coordinates with the visible center.
12. Turn TP off and confirm the normal touch workflow returns.
13. Relaunch the app and confirm saved settings remain sensible.

## 7. How a user should ask an AI to add a feature

A useful request should include:

- The exact feature and user problem.
- Whether the change is Android-only or affects all platforms.
- Which existing UI area should contain the control.
- Whether current colors, fonts, panels, and layout must remain unchanged.
- Expected touch, mouse, keyboard, and accessibility behavior.
- Persistence requirements across app launches.
- Whether the feature should be included in the end-user guide and release notes.
- The device model and Android version for device-specific issues.
- A clear acceptance checklist.

Recommended request template:

```text
Project: Kinora Effect
Target: Android / desktop / both
Feature: <short feature name>
User problem: <what is difficult today>
UI location: <header, panel, timeline, settings, etc.>
Keep unchanged: existing colors, fonts, panel layout, branding
Behavior: <precise tap, drag, keyboard, mouse, and state behavior>
Persistence: <what must survive relaunch>
Compatibility: <Android versions/devices or UI Scale cases>
Acceptance tests: <numbered tests>
Documentation: update README, User Guide, or release notes as appropriate
Constraint: do not modify unrelated features
```

## 8. AI change protocol

When asked to modify Kinora Effect, an AI should follow this sequence:

1. Read this file, `README.md`, the relevant source file, and the relevant patch.
2. Inspect the current Git status and recent commits.
3. Identify whether the requested change belongs in the upstream patch, Android template, builder, documentation, or release metadata.
4. Make the smallest isolated change possible.
5. Keep generated source changes reproducible from the tracked template or patch.
6. Run syntax and diff checks before building.
7. Build the APK and inspect the generated source, not only the input patch.
8. Run the relevant functional checklist.
9. Calculate and report the APK SHA-256.
10. Update documentation and release notes only when the user-facing behavior changed.
11. Never claim physical-device validation unless it was actually performed.

## 9. Release protocol

Public APKs should be uploaded as GitHub Release assets, not committed repeatedly into normal source history. Every APK release should have:

- A semantic version tag.
- A concise English release description focused on user-visible features.
- The APK asset.
- A matching `.sha256` asset.
- A link to the User Guide when relevant.
- A note if the APK is a local/test-signed build.

Do not create or publish a new release unless the user has requested release publication. A build artifact can be delivered privately for testing first.

## 10. What not to change accidentally

- Do not remove the Kinora branding or repository artwork.
- Do not change the public app name.
- Do not re-add the removed blue Java pointer overlay.
- Do not add Arabic text to public documentation unless the repository owner explicitly changes the language policy.
- Do not hide a failing build by deleting the workflow or badge.
- Do not commit `android-builder/keys/kinora-effect.jks` or other signing credentials.
- Do not overwrite the user’s supplied APK or artwork.
