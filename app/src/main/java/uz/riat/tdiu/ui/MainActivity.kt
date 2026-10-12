package uz.riat.tdiu.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import uz.riat.tdiu.ui.widget.ScheduleWidgetProvider
import uz.riat.tdiu.data.update.UpdateChecker
import uz.riat.tdiu.R

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import uz.riat.tdiu.data.notification.ScheduleAlarmScheduler
import uz.riat.tdiu.ui.widget.NotificationConfigActivity
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val liveUrl = "https://tsue-digital-economy.web.app/"
    private val localUrl = "file:///android_asset/web/index.html"
    private var injected = false

    companion object {
        private const val REQ_NOTIF_PERMISSION = 701
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkNotificationPermissions()
        ScheduleAlarmScheduler.scheduleNextAlarm(this)
        UpdateChecker.checkForUpdates(this, silent = true)

        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(this, ScheduleWidgetProvider::class.java))
            for (id in ids) {
                ScheduleWidgetProvider.updateAppWidget(this, appWidgetManager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        swipeRefresh.setColorSchemeColors(0xFF00E1D9.toInt(), 0xFF004899.toInt())
        swipeRefresh.setOnRefreshListener {
            injected = false
            webView.reload()
        }

        configureWebView()

        if (isNetworkAvailable()) {
            webView.loadUrl(liveUrl)
        } else {
            webView.loadUrl(localUrl)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.loadsImagesAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.cacheMode = if (isNetworkAvailable()) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK

        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.isHapticFeedbackEnabled = true
        webView.addJavascriptInterface(RiatNativeBridge(), "RiatNative")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injected = false
                swipeRefresh.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                if (!injected) {
                    injected = true
                    injectMobileOptimizations(view)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    injected = false
                    view?.loadUrl(localUrl)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 95 && !injected) {
                    injected = true
                    swipeRefresh.isRefreshing = false
                    injectMobileOptimizations(view)
                }
            }
        }
    }

    private fun injectMobileOptimizations(view: WebView?) {
        // language=JavaScript
        val js = """
(function() {
    if (window.__riatNativeInjected) return;
    window.__riatNativeInjected = true;

    /* ── Mark as native app ──────────────────────────────── */
    document.documentElement.classList.add('is-native-app');
    document.body.classList.add('is-native-app');

    /* ── Remove PWA install banners ─────────────────────────*/
    function removePwa() {
        var ids = ['pwaInstallBanner', 'iosInstallModalOverlay', 'pwaBottomBar'];
        ids.forEach(function(id) {
            var el = document.getElementById(id);
            if (el) el.remove();
        });
        document.querySelectorAll('.pwa-install-banner, .pwa-prompt, .install-banner').forEach(function(el) {
            el.remove();
        });
    }
    removePwa();
    var pwaObserver = new MutationObserver(removePwa);
    pwaObserver.observe(document.body, { childList: true, subtree: true });

    /* ── Inject fix CSS ──────────────────────────────────────*/
    var style = document.createElement('style');
    style.id = 'riat-native-style';
    style.textContent = [
        /* Hide PWA banners always */
        '#pwaInstallBanner,#iosInstallModalOverlay,.pwa-install-banner,.install-banner{display:none!important}',

        /* ── NEWS CAROUSEL FIX ──────────────────────────────
           Replace absolute-offset JS carousel with native
           CSS scroll-snap. The wheel buttons are hidden
           on mobile; users swipe naturally. */
        '.news-wheel-section{overflow:hidden!important}',
        '.news-wheel-track{',
            'display:flex!important;',
            'flex-direction:row!important;',
            'gap:12px!important;',
            'overflow-x:scroll!important;',
            'overflow-y:hidden!important;',
            'scroll-snap-type:x mandatory!important;',
            '-webkit-overflow-scrolling:touch!important;',
            'scrollbar-width:none!important;',
            'padding:8px 16px 16px 16px!important;',
            'width:100%!important;',
            'box-sizing:border-box!important;',
            'transform:none!important;',
            'will-change:unset!important;',
            'transition:none!important;',
        '}',
        '.news-wheel-track::-webkit-scrollbar{display:none!important}',
        '.news-wheel-card{',
            'min-width:78vw!important;',
            'max-width:78vw!important;',
            'width:78vw!important;',
            'flex-shrink:0!important;',
            'scroll-snap-align:start!important;',
            'box-sizing:border-box!important;',
        '}',
        '.news-card-thumb-wrap{height:160px!important;overflow:hidden!important}',
        '.news-card-thumb-wrap img{width:100%!important;height:100%!important;object-fit:cover!important}',
        /* Hide navigation arrows on mobile */
        '.news-wheel-btn,.news-nav-btn,.news-arrow{display:none!important}',

        /* ── LEADERSHIP CARD FIX ────────────────────────────*/
        '.lcc-photo-col{',
            'height:280px!important;',
            'order:-1!important;',
            'background:#001f4d!important;',
            'overflow:hidden!important;',
        '}',
        '.lcc-photo-col img{',
            'width:100%!important;',
            'height:100%!important;',
            'object-fit:contain!important;',
            'object-position:center top!important;',
            'background:#001838!important;',
        '}',

        /* ── PARTNER LOGOS FIX ──────────────────────────────*/
        '.partner-logo-item{width:68px!important;height:68px!important;border-radius:14px!important}',
        '.partner-logo-img-wrap{width:46px!important;height:46px!important;padding:4px!important}',
        '.partner-logos-grid{gap:10px!important}',
    ].join('');
    (document.head || document.documentElement).appendChild(style);

    /* ── Disable JS carousel animation on mobile ────────────
       Patch the carousel's internal scrollTo calls to use
       native scroll-snap instead of JS translateX magic.
    */
    function patchNewsCarousel() {
        var track = document.querySelector('.news-wheel-track');
        if (!track) return;

        /* Remove existing listeners by cloning */
        var oldTrack = track;
        var newTrack = oldTrack.cloneNode(true);
        oldTrack.parentNode.replaceChild(newTrack, oldTrack);
        track = newTrack;

        /* Apply scroll behaviour */
        track.style.cssText = '';  /* Let our injected CSS take over */

        /* Kill all interval timers that drive the carousel */
        var highId = window.setInterval(function(){}, 99999);
        for (var i = 0; i <= highId; i++) { window.clearInterval(i); }
    }

    if (document.readyState === 'complete') {
        patchNewsCarousel();
    } else {
        window.addEventListener('load', patchNewsCarousel);
    }
})();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ScheduleAlarmScheduler.scheduleNextAlarm(this)
            }
        }
    }

    inner class RiatNativeBridge {
        @JavascriptInterface
        fun scheduleNextAlarm() {
            runOnUiThread {
                ScheduleAlarmScheduler.scheduleNextAlarm(this@MainActivity)
            }
        }

        @JavascriptInterface
        fun triggerTestNotification() {
            runOnUiThread {
                ScheduleAlarmScheduler.triggerTestNotification(this@MainActivity)
                Toast.makeText(this@MainActivity, "Тестовое звуковое уведомление отправлено", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, NotificationConfigActivity::class.java))
            }
        }

        @JavascriptInterface
        fun setTargetGroup(groupName: String) {
            runOnUiThread {
                getSharedPreferences("riat_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("user_group", groupName)
                    .apply()
                getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("active_target_name", groupName)
                    .apply()
                ScheduleAlarmScheduler.scheduleNextAlarm(this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        ScheduleAlarmScheduler.scheduleNextAlarm(this)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
