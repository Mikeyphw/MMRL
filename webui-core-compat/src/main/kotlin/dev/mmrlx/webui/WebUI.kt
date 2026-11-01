package dev.mmrlx.webui

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.io.InputStream

open class WebUI internal constructor(
    internal val shared: SharedWebUI,
) {
    constructor(context: Context) : this(SharedWebUI(context.applicationContext))

    protected constructor(other: WebUI) : this(other.shared)

    val kontext: Context
        get() = shared.context

    val insets: WebUIInsets
        get() = shared.insets
}

internal class SharedWebUI(
    val context: Context,
) {
    val insets = WebUIInsets()

    @Volatile
    var webView: WebView? = null
}

class WebUIInsets {
    @Volatile
    var topPx: Int = 0
        internal set

    @Volatile
    var bottomPx: Int = 0
        internal set

    val css: String
        get() = ":root{--window-inset-top:${topPx}px;--window-inset-bottom:${bottomPx}px;}"
}

abstract class PathHandler(
    webui: WebUI,
) : WebUI(webui) {
    open val id: String = "/"

    abstract fun handle(request: WebUIResourceRequest): WebResourceResponse

    protected val notFoundResponse: WebResourceResponse
        get() = response(
            mimeType = "text/plain",
            statusCode = 404,
            reasonPhrase = "Not Found",
            body = ByteArrayInputStream("Not found".toByteArray()),
        )

    protected fun htmlResponse(input: InputStream): WebResourceResponse =
        response("text/html", body = input)

    protected fun String.asStyleResponse(): WebResourceResponse =
        response(
            mimeType = "text/css",
            body = ByteArrayInputStream(toByteArray()),
        )

    private fun response(
        mimeType: String,
        statusCode: Int = 200,
        reasonPhrase: String = "OK",
        body: InputStream,
    ): WebResourceResponse =
        WebResourceResponse(
            mimeType,
            "UTF-8",
            statusCode,
            reasonPhrase,
            mapOf(
                "Cache-Control" to "no-store",
                "Access-Control-Allow-Origin" to "*",
            ),
            body,
        )
}

data class WebUIResourceRequest(
    val method: String,
    val isForMainFrame: Boolean,
    val url: Uri,
    val path: String,
    val requestHeaders: Map<String, String>,
    val isRedirect: Boolean,
    val hasGesture: Boolean,
) {
    constructor(request: WebResourceRequest, path: String) : this(
        method = request.method,
        isForMainFrame = request.isForMainFrame,
        url = request.url,
        path = path,
        requestHeaders = request.requestHeaders.orEmpty(),
        isRedirect = request.isRedirect,
        hasGesture = request.hasGesture(),
    )

    fun hasGesture(): Boolean = hasGesture
}
