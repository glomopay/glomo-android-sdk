package com.glomopay.sdk.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Native equivalent of the Flutter API client used for order detection. */
public class GlomoPayApiClient public constructor(
    private val publicKey: String,
    private val devMode: Boolean = false,
) {
    public suspend fun fetchOrder(orderId: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val url = URL("https://api.glomopay.com/api/public/v1/order/$orderId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $publicKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (status == HttpURLConnection.HTTP_OK) {
                JSONObject(body).toMap()
            } else {
                throw IOException("Failed to load order. Status: $status, Body: $body")
            }
        } catch (error: Exception) {
            if (devMode) error.printStackTrace()
            throw IOException("Network error fetching order: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
        when (val value = get(key)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            else -> value
        }
    }

    private fun JSONArray.toList(): List<Any?> = (0 until length()).map { index ->
        when (val value = get(index)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            else -> value
        }
    }
}
