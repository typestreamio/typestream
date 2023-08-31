package io.typestream.http

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val okHttpClient = OkHttpClient()

object HttpClient {
    fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()
        return response.body?.string() ?: "{}"
    }

    // post with body
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Content-Type", "application/json")

        headers.forEach(request::addHeader)

        val response = okHttpClient.newCall(request.build()).execute()
        return response.body?.string() ?: "{}"
    }
}
