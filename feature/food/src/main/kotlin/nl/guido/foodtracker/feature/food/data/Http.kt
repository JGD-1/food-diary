package nl.guido.foodtracker.feature.food.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** A web answer: status code and text. */
internal data class HttpResponse(val code: Int, val body: String)

/** The few web calls this module makes, behind an interface so tests can fake them. */
internal interface Http {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

/** Plain Android HttpURLConnection, so we need no extra library. Throws IOException when offline. */
internal class UrlConnectionHttp @Inject constructor() : Http {
    override suspend fun get(url: String, headers: Map<String, String>) = request("GET", url, null, headers)

    override suspend fun postJson(url: String, json: String, headers: Map<String, String>) =
        request("POST", url, json, headers + ("Content-Type" to "application/json"))

    private suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>) =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        }
}
