package com.dergoogler.mmrl.pathHandler


import android.util.Log
import android.webkit.WebResourceResponse
import androidx.compose.material3.ColorScheme
import com.dergoogler.mmrl.model.WebColors
import com.dergoogler.mmrl.ui.theme.SemanticColors
import dev.mmrlx.webui.PathHandler
import dev.mmrlx.webui.WebUI
import dev.mmrlx.webui.WebUIResourceRequest
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class InternalPathHandler(
    webui: WebUI,
    private val readmeUrl: String,
    private val colorScheme: ColorScheme,
    private val semanticColors: SemanticColors,
) : PathHandler(webui) {
    override val id = "/internal/"
    val webColors get() = WebColors(colorScheme, semanticColors)
    val assetsPathHandler = AssetsPathHandler(this)

    override fun handle(request: WebUIResourceRequest): WebResourceResponse {
        val path = request.path

        try {
            if (path.matches(Regex("^assets(/.*)?$"))) {
                return assetsPathHandler.handle(
                    WebUIResourceRequest(
                        method = request.method,
                        isForMainFrame = request.isForMainFrame,
                        url = request.url,
                        path = path.removePrefix("assets/"),
                        requestHeaders = request.requestHeaders,
                        isRedirect = request.isRedirect,
                        hasGesture = request.hasGesture()
                    )
                )
            }



            if (path.matches(Regex("readme\\.md"))) {
                val readmeUri = WebUiContentPolicy.requireReadmeUri(readmeUrl)
                val connection = URL(readmeUri.toString()).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.connect()
                if (connection.responseCode !in 200..299) return notFoundResponse
                val length = connection.contentLengthLong
                if (length > WebUiContentPolicy.boundedReadLimitBytes()) return notFoundResponse
                val body = connection.inputStream.use { input ->
                    val bytes = readBounded(input, WebUiContentPolicy.boundedReadLimitBytes())
                    WebUiContentPolicy.sanitizeMarkdown(bytes.toString(Charsets.UTF_8))
                }
                return htmlResponse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
            }

            if (path.matches(Regex("insets\\.css"))) {
                return insets.css.asStyleResponse()
            }

            if (path.matches(Regex("colors\\.css"))) {
                return webColors.allCssColors.asStyleResponse()
            }

            return notFoundResponse
        } catch (e: IOException) {
            Log.e("InternalPathHandler", "Error opening mmrl asset path: $path", e)
            return notFoundResponse
        }
    }


    private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > maxBytes) {
                throw IOException("README response exceeds bounded read limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
