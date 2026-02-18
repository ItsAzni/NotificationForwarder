package com.itsazni.notificationforwarder.network

import com.google.gson.Gson
import com.itsazni.notificationforwarder.data.QueueItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class SendResult(
    val success: Boolean,
    val isPermanentFailure: Boolean,
    val message: String
)

class WebhookClient {
    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    fun send(
        url: String,
        method: String,
        headers: Map<String, String>,
        item: QueueItem,
        deviceId: String
    ): SendResult {
        return try {
            val payload = mapOf(
                "deviceId" to deviceId,
                "packageName" to item.packageName,
                "appName" to item.appName,
                "title" to item.title,
                "text" to item.text,
                "postedAt" to item.postedAt,
                "notificationKey" to item.notificationKey
            )
            val bodyJson = gson.toJson(payload)
            val requestBuilder = Request.Builder()
                .url(url)
                .method(method, bodyJson.toRequestBody("application/json".toMediaType()))

            headers.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    SendResult(true, false, "OK")
                } else {
                    val permanent = response.code in 400..499 && response.code != 429
                    SendResult(false, permanent, "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            SendResult(false, false, e.message ?: "network error")
        }
    }
}
