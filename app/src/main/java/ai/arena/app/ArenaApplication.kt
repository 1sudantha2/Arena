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
        try {
            WebViewCompat.getCurrentWebViewPackage(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Optimize for low RAM devices - setDataDirectorySuffix requires API 28+
        if (isLowRamDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("arena_lowram")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice
    }
}
