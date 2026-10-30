# Kinora Effect User Guide

This guide explains the normal user workflow in **Kinora Effect** for Android. It focuses on using the application and does not describe internal packaging or conversion processes.

## Install and launch

1. Download the latest APK from the [official Releases page](https://github.com/Paplo-painite/Kinora-Effect/releases/latest).
2. Install the app named **Kinora Effect**.
3. Launch it and wait for the workspace to finish loading.
4. Use landscape orientation for the most comfortable desktop-style layout.

If Android blocks installation, temporarily enable installation from the selected source in system settings. Only use APKs published on the official project release page.

## Workspace overview

Kinora Effect keeps a desktop-style creative workspace on Android:

- **Header:** project tools, appearance, UI Scale, and workspace controls.
- **Composition:** preview and inspect the visual result.
- **Timeline:** arrange layers, durations, and animation over time.
- **Project:** view project assets and items.
- **Properties:** edit the selected item’s basic properties.
- **Effect Controls:** adjust effects applied to the selected item.
- **Render Queue:** prepare output and rendering jobs.

The exact panel arrangement depends on the selected workspace, but the main editing workflow remains the same.

## Create or open a project

Use the application menu to create a new project or open an existing one. Set the project dimensions, frame rate, and duration before adding content. Save early and keep numbered copies of important projects so earlier work can be restored.

When opening a project, use Android’s system file picker and wait for all project data to load. If media is missing, verify that the original files remain available and relink them when the application provides that option.

## Composition workflow

Select items in the **Composition** panel and use the available transform tools to move, resize, or rotate them. Use the panel’s zoom controls to inspect detail. UI Scale changes the size of the application controls; Composition zoom changes only the preview area.

## Timeline workflow

Move the playhead to review different moments. Drag item edges to change duration, select a layer before editing its properties, and use keyframes when a property must change over time. If a small handle is difficult to touch, temporarily increase UI Scale or give the panel more space.

## Properties and effects

Select an item, edit its basic values in **Properties**, and adjust applied effects in **Effect Controls**. Change values gradually while watching Composition, and save after important groups of edits. If an option is missing, confirm that the correct item and panel are selected.

## UI Scale and workspaces

Open **Settings > Appearance > UI Scale** to choose a value from **50% to 200%**. The header’s **+** and **−** controls adjust the value in 10% steps. **Fit** chooses a screen-friendly value and switches to **Kinora Fit**, while **Default** and the other workspaces restore alternative layouts. The setting is saved with the application preferences.

## Save and render

Save before rendering, then open **Render Queue**, add an output job, choose the available quality, size, format, duration, and destination settings, and start the render. Large projects may take time and need additional storage. Keep the device powered and avoid closing the application while a render is running.

## Performance tips

- Close other heavy applications before working on a large project.
- Use a lower preview quality while editing complex compositions.
- Keep several saved project copies.
- Remove unused large assets from projects.
- Leave sufficient storage space for output and temporary files.
- Restart Kinora Effect if a long session becomes sluggish.

## Troubleshooting

### The app does not open

Restart the device and try the latest APK from the official Releases page. When reporting the problem, include the device model, Android version, Kinora Effect version, and any error message.

### Controls are too small

Increase **UI Scale** under **Settings > Appearance**, or use **Fit** in the header.

### Touch selection is inaccurate

Use landscape orientation, increase UI Scale, tap within the item bounds, and confirm that the appropriate tool is active.

### A project is slow

Lower preview quality, close other applications, save, and restart Kinora Effect.

### A file cannot be opened or saved

Check file access, available storage, and try another folder in Android’s file picker.

### An update will not install

Back up projects, remove the older test build if necessary, and install the latest release from GitHub.

## Report a problem

Before opening an issue, confirm that the problem still occurs on the latest release. Include reproducible steps, device model, Android version, application version, screenshots, and error text when available. Use the project’s [Issues page](https://github.com/Paplo-painite/Kinora-Effect/issues).

## Current input model

The TP virtual mouse, blue overlay, floating click controls, and loupe have been removed from the current release. Kinora Effect uses normal touch input together with the adjustable workspace scale.
