# Arena - Android Native App (Ultra Smooth WebView)

**Package:** `ai.arena.app`  
**APK Name:** `Arena`  
**Website:** https://arena.ai/  
**Language:** Kotlin + Android System WebView

## 🚀 Ultra Smooth 60/120fps Optimizations

This app implements **exact** optimizations requested for minimal RAM/CPU and ultra-smooth scrolling:

### 1. Android Native:
- ✅ **Hardware acceleration** enabled in manifest + `setLayerType(LAYER_TYPE_HARDWARE)`
- ✅ **WebView standalone** - never inside ScrollView (FrameLayout root)
- ✅ **Render priority HIGH**, memory caching (50MB), **overscroll disabled** (`OVER_SCROLL_NEVER`)

### 2. CSS / Rendering (Injected via JS):
- ✅ **Force GPU layer** rendering on scrollable elements using 3D transforms (`translate3d(0,0,0)`, `translateZ(0)`, `will-change`, `backface-visibility`)
- ✅ **`content-visibility: auto`** for off-screen list elements (`contain-intrinsic-size: 0 500px`, `contain: layout style paint`)
- ✅ **Remove heavy CSS** - no large box-shadows, blur filters, or layout shifts during scroll (`.is-scrolling` class removes shadows/filters while scrolling)

### 3. JavaScript (Injected):
- ✅ **All touch & scroll listeners passive** (`{ passive: true }`) via `EventTarget.prototype.addEventListener` override
- ✅ **DOM nodes minimal** (virtual list pattern via `content-visibility` + `IntersectionObserver` for videos/iframes, pausing offscreen media)
- ✅ **Images async decoding & lazy loading** (`loading="lazy"`, `decoding="async"` + MutationObserver)

### Extra Features:
- ✅ **Notification panel & 3-button navigation color match** - Edge-to-edge with `#0A0A0B` background, transparent system bars, light icons
- ✅ **Cache enabled** - `LOAD_DEFAULT`, 50MB AppCache, DOM storage, database, `setAppCacheEnabled`
- ✅ **Layout pre-load + content via API** - Splash with shimmer placeholder, layout pre-loads instantly, content loads via WebView API
- ✅ **Beautiful smooth animations** - Fade in/out (350ms), scale up with overshoot, slide up, logo pulse, progress bar, decelerate/accelerate interpolators for 60/120fps feel
- ✅ File chooser, geolocation, error handling, back press with animation, offline handling

## 📱 Screenshots & Design
- Dark theme matching arena.ai (#0A0A0B background, #8B5CF6 primary)
- Vector logo (adaptive icon) - modern geometric "A"
- Splash background with subtle gradient glow
- Material3 theming

## 🛠️ Build from GitHub (APK)

### Automatic Build:
Push to `main` or `arena/**` branches triggers GitHub Actions workflow:
- **Workflow:** `.github/workflows/build-apk.yml`
- **Artifacts:** `Arena-debug-apk` and `Arena-release-apk`
- **APK Output:** `app/build/outputs/apk/debug/Arena-debug.apk` (renamed to `Arena`)

### Manual Build Locally:
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/Arena-debug.apk
```

### Requirements:
- JDK 17
- Android SDK 34
- Gradle 8.5

## 📂 Project Structure
```
app/src/main/
├── java/ai/arena/app/
│   ├── MainActivity.kt (Ultra smooth WebView + injections)
│   └── ArenaApplication.kt (Pre-warm, low RAM optimization)
├── res/
│   ├── layout/activity_main.xml (Standalone WebView, no ScrollView)
│   ├── drawable/ic_logo.xml (Vector logo)
│   ├── drawable/ic_launcher_foreground.xml
│   ├── drawable/splash_background.xml
│   ├── values/colors.xml (Matched nav colors)
│   ├── values/themes.xml (Edge-to-edge)
│   └── anim/ (fade_in, fade_out, scale_up, slide_up)
└── AndroidManifest.xml (hardwareAccelerated=true)
```

## 🎨 Color Matching (Notification Panel & 3-Button Nav)
- `status_bar` = `#0A0A0B`
- `navigation_bar` = `#0A0A0B`
- `window.isNavigationBarContrastEnforced = false`
- `WindowCompat.setDecorFitsSystemWindows(window, false)`
- Transparent system bars + dark icons for perfect match

## 📦 APK Info
- **Name:** Arena
- **Package:** ai.arena.app
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34
- **Version:** 1.0.0
- **Size:** ~3-5MB (optimized with R8)

## 🔧 How to Install
1. Go to Actions tab in GitHub
2. Download `Arena-debug-apk` artifact
3. Install on device (allow unknown sources)

---
Built with ❤️ for ultra-smooth 60/120fps experience
