package com.lifechat.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @Suppress("DEPRECATION", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or
             View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(FirebaseBridge(), "FirebaseBridge")

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class FirebaseBridge {
        @JavascriptInterface
        fun signInAnonymously() {
            val auth = FirebaseAuth.getInstance()

            if (auth.currentUser != null) {
                sendResult(true, auth.currentUser!!.uid, "")
                return
            }

            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    sendResult(true, task.result?.user?.uid ?: "", "")
                } else {
                    sendResult(
                        false,
                        "",
                        task.exception?.localizedMessage ?: "Firebase login failed"
                    )
                }
            }
        }
    }

    private fun sendResult(ok: Boolean, uid: String, error: String) {
        val result = JSONObject()
            .put("ok", ok)
            .put("uid", uid)
            .put("error", error)
            .toString()

        runOnUiThread {
            webView.evaluateJavascript(
                "window.onFirebaseAuthResult && window.onFirebaseAuthResult(${JSONObject.quote(result)})",
                null
            )
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
