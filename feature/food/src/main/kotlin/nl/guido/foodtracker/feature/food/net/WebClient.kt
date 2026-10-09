package nl.guido.foodtracker.feature.food.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class WebResponse(val code: Int, val body: String)

/** A tiny web client, so tests can swap it out. Throws IOException when offline. */
interface WebClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): WebResponse
    suspend fun postJson(url: String, headers: Map<String, String>, json: String): WebResponse
}

internal class UrlConnectionWebClient @Inject constructor() : WebClient {
    override suspend fun get(url: String, headers: Map<String, String>) = send("GET", url, headers, null)

    override suspend fun postJson(url: String, headers: Map<String, String>, json: String) =
        send("POST", url, headers, json)

    private suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?) =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = connection.responseCode
                val stream = if (code < 400) connection.inputStream else connection.errorStream
                WebResponse(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                connection.disconnect()
            }
        }
}
