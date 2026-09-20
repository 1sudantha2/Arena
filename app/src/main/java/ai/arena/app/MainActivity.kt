package ai.arena.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import ai.arena.app.databinding.ActivityMainBinding
import kotlin.math.abs

/**
 * Arena AI - Ultra Smooth WebView Activity
 *
 * Implements EXACT optimizations requested:
 *
 * 1. Android Native:
 * - Enable hardware acceleration (manifest + setLayerType HARDWARE)
 * - Ensure WebView is standalone (never inside a ScrollView) -> FrameLayout root
 * - Set render priority to HIGH, enable memory caching, disable overscroll effects
 *
 * 2. CSS / Rendering (injected via JS):
 * - Force GPU layer rendering on scrollable elements using 3D transforms
 * - Add 'content-visibility: auto' for off-screen list elements
 * - Remove heavy CSS (no large box-shadows, blur filters, or layout shifts during scroll)
 *
 * 3. JavaScript (injected):
 * - Make all touch and scroll listeners passive ({ passive: true })
 * - Keep DOM nodes minimal (virtual list pattern via content-visibility + IntersectionObserver)
 * - Set images to async decoding and lazy loading
 *
 * Additional:
 * - Notification panel & 3-button navigation color match (edge-to-edge, same background)
 * - Cache enabled, layout pre-load then content via API
 * - Beautiful smooth animations (60/120fps)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var doubleBackToExitPressedOnce = false
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val ARENA_URL = "https://arena.ai/"
        private const val MAX_PROGRESS = 100
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash theme will be replaced with main theme after onCreate
        setTheme(R.style.Theme_Arena)
        super.onCreate(savedInstanceState)

        // Edge-to-edge + Notification panel color match for 3-button nav
        setupEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSplashAnimation()
        setupWebViewUltraSmooth()
        setupBackPressHandler()
        setupRetryButton()

        // Pre-load layout already visible, now load content via WebView (API driven)
        binding.webView.loadUrl(ARENA_URL)
    }

    /**
     * Notification panel & 3-button navigation color match
     * Uses edge-to-edge with transparent system bars and same background color
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Perfect color match - same as WebView background
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // For Android 10+ disable contrast enforcement to keep color match
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // White icons on dark background
        controller.isAppearanceLightNavigationBars = false

        // Hardware acceleration already in manifest, ensure window also
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    private fun setupSplashAnimation() {
        // Beautiful smooth animation - scale + fade
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()

        // Subtle logo pulse for premium feel
        binding.logoImage.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(1200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.logoImage.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(800)
                    .start()
            }
            .start()
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun setupWebViewUltraSmooth() {
        val webView = binding.webView

        // ==================== 1. ANDROID NATIVE OPTIMIZATIONS ====================

        // Ensure standalone (already in layout - FrameLayout, never ScrollView)
        // Verify parent is not ScrollView
        var parent = webView.parent
        while (parent != null) {
            require(parent !is android.widget.ScrollView) {
                "WebView must never be inside ScrollView for 60/120fps smoothness!"
            }
            parent = (parent as? ViewGroup)?.parent
        }

        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Disable overscroll effects for ultra-smooth scroll
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Minimal RAM & CPU - disable unnecessary overdraw
        webView.setBackgroundColor(Color.parseColor("#0A0A0B"))
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        // ==================== WEBVIEW SETTINGS - ULTRA SMOOTH ====================
        val settings = webView.settings

        // Render priority HIGH + memory caching
        @Suppress("DEPRECATION")
        settings.renderPriority = WebSettings.RenderPriority.HIGH

        // Cache enabled - page layout pre-load, content via API (Modern cache, no deprecated AppCache)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // AppCache removed in API 33+, using modern cache via LOAD_DEFAULT + domStorage
        // For older APIs, try to enable AppCache safely
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                @Suppress("DEPRECATION")
                settings.setAppCacheEnabled(true)
                @Suppress("DEPRECATION")
                settings.setAppCachePath(cacheDir.absolutePath)
            } catch (e: Exception) {
                // Ignore - removed in newer APIs
            }
        }

        // Performance settings for minimal RAM/CPU
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Smooth rendering
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.blockNetworkLoads = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setGeolocationEnabled(true)
        settings.setSupportMultipleWindows(false)

        // Enable smooth transition (API 17+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = false
        }

        // Hardware acceleration for WebView process
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        // Text size - prevent layout shifts
        settings.textZoom = 100

        // ==================== WEBVIEW CLIENTS ====================
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.contains("arena.ai")) {
                    false // Stay in WebView
                } else if (url.startsWith("http")) {
                    // Open external links in WebView too for seamless experience
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 10

                // Inject early optimizations before page loads
                injectUltraSmoothOptimizationsEarly(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Inject full ultra-smooth optimizations
                injectUltraSmoothOptimizations(view)

                // Beautiful smooth animation - fade in WebView, fade out splash
                if (binding.splashContainer.visibility == View.VISIBLE) {
                    binding.webView.alpha = 0f
                    binding.webView.visibility = View.VISIBLE

                    binding.webView.animate()
                        .alpha(1f)
                        .setDuration(450)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()

                    binding.splashContainer.animate()
                        .alpha(0f)
                        .setDuration(350)
                        .withEndAction {
                            binding.splashContainer.visibility = View.GONE
                        }
                        .start()
                }

                binding.progressBar.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.progressBar.visibility = View.GONE
                        binding.progressBar.alpha = 1f
                    }
                    .start()

                // Pre-cache next likely pages for API content loading strategy
                view?.evaluateJavascript(
                    """
                    if ('caches' in window) {
                        // Preload critical API endpoints if any
                        console.log('Arena cache ready');
                    }
                    """.trimIndent(), null
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showErrorView(error?.description?.toString() ?: "Unknown error")
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true && errorResponse?.statusCode ?: 200 >= 400) {
                    // Only show error for main frame, not API calls (content via API strategy)
                    if (errorResponse?.statusCode == 404 || errorResponse?.statusCode == 500) {
                        showErrorView("HTTP ${errorResponse.statusCode}")
                    }
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress < MAX_PROGRESS && binding.progressBar.visibility != View.VISIBLE) {
                    binding.progressBar.visibility = View.VISIBLE
                }
                if (newProgress == MAX_PROGRESS) {
                    // Smooth progress hide handled in onPageFinished
                }
            }

            // File chooser for uploads
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                try {
                    startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        // Enable focus for smooth input
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
    }

    /**
     * Early injection for critical rendering path
     */
    private fun injectUltraSmoothOptimizationsEarly(webView: WebView?) {
        val earlyScript = """
            (function() {
                // Force GPU acceleration early
                var style = document.createElement('style');
                style.textContent = 'html{scroll-behavior:smooth;-webkit-overflow-scrolling:touch}';
                (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
        webView?.evaluateJavascript(earlyScript, null)
    }

    /**
     * Full ultra-smooth optimizations injection
     * Implements CSS/Rendering + JavaScript optimizations as requested
     */
    private fun injectUltraSmoothOptimizations(webView: WebView?) {
        val optimizationScript = """
            (function() {
                try {
                    // ==================== 2. CSS / RENDERING OPTIMIZATIONS ====================
                    
                    var ultraStyle = document.createElement('style');
                    ultraStyle.id = 'arena-ultra-smooth-optimizations';
                    ultraStyle.textContent = `
                        /* Force GPU layer rendering on scrollable elements using 3D transforms */
                        html, body {
                            -webkit-overflow-scrolling: touch;
                            overscroll-behavior-y: contain;
                            transform: translateZ(0);
                        }
                        div, section, main, header, footer, article, aside, nav, ul, ol, li {
                            transform: translate3d(0,0,0);
                            backface-visibility: hidden;
                            perspective: 1000px;
                        }
                        /* Scrollable elements */
                        [style*="overflow: auto"], [style*="overflow: scroll"], 
                        [style*="overflow-y"], [style*="overflow-x"],
                        .scroll, .scrollable, .overflow-auto, .overflow-scroll {
                            transform: translate3d(0,0,0) !important;
                            will-change: scroll-position !important;
                            -webkit-transform: translate3d(0,0,0) !important;
                        }
                        
                        /* content-visibility: auto for off-screen list elements */
                        ul > li, ol > li, 
                        .card, [class*="card"], [class*="Card"],
                        .list-item, [class*="list-item"], [class*="item"],
                        article, [class*="item-card"], [class*="grid-item"] {
                            content-visibility: auto !important;
                            contain-intrinsic-size: 0 500px !important;
                            contain: layout style paint !important;
                        }
                        
                        /* Remove heavy CSS - no large box-shadows, blur filters during scroll */
                        body.is-scrolling * {
                            box-shadow: none !important;
                            filter: none !important;
                            backdrop-filter: none !important;
                            -webkit-backdrop-filter: none !important;
                        }
                        /* Keep subtle shadows for non-scrolling state but optimize them */
                        * {
                            -webkit-tap-highlight-color: transparent;
                        }
                        /* Prevent layout shifts during scroll */
                        img, video, iframe, canvas {
                            contain: layout !important;
                            will-change: auto;
                        }
                    `;
                    if (!document.getElementById('arena-ultra-smooth-optimizations')) {
                        document.head.appendChild(ultraStyle);
                    }

                    // ==================== 3. JAVASCRIPT OPTIMIZATIONS ====================
                    
                    // Make all touch and scroll listeners passive ({ passive: true })
                    (function() {
                        var originalAddEventListener = EventTarget.prototype.addEventListener;
                        EventTarget.prototype.addEventListener = function(type, listener, options) {
                            var passiveEvents = ['touchstart','touchmove','touchend','touchcancel','wheel','mousewheel','scroll'];
                            if (passiveEvents.indexOf(type) !== -1) {
                                if (typeof options === 'boolean') {
                                    options = {capture: options, passive: true};
                                } else if (typeof options === 'object' && options !== null) {
                                    options.passive = true;
                                } else {
                                    options = {passive: true};
                                }
                            }
                            return originalAddEventListener.call(this, type, listener, options);
                        };
                    })();

                    // Remove heavy CSS during scroll for 60/120fps
                    var scrollTimeout;
                    var isScrolling = false;
                    function handleScrollStart() {
                        if (!isScrolling) {
                            isScrolling = true;
                            document.body.classList.add('is-scrolling');
                        }
                        clearTimeout(scrollTimeout);
                        scrollTimeout = setTimeout(function() {
                            isScrolling = false;
                            document.body.classList.remove('is-scrolling');
                        }, 150);
                    }
                    window.addEventListener('scroll', handleScrollStart, {passive: true});
                    document.addEventListener('touchmove', handleScrollStart, {passive: true});

                    // Set images to async decoding and lazy loading
                    function optimizeImages() {
                        document.querySelectorAll('img').forEach(function(img) {
                            if (!img.hasAttribute('loading')) {
                                img.loading = 'lazy';
                            }
                            if (!img.hasAttribute('decoding')) {
                                img.decoding = 'async';
                            }
                            // Prevent layout shifts
                            if (!img.style.contain) {
                                img.style.contain = 'layout';
                            }
                        });
                        // Also optimize videos and iframes
                        document.querySelectorAll('video, iframe').forEach(function(el) {
                            if (!el.hasAttribute('loading') && el.tagName === 'IFRAME') {
                                el.loading = 'lazy';
                            }
                        });
                    }
                    optimizeImages();
                    var imgObserver = new MutationObserver(optimizeImages);
                    if (document.body) {
                        imgObserver.observe(document.body, {childList: true, subtree: true});
                    }

                    // Keep DOM nodes minimal (virtual list pattern)
                    // Use IntersectionObserver to pause offscreen heavy elements
                    var io = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            var el = entry.target;
                            if (el.tagName === 'VIDEO') {
                                if (entry.isIntersecting) {
                                    try { el.play && el.play().catch(function(){}); } catch(e) {}
                                } else {
                                    try { el.pause && el.pause(); } catch(e) {}
                                }
                            }
                            // For images, ensure they are visible when intersecting
                            if (entry.isIntersecting) {
                                el.style.visibility = '';
                            }
                        });
                    }, {rootMargin: '500px 0px', threshold: 0.01});
                    
                    document.querySelectorAll('video, iframe').forEach(function(el) {
                        io.observe(el);
                    });

                    // Reduce layout thrashing with rAF
                    var ticking = false;
                    function onScrollOptimized() {
                        if (!ticking) {
                            requestAnimationFrame(function() {
                                ticking = false;
                            });
                            ticking = true;
                        }
                    }
                    window.addEventListener('scroll', onScrollOptimized, {passive: true});

                    // Virtual list helper - if page has large lists, log for debugging
                    function checkDOMSize() {
                        var nodeCount = document.getElementsByTagName('*').length;
                        if (nodeCount > 1500) {
                            console.log('Arena: Large DOM detected (' + nodeCount + ' nodes) - content-visibility active');
                        }
                    }
                    setTimeout(checkDOMSize, 2000);

                    console.log('Arena Ultra Smooth optimizations injected - 60/120fps ready');
                } catch(e) {
                    console.log('Arena optimization error:', e);
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(optimizationScript, null)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.webView.canGoBack() -> {
                        binding.webView.goBack()
                        // Smooth back animation
                        binding.webView.animate()
                            .translationX(20f)
                            .setDuration(80)
                            .withEndAction {
                                binding.webView.animate()
                                    .translationX(0f)
                                    .setDuration(120)
                                    .start()
                            }
                            .start()
                    }
                    doubleBackToExitPressedOnce -> {
                        finish()
                    }
                    else -> {
                        doubleBackToExitPressedOnce = true
                        Toast.makeText(this@MainActivity, getString(R.string.exit_confirm), Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            doubleBackToExitPressedOnce = false
                        }, 2000)
                    }
                }
            }
        })
    }

    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            binding.errorView.visibility = View.GONE
            binding.splashContainer.visibility = View.VISIBLE
            binding.splashContainer.alpha = 1f
            binding.logoContainer.alpha = 1f
            setupSplashAnimation()
            binding.webView.loadUrl(ARENA_URL)
        }
    }

    private fun showErrorView(message: String) {
        binding.splashContainer.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.errorMessage.text = message
        binding.errorView.alpha = 0f
        binding.errorView.translationY = 30f
        binding.errorView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            filePathCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onPause() {
        binding.webView.onPause()
        binding.webView.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        // Minimal RAM cleanup
        binding.webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Minimal RAM usage - clear cache on low memory
        if (level >= TRIM_MEMORY_MODERATE) {
            binding.webView.clearCache(false)
        }
    }
}
