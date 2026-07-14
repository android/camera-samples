# AGENTS.md — Android Camera Samples

Guidance for AI coding agents (and humans) working in this repository. **Read this before making
changes.** It describes the rules, architecture, design system, and shared components.

## What this is

A single, **Compose-first** Android app — the **Camera Samples Catalog** — that showcases the
**Camera2** and **CameraX** APIs through **28 small, self-contained samples**. A home catalog routes to
each sample; every sample is its own Gradle library module and reuses a shared camera / UI / theme
layer, so adding a sample means writing *the feature*, not the boilerplate.

## TL;DR — rules you must not violate

1. **Viewfinder.** CameraX previews use **`CameraXViewfinder`**; Camera2 previews use the Compose
   **`Viewfinder`**. **Never** use `PreviewView`, `ViewfinderView`, `SurfaceView`, or `TextureView`.
   In practice always render through the shared `CameraXPreview` / `Camera2Preview` composables
   (`:core-camera`) — there are **no Android Views** in sample/UI code.
2. **One feature per sample.** Each sample demonstrates exactly one capability. Don't bundle features
   unless a feature genuinely requires another (e.g. *Green Screen* = concurrent camera + ML Kit
   segmentation, *Feature Combination* = several CameraX features by design).
3. **No loose strings.** Every user-facing string lives in a module's `res/values/strings.xml` and is
   read via `stringResource(...)` / `context.getString(...)`. No string literals in `Text(...)`,
   `contentDescription`, button labels, etc.
4. **Reuse the shared layers** (`:core-camera`, `:core-ui`, `:core-theme`). Don't reinvent shutter
   buttons, control bars, scaffolds, previews, sliders, overlays, theme tokens, etc.
5. **Layered architecture** (see below): `UiState` → `ViewModel` → `Controller` → `Screen`.
   ViewModels never reference `Context`, `Activity`, lifecycle, or `Resources`.
6. **Guard optional hardware** → render `UnsupportedView` instead of crashing.
7. **Apache license header** on every file; run **`./gradlew spotlessApply`** (ktlint + headers)
   before finishing.

## Repository layout / module graph

```
:app                      home catalog + Navigation-Compose NavHost + Hilt application
:core-theme               design system (colors, typography, AISampleCatalogTheme)        — leaf
:core-camera              Camera2/CameraX plumbing (controllers, previews, utils)         — re-exports camera libs
:core-ui                  shared Compose chrome (scaffold, controls, overlays, review)    — api(:core-camera, :core-theme)
:samples:{api}-{feature}  one library module per sample                                   — depends on :core-camera + :core-ui
```

Dependency direction is **acyclic**: `app → core-theme + samples`; `samples → core-camera + core-ui`;
`core-ui → api(core-camera, core-theme)`; `core-camera` and `core-theme` are leaves.

**Naming.** Module `samples/{api}-{feature}` → package `com.android.{api}.{feature}` → composables
`{Api}{Feature}Screen` / `{Api}{Feature}ViewModel` / `{Api}{Feature}Controller` /
`{Api}{Feature}UiState`, where `{api}` ∈ `{camerax, camera2}`.

## Build, run, format

- **JDK 17** toolchain (`jvmToolchain(17)`); **compileSdk / targetSdk 37** (Android 17), **minSdk 23**.
- Build everything: `./gradlew assembleDebug` · Install: `./gradlew :app:installDebug`.
- **Format (required, CI-gated):** `./gradlew spotlessApply` (auto-fix) / `./gradlew spotlessCheck`.
  Spotless = ktlint + the Apache header from `spotless/copyright.kt`. `.editorconfig` sets
  `ktlint_function_naming_ignore_when_annotated_with = Composable` so PascalCase `@Composable` names
  pass.
- Key versions (`gradle/libs.versions.toml`): AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6, Compose BOM
  2026.06.00, Material 3 1.5.0-alpha22, androidx.camera 1.6.1 (`camera-compose`,
  `camera-viewfinder-compose`), Media3 1.10.1, Hilt 2.59.2, Navigation-Compose 2.9.8,
  kotlinx-serialization 1.11.0, ML Kit (barcode 17.3, image-labeling 17.0.9, selfie-segmentation
  16.0.0-beta6), KSP 2.3.5, Spotless 8.7.0.

## Architecture & programming paradigm

Layered, unidirectional (UDF), Compose-first — see [`android_architecture.md`](android_architecture.md).
Each sample is **four pieces**:

- **`{Feature}UiState`** — a `sealed interface` of states: `Initial`, one or more feature states
  (e.g. `Previewing`, `Capturing`, `Recording`, `Captured`), and `Error(message)`. Add `Unsupported`
  when hardware support is conditional.
- **`{Feature}ViewModel`** — `@HiltViewModel`, `@Inject constructor`. Exposes **exactly one**
  `val uiState: StateFlow<{Feature}UiState>` (a private `MutableStateFlow` surfaced immutably).
  **No** `Context`/`Activity`/lifecycle/`Resources`; **no** events pushed to the UI (process the
  event, update state). Business logic lives here.
- **`{Feature}Controller`** — a `@Stable` state-holder that owns the **camera SDK lifecycle**
  (open/close, sessions, capture/record). Created from the Screen via a `remember{Feature}Controller(...)`
  composable. Camera2 controllers extend `BaseCamera2Controller`; CameraX controllers wrap
  `ProcessCameraProvider`. The controller is the only place that holds `Context`.
- **`{Feature}Screen`** — the screen-level composable. Collects with `collectAsStateWithLifecycle()`,
  wraps content in `CameraSampleScaffold(...)`, and branches with `when (state)`.

**Conventions**

- Lifecycle-aware collection (`collectAsStateWithLifecycle`); never override Activity lifecycle methods
  — use `LifecycleEventObserver` + `DisposableEffect`.
- Every reusable composable (not the top-level Screen) takes `modifier: Modifier = Modifier` as its
  **first optional** parameter.
- Single-activity app + Navigation-Compose; the `NavHost` is derived from `sampleCatalog`.
- **Single controller call-site for long-lived state.** When several states all show the camera
  (`Previewing` / `Recording` / `Captured`), create the controller from **one** call site (e.g. a
  shared `else ->` branch), not separately per `when` branch — otherwise a state change disposes and
  recreates the controller and **kills an in-progress recording**. Overlay review screens on top of
  the live content instead of swapping the whole branch.
- DI: Hilt — `@HiltAndroidApp` Application, `@AndroidEntryPoint` MainActivity, ViewModels via
  `hiltViewModel(...)`.

## The viewfinder rule (expanded)

- **CameraX** → render the `SurfaceRequest` through **`CameraXPreview`** (`:core-camera`), which uses
  `androidx.camera.compose.CameraXViewfinder`.
- **Camera2** → render through **`Camera2Preview`** (`:core-camera`), which uses
  `androidx.camera.viewfinder.compose.Viewfinder` driven by a `BaseCamera2Controller`.
- **Never** instantiate `PreviewView`, `ViewfinderView`, `SurfaceView`, or `TextureView`. Even the
  captured-video player is Compose (Media3 `PlayerSurface`), not a `VideoView`.

## Strings & resources

- Each sample module owns its `res/values/strings.xml`. **No hardcoded user-facing text** anywhere.
- Catalog strings live in `app/src/main/res/values/strings.xml` with keys
  `{api}_{feature}_list_title` and `{api}_{feature}_list_description` (title format e.g.
  `"CameraX • Take a Photo"`). In-screen strings use the sample module's own keys.

## UI / design system — "Console"

A dark, pro/technical aesthetic (`:core-theme` + `:core-ui`).

- **Accent** violet `#B59CFF` (`primary`) on `#190F33` (`onPrimary`); dark surfaces; hairline borders.
- **Type**: `displayFontFamily` / `bodyFontFamily` = Space Grotesk; `monoFontFamily` = Space Mono for
  all technical/metadata text (API labels, filter chips, counts, HUD readouts).
- **Home catalog** (`:app` `catalog/ui`): mono header + live count, the `SampleCategory` filter pills
  (All · Images · Video · ML · Graphics · Extensions · Controls), a bordered featured hero
  (`CatalogWideCard`), and a 2-column grid of compact tiles (`CatalogRowCard`) grouped under `CAMERAX`
  / `CAMERA2` section labels. The tile API label is accent for CameraX, muted for Camera2; a category
  dot uses the first tag's color.
- **Viewfinder chrome** (`:core-ui`): scrim top-bar buttons (`ScrimIconButton`), centered title chip
  (`ViewfinderTitleChip`), `RuleOfThirdsGrid`, the accent square `FocusIndicator` reticle, a bottom
  `CameraControlsBar` with `ShutterButton` / `RecordButton`, `TorchChip` / `TorchGlow`, and
  `ZoomControls`.
- **In-app settings** use `SettingsOverlay` (+ `SettingsHeader` / `SettingsRow` / `SettingsDropdown`
  accent chips / `settingsSwitchColors`) — never raw Material dropdowns.
- **Captured-media review** via `CapturedImagePreview` / `CapturedVideoPreview`.

## Component reference (reuse these — don't duplicate)

### `:core-theme` (`com.android.camera.coretheme`)
- **`AISampleCatalogTheme(darkTheme, contrast, content)`** — app theme wrapper (Material 3 +
  `AppTypography`). `Theme.kt`
- **Color tokens** (violet `primary`/`onPrimary`, dark surfaces, …) + **`extendedColorScheme`**
  (`media3`, `mLKit`, `imagen`, `geminiProFlash`, … consumed by `SampleTags`). `Color.kt`, `Theme.kt`
- **`displayFontFamily`**, **`bodyFontFamily`** (Space Grotesk), **`monoFontFamily`** (Space Mono),
  **`AppTypography`**. `Type.kt`

### `:core-camera` (`com.android.camera.core.*`)
- **`BaseCamera2Controller`** — `@Stable` abstract base: background-thread open/close, session creation
  (`createCaptureSession`), display-rotation transform, tap-to-focus (`focus`),
  `surfaceRequest`/`transformationInfo` state, `release()`; subclasses override `onCameraOpened` /
  `configureOutput` / etc. `camera2/BaseCamera2Controller.kt`
- **`Camera2Preview(controller, modifier, onFocusTap)`** — Compose `Viewfinder` host. `camera2/Camera2Preview.kt`
- **`CameraXPreview(surfaceRequest, modifier, onTapToFocus, onFocusTap)`** — `CameraXViewfinder` host
  with coordinate transform. `camerax/CameraXPreview.kt`
- **`ImageProxy.toBitmap(mirror)`** / **`Image.toBitmap(sensorOrientation, mirror)`** — JPEG → oriented
  `Bitmap`. `image/ImageUtils.kt`
- **`MediaStoreSaver`** — `saveBitmap(...)` to DCIM/Camera, `newVideoFile()`, `scanFile(...)`. `media/MediaStoreSaver.kt`
- **`CameraPermissions`** — `PHOTO`, `VIDEO` permission lists. `permissions/CameraPermissions.kt`
- **`rememberDisplayRotation(): Int`** — current display rotation; recomposes on config change. `display/DisplayRotation.kt`

### `:core-ui` (`com.android.camera.coreui.*`)
- **scaffold/** — `CameraSampleScaffold(permissions, api, …, content)` (permission gate + black surface
  + API badge); `CameraApi` enum (`CAMERA2`, `CAMERAX`).
- **controls/** — `ShutterButton`, `RecordButton`, `CameraSwitchButton`,
  `ScrimIconButton(onClick, imageVector, …, size, iconSize)`, `CameraControlsBar(startSlot, center, endSlot)`,
  `ValueSlider(label, value, …, accentColor)`, `ZoomControls(zoomRatio, valueRange, onValueChange, stops)`.
- **overlay/** — `FocusIndicator(tapOffset)`, `RuleOfThirdsGrid()`, `ViewfinderTitleChip(text)`,
  `TorchChip(on, onToggle)`, `TorchGlow(visible)`, `SettingsOverlay(visible, onDismiss, content)`,
  `SettingsHeader(text)`, `SettingsRow(label, trailing)`,
  `SettingsDropdown(label, options, selected, onSelected, optionLabel)`, `settingsSwitchColors()`.
- **preview/** — `CapturedImagePreview(bitmap, onRetake, onDone)`, `CapturedVideoPreview(uri, onDismiss)`.
- **widget/** — `VideoPlayer(player)` (Compose-first Media3 `PlayerSurface`; tap to play/pause),
  `HdrWindowColorMode(enabled)` (toggle host-window HDR color mode, API 26+).
- **state/** — `LoadingView()`, `ErrorView(errorMessage, onRetry)`, `UnsupportedView(message)`.

## Catalog model (`app/.../catalog/domain/SampleCatalog.kt`)

- **`sampleCatalog: List<SampleCatalogItem>`** — the single registry; the `NavHost` and the home grid
  both derive from it.
- **`SampleCatalogItem(title, description, route, sampleEntryScreen, type, category, tags, isFeatured, keyArt)`**.
- **`SampleType`** = `{ CAMERAX, CAMERA2 }`; **`SampleCategory`** =
  `{ IMAGES, VIDEO, ML, GRAPHICS, EXTENSIONS, CONTROLS }` (drives the home filter); **`SampleTags`** =
  `{ MEDIA3, ML_KIT, VIDEO, EXTENSIONS, ANALYSIS }` (drives the tile's category dot color).

## Adding a new sample

1. **Generate**:
   ```bash
   ./gradlew createSample \
     -PsampleName="camera2-flash" \
     -PscreenName="Camera2FlashScreen" \
     -Ptitle="Camera2 • Flash" \
     -Pdesc="Toggle the flash with Camera2" \
     -Ptype="camera2"          # camera2 (default) | camerax
   ```
   This scaffolds `samples/camera2-flash/` (UiState / ViewModel / Controller / Screen + `build.gradle.kts`
   + `strings.xml`) and wires `settings.gradle.kts`, `app/build.gradle.kts`, the catalog
   (`SampleCatalog.kt`), and the catalog strings.
2. `./gradlew spotlessApply`.
3. Implement the feature in the generated `Controller` + `Screen`; add in-screen strings to the
   module's `strings.xml`; set the new `SampleCatalogItem`'s `category` (and `tags` / `isFeatured` if
   relevant).
4. Honor every TL;DR rule — reuse `:core-ui` / `:core-camera`, render via `CameraXPreview` /
   `Camera2Preview`, no loose strings, one feature.
5. Verify: `./gradlew assembleDebug spotlessCheck`.
