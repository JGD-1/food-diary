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
}

internal class UrlConnectionWebClient @Inject constructor() : WebClient {
    override suspend fun get(url: String, headers: Map<String, String>) =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                val code = connection.responseCode
                val stream = if (code < 400) connection.inputStream else connection.errorStream
                WebResponse(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                connection.disconnect()
            }
        }
}
