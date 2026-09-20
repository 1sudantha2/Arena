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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import ai.arena.app.databinding.ActivityMainBinding

/**
 * Arena AI - Ultra Smooth WebView Activity - FIXED RENDER + NAV BAR
 *
 * Fixes:
 * - Page render: Less aggressive CSS injection (only scrollable containers get GPU layer, content-visibility only for ul>li)
 * - 3-button nav overlap: Edge-to-edge with WindowInsets handling, solid #0A0A0B color match, bottom padding for WebView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var doubleBackToExitPressedOnce = false
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val ARENA_URL = "https://arena.ai/"
        private const val MAX_PROGRESS = 100
        private const val ARENA_BACKGROUND = "#0A0A0B"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Arena)
        super.onCreate(savedInstanceState)

        setupEdgeToEdgeFixed()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsetsFix()
        setupSplashAnimation()
        setupWebViewUltraSmooth()
        setupBackPressHandler()
        setupRetryButton()

        binding.webView.loadUrl(ARENA_URL)
    }

    /**
     * FIXED: Notification panel & 3-button navigation color match + no overlap
     * - Solid color #0A0A0B for status & nav bar (perfect match)
     * - Edge-to-edge with WindowInsets handling to prevent content behind nav bar
     */
    private fun setupEdgeToEdgeFixed() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Solid color match - NOT transparent to prevent overlap issues
        val arenaColor = Color.parseColor(ARENA_BACKGROUND)
        window.statusBarColor = arenaColor
        window.navigationBarColor = arenaColor

        // Disable contrast enforcement to keep solid color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // White icons on dark
        controller.isAppearanceLightNavigationBars = false

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    /**
     * FIXED: Handle WindowInsets to prevent WebView content going behind nav bar
     */
    private fun setupWindowInsetsFix() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply padding to root so content doesn't go behind system bars
            // Top for status bar, bottom for nav bar (3-button)
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )

            // Also ensure WebView gets bottom inset for its internal content
            // The website's "Ask anything..." bar should be above nav bar
            binding.webView.updatePadding(
                bottom = 0 // WebView itself already inset via parent, but keep 0 to avoid double
            )

            // For IME (keyboard), adjust bottom padding
            if (ime.bottom > 0) {
                // Keyboard visible - adjust
                binding.rootContainer.updatePadding(bottom = ime.bottom)
            }

            WindowInsetsCompat.CONSUMED
        }

        // Also handle WebView's own insets for better compatibility
        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to WebView to prevent website's fixed bottom bar going behind nav
            // This is crucial for "Ask anything..." bar
            v.updatePadding(bottom = 0) // Parent already handles, but keep for safety
            insets
        }
    }

    private fun setupSplashAnimation() {
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()

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

        var parent = webView.parent
        while (parent != null) {
            require(parent !is android.widget.ScrollView) {
                "WebView must never be inside ScrollView!"
            }
            parent = (parent as? ViewGroup)?.parent
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.setBackgroundColor(Color.parseColor(ARENA_BACKGROUND))
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        val settings = webView.settings

        // Render priority HIGH via reflection for older APIs
        try {
            val renderPriorityField = WebSettings::class.java.getMethod(
                "setRenderPriority",
                Class.forName("android.webkit.WebSettings\$RenderPriority")
            )
            val renderPriorityEnum = Class.forName("android.webkit.WebSettings\$RenderPriority")
            val highField = renderPriorityEnum.getField("HIGH")
            val highValue = highField.get(null)
            renderPriorityField.invoke(settings, highValue)
        } catch (e: Exception) {
        }

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        settings.textZoom = 100

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.contains("arena.ai")) {
                    false
                } else if (url.startsWith("http")) {
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
                injectUltraSmoothOptimizationsEarly(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectUltraSmoothOptimizationsFixed(view)

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
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
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

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
    }

    private fun injectUltraSmoothOptimizationsEarly(webView: WebView?) {
        val earlyScript = """
            (function() {
                var style = document.createElement('style');
                style.textContent = 'html{scroll-behavior:smooth;-webkit-overflow-scrolling:touch}';
                (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
        webView?.evaluateJavascript(earlyScript, null)
    }

    /**
     * FIXED: Less aggressive optimizations to prevent render issues
     * - Only scrollable containers get GPU layer (not all divs)
     * - content-visibility only for ul>li, ol>li (not cards that caused black boxes)
     * - No contain: layout on images that broke layout
     */
    private fun injectUltraSmoothOptimizationsFixed(webView: WebView?) {
        val optimizationScript = """
            (function() {
                try {
                    // ==================== FIXED CSS / RENDERING OPTIMIZATIONS ====================
                    var ultraStyle = document.createElement('style');
                    ultraStyle.id = 'arena-ultra-smooth-optimizations';
                    ultraStyle.textContent = `
                        /* Smooth scrolling - safe */
                        html {
                            scroll-behavior: smooth;
                            -webkit-overflow-scrolling: touch;
                        }
                        body {
                            overscroll-behavior-y: contain;
                            -webkit-tap-highlight-color: transparent;
                            /* Ensure body doesn't go behind nav bar - add safe area */
                            padding-bottom: env(safe-area-inset-bottom);
                        }
                        /* FIXED: Only scrollable containers get GPU layer - NOT all divs */
                        [style*="overflow: auto"], [style*="overflow: scroll"],
                        [style*="overflow-y: auto"], [style*="overflow-y: scroll"],
                        [style*="overflow-x: auto"], [style*="overflow-x: scroll"],
                        .scroll, .scrollable, .overflow-auto, .overflow-scroll,
                        [class*="scroll-container"], main {
                            transform: translate3d(0,0,0);
                            will-change: scroll-position;
                            -webkit-transform: translate3d(0,0,0);
                            backface-visibility: hidden;
                        }
                        /* FIXED: content-visibility only for list items, NOT cards (cards caused black boxes) */
                        ul > li, ol > li {
                            content-visibility: auto;
                            contain-intrinsic-size: 0 400px;
                        }
                        /* FIXED: Remove heavy CSS only during scroll, but keep it minimal */
                        /* Only remove large shadows during scroll for performance */
                        body.is-scrolling [style*="box-shadow: 0 0 20"],
                        body.is-scrolling [style*="box-shadow: 0 4px 20"],
                        body.is-scrolling [style*="blur(20"],
                        body.is-scrolling [style*="blur(10"] {
                            box-shadow: none !important;
                            filter: none !important;
                            backdrop-filter: none !important;
                        }
                        /* Ensure images don't cause layout shifts but don't break with contain */
                        img {
                            max-width: 100%;
                            height: auto;
                        }
                        /* Fix for Arena's bottom input bar - ensure it's above nav bar */
                        [class*="Ask"], [class*="input"], [class*="bottom-bar"], footer {
                            padding-bottom: env(safe-area-inset-bottom, 0px);
                            margin-bottom: 0;
                        }
                    `;
                    var existing = document.getElementById('arena-ultra-smooth-optimizations');
                    if (existing) existing.remove();
                    document.head.appendChild(ultraStyle);

                    // ==================== FIXED JAVASCRIPT OPTIMIZATIONS ====================
                    // Passive listeners
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

                    // Scroll handling for performance
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

                    // FIXED: Images async decoding and lazy loading - safe version
                    function optimizeImages() {
                        document.querySelectorAll('img').forEach(function(img) {
                            try {
                                if (!img.hasAttribute('loading')) {
                                    img.loading = 'lazy';
                                }
                                if (!img.hasAttribute('decoding')) {
                                    img.decoding = 'async';
                                }
                            } catch(e) {}
                        });
                    }
                    optimizeImages();
                    var imgObserver = new MutationObserver(optimizeImages);
                    if (document.body) {
                        imgObserver.observe(document.body, {childList: true, subtree: true});
                    }

                    // FIXED: Keep DOM minimal - only pause offscreen videos, not hide images
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
                        });
                    }, {rootMargin: '400px 0px', threshold: 0.01});
                    
                    document.querySelectorAll('video').forEach(function(el) {
                        try { io.observe(el); } catch(e) {}
                    });

                    // rAF for smooth scroll
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

                    console.log('Arena FIXED ultra smooth injected - render fixed, nav bar fixed');
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
        if (level >= TRIM_MEMORY_MODERATE) {
            binding.webView.clearCache(false)
        }
    }
}
