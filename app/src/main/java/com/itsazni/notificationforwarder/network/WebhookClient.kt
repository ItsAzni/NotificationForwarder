package com.itsazni.notificationforwarder.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.itsazni.notificationforwarder.data.QueueItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        queryParams: Map<String, String>,
        payloadTemplate: String,
        item: QueueItem,
        deviceId: String
    ): SendResult {
        return try {
            val vars = mapOf(
                "deviceId" to deviceId,
                "packageName" to escapeJson(item.packageName),
                "appName" to escapeJson(item.appName),
                "title" to escapeJson(item.title),
                "text" to escapeJson(item.text),
                "postedAt" to item.postedAt.toString(),
                "notificationKey" to escapeJson(item.notificationKey)
            )

            val finalUrl = buildUrl(url, queryParams)
            val bodyJson = if (payloadTemplate.isBlank()) {
                gson.toJson(
                    mapOf(
                        "deviceId" to deviceId,
                        "packageName" to item.packageName,
                        "appName" to item.appName,
                        "title" to item.title,
                        "text" to item.text,
                        "postedAt" to item.postedAt,
                        "notificationKey" to item.notificationKey
                    )
                )
            } else {
                renderTemplate(payloadTemplate, vars)
            }

            val isGet = method.equals("GET", ignoreCase = true)
            val requestBuilder = Request.Builder()
                .url(finalUrl)

            if (isGet) {
                requestBuilder.get()
            } else {
                val contentType = headers["Content-Type"] ?: "application/json"
                requestBuilder.method(method.uppercase(), bodyJson.toRequestBody(contentType.toMediaType()))
            }

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

    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl
        val builder = httpUrl.newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun renderTemplate(template: String, vars: Map<String, String>): String {
        var result = template
        vars.forEach { (k, v) ->
            result = result.replace("{$k}", v)
        }
        // validate JSON to catch syntax errors early
        JsonParser.parseString(result)
        return result
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
