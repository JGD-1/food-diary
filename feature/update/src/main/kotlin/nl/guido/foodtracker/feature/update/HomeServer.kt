package nl.guido.foodtracker.feature.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Talks to the home update server on the PC (tools/local/serve.py) with plain HTTP over the home network.
 *
 * Why a small socket client instead of HttpURLConnection: Android's network security config can only allow
 * plain HTTP for addresses fixed at build time, and the PC's address can change (it's set in Profile > Updates).
 * The rest of the app keeps Android's default (HTTPS only). This client refuses any address that isn't on the
 * home network, only reads (GET), and every downloaded APK is checked against version.json (size, SHA-256,
 * package and version) before Android's own install screen opens; Android also checks it's signed with the same key.
 */
@Singleton
class HomeServer @Inject constructor() {

    suspend fun fetchVersion(server: ServerAddress): RemoteVersion = withContext(Dispatchers.IO) {
        val body = java.io.ByteArrayOutputStream()
        get(server, "/version.json", maxBytes = 64 * 1024) { input, _ -> input.copyTo(body) }
        UpdateLogic.parseVersion(body.toString(Charsets.UTF_8.name())) ?: throw UpdateException(UpdateProblem.BAD_VERSION_FILE)
    }

    /** Downloads [remote]'s APK into [target]; returns its SHA-256. [onProgress] gets 0..1. */
    suspend fun download(server: ServerAddress, remote: RemoteVersion, target: File, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                get(server, "/${remote.apk}", maxBytes = UpdateLogic.MAX_APK_BYTES) { input, length ->
                    val total = (length ?: remote.sizeBytes).coerceAtLeast(1)
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            with(UpdateLogic) { digest.digest().toHex() }
        }

    private suspend fun get(server: ServerAddress, path: String, maxBytes: Long, read: suspend (InputStream, Long?) -> Unit) {
        val address = runCatching { InetAddress.getByName(server.host) }.getOrNull()
            ?: throw UpdateException(UpdateProblem.NOT_FOUND)
        if (!UpdateLogic.isHomeNetwork(address)) throw UpdateException(UpdateProblem.NOT_HOME_NETWORK)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, server.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                val request = "GET $path HTTP/1.0\r\nHost: ${server.host}\r\nUser-Agent: FoodDiary-updater\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(request.toByteArray(Charsets.US_ASCII)); flush() }
                val input = BufferedInputStream(socket.getInputStream())
                val status = readLine(input)
                val code = status.split(' ').getOrNull(1)?.toIntOrNull()
                if (code != 200) throw UpdateException(UpdateProblem.NOT_ON_SERVER)
                var length: Long? = null
                while (true) {
                    val line = readLine(input)
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toLongOrNull()
                }
                if (length != null && length > maxBytes) throw UpdateException(UpdateProblem.BAD_VERSION_FILE)
                read(LimitedInput(input, length ?: maxBytes), length)
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(UpdateProblem.NOT_REACHABLE, e)
        }
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < 8 * 1024) {
            val c = input.read()
            if (c < 0) break
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
        return line.toString().trimEnd('\r')
    }

    /** Never reads more than the server said (or than the limit). */
    private class LimitedInput(private val input: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int = if (left <= 0) -1 else input.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 15_000
    }
}

enum class UpdateProblem { NOT_FOUND, NOT_HOME_NETWORK, NOT_REACHABLE, NOT_ON_SERVER, BAD_VERSION_FILE, CHECK_FAILED }

class UpdateException(val problem: UpdateProblem, cause: Throwable? = null) : Exception(problem.name, cause)
