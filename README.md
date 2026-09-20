# 3D Model Viewer

A modern, highly interactive Android application built using Jetpack Compose that allows users to display, arrange, move, and manipulate multiple 3D models concurrently on a single canvas workspace.

---

## Video

[Screencast From 2026-09-20 12-34-00.webm](https://github.com/user-attachments/assets/626e4d39-cfa7-4ab3-b502-12e1d28e5148)



https://github.com/user-attachments/assets/43f745dc-6981-449d-9044-0d95b4debd74




## 🚀 3D Graphics Foundation

### Library Used: **Sceneview (v2.3.0)**
This project uses [Sceneview](https://github.com/SceneView/sceneview-android), a high-performance 3D framework built directly on top of Google's **Filament** entity-component renderer.

#### Why Sceneview?
1. **Jetpack Compose Native:** Provides out-of-the-box Composable architecture wrapper (`Scene`) that eliminates the complex lifecycles and layout integration overhead traditionally found when hosting Android views (`SurfaceView`/`TextureView`) inside Compose.
2. **Filament Powerhouse:** Leverages Filament's industry-standard Physically Based Rendering (PBR) engine for photo-realistic materials and textures without requiring deep, low-level OpenGL ES or Vulkan expertise.
3. **Optimized Resource Management:** Offers smart, lifecycle-aware Kotlin utilities (`rememberEngine()`, `rememberModelLoader()`) that prevent graphics context leaks and ensure hardware-accelerated asset loading.

---

## ⚡ Performance Optimizations

Managing multiple live 3D rendering viewports simultaneously on a mobile platform presents severe performance challenges. The following optimizations were applied to ensure smooth 60 FPS performance:

* **Engine & Loader Singleton Pattern:** A single Filament `Engine` and `ModelLoader` context are instantiated once at the root level (`ViewerApp`) and shared down across all instances of `ModelCard`. This prevents massive memory bloat and initialization stalls that occur when instantiating raw graphics contexts per model.
* **Constant Unit Base Scaling:** Model transforms utilize `node.scaleToUnitCube(1.0f)` inside `updateNodeTransform`. This decouples the model's inner 3D mesh boundaries from structural multi-window container resizes, completely averting costly re-computation of bounding boxes and preventing "scale-to-fit" layout jumps.
* **Granular Recomposition Controls via `key(model.id)`:** Cards are structured inside a dynamic collection mapped through Compose's explicit `key` utility. When a single model is translated, dragged to the front, or dismissed, only that specific card experiences recomposition, leaving other complex rendering threads uninterrupted.
* **Hybrid State Synchronization:** High-frequency layout changes (like pixel-by-pixel dragging and sizing animations) are stored and performed entirely via localized `mutableFloatStateOf` layers. Global model list state updates are debounced and propagated up through `onChange` callbacks only when a gesture phase concludes, bypassing rendering lags caused by heavy structural item mappings.
* **Robust Process Preservation:** `ViewerModel` implements `@Parcelize` to survive low-memory activity destruction and device orientation rotations seamlessly, avoiding unexpected model reloading overhead.

---

## ⚖️ Architectural Trade-offs

* **Overlay Gesture Interception Layering:** To reliably support dragging, card resizing, and 3D rotations, a high-level gesture-capturing `Box` overlay covers the viewport bounds. The trade-off is that direct, multi-finger input tracking to sub-components inside Filament's inner viewport is restricted to a toggled state control (`rotateMode`), prioritizing deterministic layout stability over unconstrained native touch propagation.
* **Local Mutability vs Pure Unidirectional Data Flow (UDF):** To optimize performance, dragging updates local state parameters *before* rewriting the model list. While a strictly traditional UDF pattern dictates that every single coordinate pixel change must travel up to the single source of truth and travel back down, doing so introduces visible visual stuttering inside rich 3D rendering loops.

---

## 🔮 Future Improvements (With More Time)

1. **Advanced Lighting & Environment Mapping:** Introduce Image-Based Lighting (IBL) by loading high-dynamic-range `.hdr` or `.ktx` skyboxes to dynamically project environmental reflections and ambient shadows onto models.
2. **Asynchronous Load States & Placeholders:** Inject visual shimmers or loading wheels into the card while `modelLoader.createModelInstance` decodes large `.glb` files asynchronously on back-ground IO threads.
3. **Advanced Z-Index Contexts:** Track explicit window layer depths to allow cards to slide beneath or cleanly overlap each other with custom alpha blending when intersecting boundaries.

---

## 🐛 Known Limitations & Bugs

* **Filament Surface Layering Constraints:** The project applies `setZOrderMediaOverlay(true)` to embed surface rendering layouts inline. On certain legacy GPU architectures or older Android versions, this can occasionally lead to edge clipping or background artifact glitches when regular Jetpack Compose components completely overlap the 3D scene.
* **Static Skeletal Animations:** Although the imported `.glb` assets may contain complex embedded skeleton bone animations, the application currently focuses entirely on layout manipulations (translations, manually controlled rotations, and scale) and doesn't trigger skeletal mesh timelines.

---

## Devices Tested

OnePlus 10T

Redmi Note 5 Pro
