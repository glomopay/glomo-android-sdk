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
    @Suppress("UNUSED_PARAMETER")
    devMode: Boolean = false,
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
            val body = if (status == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            mapOrderFetchResponse(status, body)
        } catch (error: Exception) {
            throw IOException("Network error fetching order: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun orderFetchFailureMessage(status: Int): String =
    "Failed to load order. Status: $status"

internal fun mapOrderFetchResponse(status: Int, body: String): Map<String, Any?> {
    if (status != HttpURLConnection.HTTP_OK) {
        throw IOException(orderFetchFailureMessage(status))
    }
    return JSONObject(body).toOrderMap()
}

private fun JSONObject.toOrderMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toOrderMap()
        is JSONArray -> value.toOrderList()
        else -> value
    }
}

private fun JSONArray.toOrderList(): List<Any?> = (0 until length()).map { index ->
    when (val value = get(index)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toOrderMap()
        is JSONArray -> value.toOrderList()
        else -> value
    }
}
