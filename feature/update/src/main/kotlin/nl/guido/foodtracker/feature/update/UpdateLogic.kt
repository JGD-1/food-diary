package nl.guido.foodtracker.feature.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.security.MessageDigest
import java.time.LocalDate

/** What the home update server says about the newest build (version.json, written by tools/local/build.ps1). */
@Serializable
data class RemoteVersion(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val sizeBytes: Long,
)

/** Where the home update server is: an address on the home network plus a port. */
data class ServerAddress(val host: String, val port: Int) {
    override fun toString() = if (port == DEFAULT_PORT) host else "$host:$port"

    companion object {
        const val DEFAULT_PORT = 8787
        /** Guido's PC on 8 Nov 2026; changeable in Profile > Updates when the PC gets another address. */
        val DEFAULT = ServerAddress("192.168.178.67", DEFAULT_PORT)
    }
}

/** Pure rules for the updater, kept apart from Android so they can be tested. */
object UpdateLogic {
    private val json = Json { ignoreUnknownKeys = true }
    private val apkName = Regex("""food-diary-0\.1\.\d+\.apk""")
    private val sha = Regex("[0-9a-f]{64}")
    private val hostName = Regex("""[A-Za-z0-9]([A-Za-z0-9-]{0,62})(\.[A-Za-z0-9]([A-Za-z0-9-]{0,62}))*""")

    /** Reads version.json; null when it isn't what build.ps1 writes (so a wrong file never offers an update). */
    fun parseVersion(text: String): RemoteVersion? {
        val v = runCatching { json.decodeFromString(RemoteVersion.serializer(), text.trimStart('﻿')) }.getOrNull()
            ?: return null
        val ok = v.versionCode > 0 && v.versionName.isNotBlank() && apkName.matches(v.apk) &&
            sha.matches(v.sha256) && v.sizeBytes in 1..MAX_APK_BYTES
        return v.takeIf { ok }
    }

    fun isNewer(installedVersionCode: Long, remote: RemoteVersion): Boolean = remote.versionCode > installedVersionCode

    /** The automatic check runs at most once a day; "Zoek naar update" always checks. */
    fun shouldAutoCheck(lastCheck: LocalDate?, today: LocalDate): Boolean = lastCheck == null || lastCheck < today

    /**
     * What the person typed in Profile > Updates, e.g. "192.168.178.67" or "192.168.178.67:8787" or "guidopc.fritz.box".
     * Null when it can't be a server address. Whether it is really on the home network is checked when connecting.
     */
    fun parseAddress(typed: String): ServerAddress? {
        val text = typed.trim().removePrefix("http://").trimEnd('/')
        if (text.isEmpty() || text.length > 253) return null
        val host = text.substringBefore(':')
        val portText = text.substringAfter(':', "")
        val port = if (portText.isEmpty()) ServerAddress.DEFAULT_PORT else portText.toIntOrNull() ?: return null
        if (port !in 1..65535 || !hostName.matches(host)) return null
        return ServerAddress(host, port)
    }

    /** Only home-network addresses (and the phone itself): the updater never fetches from the internet. */
    fun isHomeNetwork(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Checks done on the downloaded file before Android's install screen is opened. */
    fun verify(
        remote: RemoteVersion,
        downloadedBytes: Long,
        downloadedSha256: String,
        apkPackage: String?,
        apkVersionCode: Long?,
        appPackage: String,
    ): VerifyResult = when {
        downloadedBytes != remote.sizeBytes -> VerifyResult.WRONG_SIZE
        !downloadedSha256.equals(remote.sha256, ignoreCase = true) -> VerifyResult.WRONG_CHECKSUM
        apkPackage != appPackage -> VerifyResult.NOT_THIS_APP
        apkVersionCode != remote.versionCode.toLong() -> VerifyResult.WRONG_VERSION
        else -> VerifyResult.OK
    }

    const val MAX_APK_BYTES = 400L * 1024 * 1024
}

enum class VerifyResult { OK, WRONG_SIZE, WRONG_CHECKSUM, NOT_THIS_APP, WRONG_VERSION }
