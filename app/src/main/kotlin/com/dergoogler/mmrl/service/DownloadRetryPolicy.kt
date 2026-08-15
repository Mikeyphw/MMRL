package com.dergoogler.mmrl.service

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Conservative retry classification: only transport/transient HTTP failures are automatic-retry candidates. */
object DownloadRetryPolicy {
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is DownloadHttpException -> error.code == 408 || error.code == 425 || error.code == 429 || error.code in 500..599
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException, is UnknownHostException -> true
        else -> false
    }
}

class DownloadHttpException(
    val code: Int,
    message: String,
) : java.io.IOException(message)
