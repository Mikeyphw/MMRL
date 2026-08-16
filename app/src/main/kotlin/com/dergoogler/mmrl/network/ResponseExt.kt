package com.dergoogler.mmrl.network

inline fun <reified T> runRequest(run: () -> retrofit2.Response<T>): Result<T> =
    try {
        val response = run()
        if (response.isSuccessful) {
            val data = response.body()
            if (data != null) {
                Result.success(data)
            } else {
                Result.failure(NullPointerException("Empty response body"))
            }
        } else {
            val snippet = NetworkPolicy.readErrorSnippet(response.errorBody())
            Result.failure(
                NetworkHttpException(
                    statusCode = response.code(),
                    requestUrl = response.raw().request.url.toString(),
                    responseSnippet = snippet,
                ),
            )
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

inline fun <reified T> runRequest(
    get: (okhttp3.ResponseBody, okhttp3.Headers) -> T,
    run: () -> okhttp3.Response,
): Result<T> =
    try {
        val response = run()
        response.use {
            val body = it.body
            val headers = it.headers
            if (it.isSuccessful) {
                if (body != null) {
                    Result.success(get(body, headers))
                } else {
                    Result.failure(NullPointerException("Empty response body"))
                }
            } else {
                val snippet = NetworkPolicy.readErrorSnippet(body)
                Result.failure(
                    NetworkHttpException(
                        statusCode = it.code,
                        requestUrl = it.request.url.toString(),
                        responseSnippet = snippet,
                    ),
                )
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
