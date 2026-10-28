package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var currentWebView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle Android Back Navigation gracefully
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = currentWebView
                if (wv != null) {
                    wv.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : false") { result ->
                        if (result != "true") {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0F19))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .testTag("main_webview_container")
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("app_webview"),
                        factory = { context ->
                            createConfiguredWebView(context).also {
                                currentWebView = it
                            }
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0b0f19"))

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("https://appassets.androidplatform.net")) {
                        return false
                    }
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, request.url)
                        context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    return try {
                        filePickerLauncher.launch(intent)
                        true
                    } catch (e: Exception) {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = null
                        false
                    }
                }
            }

            addJavascriptInterface(AndroidBridge(this@MainActivity, this), "AndroidBridge")
            loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        }

        return webView
    }

    override fun onDestroy() {
        currentWebView?.destroy()
        currentWebView = null
        super.onDestroy()
    }

    inner class AndroidBridge(private val activity: MainActivity, private val webView: WebView) {

        @JavascriptInterface
        fun saveAndOpenPdf(base64Data: String, fileName: String): Boolean {
            return try {
                val cleanBase64 = base64Data.substringAfter("base64,").trim()
                val pdfBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val docsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: activity.filesDir
                val file = File(docsDir, fileName)
                FileOutputStream(file).use { it.write(pdfBytes) }

                val contentUri: Uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    file
                )

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooserIntent = Intent.createChooser(viewIntent, "পিডিএফ রিপোর্ট ওপেন করুন").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(chooserIntent)
                true
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error saving/opening PDF", e)
                false
            }
        }

        @JavascriptInterface
        fun saveAndShareJson(jsonContent: String, fileName: String): Boolean {
            return try {
                val docsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: activity.filesDir
                val file = File(docsDir, fileName)
                file.writeText(jsonContent)

                val contentUri: Uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(shareIntent, "JSON ব্যাকআপ শেয়ার করুন").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(chooser)
                true
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error sharing JSON", e)
                false
            }
        }

        @JavascriptInterface
        fun vibrate(durationMs: Long) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs.coerceAtLeast(10), VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(durationMs.coerceAtLeast(10), VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(durationMs.coerceAtLeast(10))
                    }
                }
            } catch (e: Exception) {
                Log.w("AndroidBridge", "Vibrate error", e)
            }
        }

        @JavascriptInterface
        fun vibratePattern(patternStr: String) {
            try {
                val timings = patternStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                if (timings.isNotEmpty()) {
                    val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(timings, -1)
                    }
                }
            } catch (e: Exception) {
                Log.w("AndroidBridge", "VibratePattern error", e)
            }
        }

        @JavascriptInterface
        fun signInWithGoogle() {
            activity.lifecycleScope.launch {
                try {
                    val credentialManager = CredentialManager.create(activity)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("705715516333-f1bupdl4tec22lusrf2qruobsng50vbg.apps.googleusercontent.com")
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(activity, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        webView.post {
                            webView.evaluateJavascript("window.onNativeGoogleSignIn ? window.onNativeGoogleSignIn('$idToken') : null", null)
                        }
                    } else {
                        webView.post {
                            webView.evaluateJavascript("toast('Google ক্রেডেনশিয়াল পাওয়া যায়নি', 'err')", null)
                        }
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.i("AndroidBridge", "Google sign-in cancelled by user")
                    webView.post {
                        webView.evaluateJavascript("toast('সাইন-ইন বাতিল করা হয়েছে', 'warn')", null)
                    }
                } catch (e: GetCredentialException) {
                    Log.w("AndroidBridge", "CredentialManager failed: ${e.message}")
                    // Fallback to web popup in JS (passing true to prevent loop)
                    webView.post {
                        webView.evaluateJavascript("if(window.fbApi && window.fbApi.google){ window.fbApi.google(true); } else { toast('গুগল সাইন-ইন ব্যর্থ', 'err'); }", null)
                    }
                } catch (e: Exception) {
                    Log.e("AndroidBridge", "Unexpected sign-in error", e)
                    webView.post {
                        webView.evaluateJavascript("toast('গুগল সাইন-ইন সমস্যা: ${e.localizedMessage ?: "ত্রুটি"}', 'err')", null)
                    }
                }
            }
        }
    }
}
