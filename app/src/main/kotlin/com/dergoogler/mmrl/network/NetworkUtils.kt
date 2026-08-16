package com.dergoogler.mmrl.network

import com.dergoogler.mmrl.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

object NetworkUtils {
    private var useDoh: Boolean = false
    private var cacheDirOrNull: File? = null
    private var sharedCacheRoot: File? = null
    private var sharedCache: Cache? = null
    @Volatile private var sharedClient: OkHttpClient? = null

    @Synchronized
    fun setCacheDir(dir: File) {
        if (cacheDirOrNull == dir) return
        cacheDirOrNull = dir
        sharedClient = null
    }

    @Synchronized
    fun setEnableDoh(doh: Boolean) {
        if (useDoh == doh) return
        useDoh = doh
        sharedClient = null
    }

    @Synchronized
    private fun cacheOrNull(): Cache? {
        val root = cacheDirOrNull ?: return null
        val cacheRoot = File(root, "okhttp")
        if (sharedCacheRoot != cacheRoot) {
            sharedCache?.close()
            sharedCacheRoot = cacheRoot
            sharedCache = Cache(cacheRoot, 10 * 1024 * 1024)
        }
        return sharedCache
    }

    fun isHTML(text: String) =
        "<html\\s*>|<head\\s*>|<body\\s*>|<!doctype\\s*html\\s*>"
            .toRegex()
            .containsMatchIn(text)

    fun isUrl(url: String) = url.toHttpUrlOrNull() != null

    fun isBlobUrl(url: String) =
        "https://github.com/[^/]+/[^/]+/blob/.+"
            .toRegex()
            .matches(url)

    fun createOkHttpClient(): OkHttpClient {
        sharedClient?.let { return it }
        return synchronized(this) {
            sharedClient ?: buildOkHttpClient().also { sharedClient = it }
        }
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(cacheOrNull())
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor { Timber.i(it) }
                    .apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
            )
        } else {
            builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        }

        val bootstrapClient = builder.build()
        builder.dns(DnsResolver(bootstrapClient, useDoh))

        builder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
            request.header("User-Agent", "MMRL/${BuildConfig.VERSION_CODE}")
            request.header("Accept-Language", Locale.getDefault().toLanguageTag())
            chain.proceed(request.build())
        }

        return builder.build()
    }

    fun createRetrofit(): Retrofit.Builder {
        val client = createOkHttpClient()

        return Retrofit
            .Builder()
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
    }

    suspend inline fun <reified T> request(
        url: String,
        crossinline get: (ResponseBody, Headers) -> T,
    ) = withContext(Dispatchers.IO) {
        runRequest(get = get) {
            val client = createOkHttpClient()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .build()

            client.newCall(request).execute()
        }
    }

    suspend fun requestString(url: String) =
        request(
            url = url,
            get = { body, _ ->
                NetworkPolicy.readUtf8Bounded(body, NetworkPolicy.MAX_REPOSITORY_JSON_BYTES, url)
            },
        )

    @OptIn(ExperimentalStdlibApi::class)
    suspend inline fun <reified T> requestJson(url: String): Result<T> {
        val result =
            request(url) { body, _ ->
                val adapter =
                    Moshi
                        .Builder()
                        .build()
                        .adapter<T>()

                adapter.fromJson(NetworkPolicy.readUtf8Bounded(body, NetworkPolicy.MAX_REPOSITORY_JSON_BYTES, url))
            }

        if (result.isSuccess) {
            val json = result.getOrThrow()
            if (json != null) return Result.success(json)
        }

        return Result.failure(IllegalArgumentException())
    }

    suspend fun downloader(
        url: String,
        output: OutputStream,
        onProgress: (Float) -> Unit,
    ) = request(url) { body, headers ->
        val buffer = ByteArray(2048)
        val input = body.byteStream()

        val all = body.contentLength()
        require(NetworkPolicy.declaredDownloadLengthAllowed(all)) {
            "Download exceeds the ${NetworkPolicy.MAX_DOWNLOAD_BYTES} byte safety limit"
        }
        var finished = 0L
        var readying: Int

        while (input.read(buffer).also { readying = it } != -1) {
            if (readying == 0) continue
            finished = NetworkPolicy.addReceivedBytes(finished, readying)
            output.write(buffer, 0, readying)

            val progress = if (all > 0L) (finished * 1.0 / all).toFloat() else -1f
            onProgress(progress)
        }

        output.flush()
        output.close()
        input.close()

        return@request headers
    }
}
