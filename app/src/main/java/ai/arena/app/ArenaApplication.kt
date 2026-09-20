package ai.arena.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import androidx.webkit.WebViewCompat

class ArenaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Pre-warm WebView for faster startup & cache enable
        // This helps with layout pre-load strategy
        try {
            // Trigger WebView initialization early
            WebViewCompat.getCurrentWebViewPackage(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Optimize for low RAM devices
        if (isLowRamDevice()) {
            // Reduce cache size for low RAM
            WebView.setDataDirectorySuffix("arena_lowram")
        }
    }

    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice
    }
}
