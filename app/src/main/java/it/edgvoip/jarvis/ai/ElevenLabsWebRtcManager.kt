package it.edgvoip.jarvis.ai

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ElevenLabsWebRtcManager {

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var webView: WebView? = null

    fun getWidgetUrl(agentId: String, tenantSlug: String): String {
        return Uri.Builder()
            .scheme("https")
            .authority("elevenlabs.io")
            .appendPath("convai")
            .appendPath("widget")
            .appendPath(agentId)
            .appendQueryParameter("tenant", tenantSlug)
            .build()
            .toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context, agentId: String): WebView {
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowContentAccess = true
            settings.userAgentString = settings.userAgentString + " JarvisApp/1.0"
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _isConnected.value = true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.let { req ->
                        val grantedResources = req.resources.filter { resource ->
                            resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                        }.toTypedArray()
                        if (grantedResources.isNotEmpty()) {
                            req.grant(grantedResources)
                        } else {
                            req.deny()
                        }
                    }
                }
            }
        }
        webView = wv
        return wv
    }

    fun loadWidget(agentId: String, tenantSlug: String) {
        val url = getWidgetUrl(agentId, tenantSlug)
        webView?.loadUrl(url)
    }

    fun updateState(listening: Boolean, speaking: Boolean) {
        _isListening.value = listening
        _isSpeaking.value = speaking
    }

    fun disconnect() {
        _isConnected.value = false
        _isListening.value = false
        _isSpeaking.value = false
        webView?.stopLoading()
        webView?.loadUrl("about:blank")
        webView?.destroy()
        webView = null
    }
}
