package dev.mmrlx.compose.webui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import dev.mmrlx.webui.PathHandler
import dev.mmrlx.webui.WebUI
import dev.mmrlx.webui.WebUIResourceRequest
import java.lang.reflect.Constructor
import java.util.concurrent.CopyOnWriteArrayList

class WebUISettings {
    val schemeWhitelist: MutableSet<String> = linkedSetOf()
    var useDefaultApplicationInterface: Boolean = true
    var useDefaultFileSystem: Boolean = true
    var debug: Boolean = false
    var forceKillProcess: Boolean = false
    var useConsoleInterceptor: Boolean = false
    var darkMode: Boolean = false
}

class WebUIClientScope internal constructor(
    val webview: WebView,
)

class PathHandlerArguments internal constructor() {
    internal val values = mutableListOf<Pair<Class<*>, Any?>>()

    fun add(value: Pair<Class<*>, Any?>) {
        values += value
    }
}

class WebUIState internal constructor(
    private val context: Context,
    private val origin: String,
    private val entryPath: String,
) {
    private val webUI = WebUI(context)
    private val settingsModel = WebUISettings()
    private val clientCallbacks = CopyOnWriteArrayList<(WebUIClientScope) -> Unit>()
    private val handlers = linkedMapOf<Class<out PathHandler>, PathHandler>()

    @Volatile
    private var webView: WebView? = null

    fun settings(block: WebUISettings.() -> Unit): WebUIState = apply {
        settingsModel.apply(block)
        webView?.let(::applySettings)
    }

    fun client(block: (WebUIClientScope) -> Unit): WebUIState = apply {
        clientCallbacks.clear()
        clientCallbacks += block
        webView?.let { block(WebUIClientScope(it)) }
    }

    fun chromeClient(block: () -> Unit): WebUIState = apply {
        block()
        webView?.webChromeClient = WebChromeClient()
    }

    fun registerPathHandler(
        handlerClass: Class<out PathHandler>,
        configure: PathHandlerArguments.() -> Unit = {},
    ): WebUIState = apply {
        val arguments = PathHandlerArguments().apply(configure)
        handlers[handlerClass] = instantiateHandler(handlerClass, arguments.values)
    }

    fun destroy() {
        val view = webView ?: return
        webView = null
        webUI.shared.webView = null
        view.post {
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
    }

    internal fun updateInsets(topPx: Int, bottomPx: Int) {
        webUI.insets.topPx = topPx
        webUI.insets.bottomPx = bottomPx
        webView?.post {
            webView?.evaluateJavascript(
                "document.documentElement.style.setProperty('--window-inset-top','${topPx}px');" +
                    "document.documentElement.style.setProperty('--window-inset-bottom','${bottomPx}px');",
                null,
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    internal fun obtainWebView(): WebView {
        webView?.let { return it }

        return WebView(context).also { view ->
            webView = view
            webUI.shared.webView = view
            view.setBackgroundColor(Color.TRANSPARENT)
            applySettings(view)
            view.webChromeClient = WebChromeClient()
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = intercept(request)
            }
            clientCallbacks.forEach { it(WebUIClientScope(view)) }
            view.loadUrl(resolveEntryUrl())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(view: WebView) {
        WebView.setWebContentsDebuggingEnabled(settingsModel.debug)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = settingsModel.useDefaultFileSystem
            allowContentAccess = settingsModel.useDefaultFileSystem
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
    }

    private fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val requestUri = request.url
        val originUri = Uri.parse(origin)
        if (!requestUri.scheme.equals(originUri.scheme, ignoreCase = true) ||
            !requestUri.host.equals(originUri.host, ignoreCase = true)
        ) return null

        val absolutePath = requestUri.encodedPath.orEmpty().ifEmpty { "/" }
        val handler = handlers.values
            .sortedByDescending { it.id.length }
            .firstOrNull { absolutePath.startsWith(normalizeHandlerId(it.id)) }
            ?: return null

        val prefix = normalizeHandlerId(handler.id)
        val relativePath = absolutePath.removePrefix(prefix).removePrefix("/")
        return handler.handle(WebUIResourceRequest(request, relativePath))
    }

    private fun resolveEntryUrl(): String {
        val normalizedOrigin = origin.trimEnd('/')
        val normalizedPath = entryPath.trimStart('/')
        return "$normalizedOrigin/$normalizedPath"
    }

    private fun instantiateHandler(
        handlerClass: Class<out PathHandler>,
        arguments: List<Pair<Class<*>, Any?>>,
    ): PathHandler {
        val constructor = handlerClass.declaredConstructors
            .filter { it.parameterCount == arguments.size + 1 }
            .firstOrNull { candidate -> constructorMatches(candidate, arguments) }
            ?: error(
                "No compatible constructor found for ${handlerClass.name}; " +
                    "expected (WebUI, ${arguments.joinToString { it.first.name }})",
            )

        constructor.isAccessible = true
        val values = arrayOfNulls<Any?>(arguments.size + 1)
        values[0] = webUI
        arguments.forEachIndexed { index, (_, value) -> values[index + 1] = value }
        return constructor.newInstance(*values) as PathHandler
    }

    private fun constructorMatches(
        constructor: Constructor<*>,
        arguments: List<Pair<Class<*>, Any?>>,
    ): Boolean {
        val parameterTypes = constructor.parameterTypes
        if (!parameterTypes[0].isAssignableFrom(WebUI::class.java)) return false
        return arguments.indices.all { index ->
            val declaredType = arguments[index].first
            val value = arguments[index].second
            val parameterType = parameterTypes[index + 1]
            value == null || parameterType.isAssignableFrom(declaredType) || parameterType.isInstance(value)
        }
    }

    private fun normalizeHandlerId(id: String): String {
        val prefixed = if (id.startsWith('/')) id else "/$id"
        return if (prefixed.endsWith('/')) prefixed else "$prefixed/"
    }
}

@Composable
fun rememberWebUIState(
    origin: String,
    entryPath: String,
    configure: (WebUIState) -> Unit = {},
): WebUIState {
    val context = LocalContext.current
    val state = remember(context, origin, entryPath) {
        WebUIState(context, origin, entryPath)
    }
    configure(state)
    return state
}

@Composable
fun WebUIState.insets(paddingValues: PaddingValues) {
    val density = LocalDensity.current
    val top = with(density) { paddingValues.calculateTopPadding().roundToPx() }
    val bottom = with(density) { paddingValues.calculateBottomPadding().roundToPx() }
    updateInsets(top, bottom)
}

@Composable
fun WebUIView(
    state: WebUIState,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { state.obtainWebView() },
        modifier = modifier,
    )

    DisposableEffect(state) {
        onDispose { /* The owner explicitly calls state.destroy(). */ }
    }
}
