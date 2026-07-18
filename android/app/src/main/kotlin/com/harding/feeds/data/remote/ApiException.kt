package com.harding.feeds.data.remote

import java.io.IOException
import retrofit2.Response

/**
 * Non-2xx API result. Extends IOException so the sync engine's "is this retryable" handling
 * can treat network failures and server errors through one catch, distinguishing on [code].
 */
class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")

fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw ApiException(code(), errorBody()?.string().orEmpty())
    return body() ?: throw ApiException(code(), "Expected a body but got none")
}
