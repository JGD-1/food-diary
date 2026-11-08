package nl.guido.foodtracker.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.time.LocalDate

class UpdateLogicTest {
    private val sha = "a3a9e1435fb4d55d1bd6de42ae332e0cdadb7be8a49cca17c81bd5e6b43c586c"

    // Exactly what tools/local/build.ps1 writes (PowerShell's ConvertTo-Json layout).
    private val versionJson = """
        {
            "versionCode":  301,
            "versionName":  "0.1.301",
            "apk":  "food-diary-0.1.301.apk",
            "sha256":  "$sha",
            "sizeBytes":  126504651,
            "commit":  "9e2866f",
            "builtAt":  "2026-11-08T14:46:01Z"
        }
    """.trimIndent()

    private val remote = RemoteVersion(301, "0.1.301", "food-diary-0.1.301.apk", sha, 126504651)

    @Test
    fun `reads the version file the PC writes`() {
        assertEquals(remote, UpdateLogic.parseVersion(versionJson))
        assertEquals(remote, UpdateLogic.parseVersion("﻿" + versionJson))
    }

    @Test
    fun `a wrong version file never offers an update`() {
        assertNull(UpdateLogic.parseVersion("<html>Not found</html>"))
        assertNull(UpdateLogic.parseVersion(versionJson.replace("food-diary-0.1.301.apk", "../secret.apk")))
        assertNull(UpdateLogic.parseVersion(versionJson.replace(sha, "1234")))
        assertNull(UpdateLogic.parseVersion(versionJson.replace("301,", "0,")))
        assertNull(UpdateLogic.parseVersion(versionJson.replace("126504651", "0")))
    }

    @Test
    fun `only a higher version number is newer`() {
        assertTrue(UpdateLogic.isNewer(300, remote))
        assertFalse(UpdateLogic.isNewer(301, remote))
        assertFalse(UpdateLogic.isNewer(302, remote))
        // GitHub's last builds were 0.1.18x/19x; local builds start at 300.
        assertTrue(UpdateLogic.isNewer(190, remote))
    }

    @Test
    fun `automatic check at most once a day`() {
        val today = LocalDate.of(2026, 11, 8)
        assertTrue(UpdateLogic.shouldAutoCheck(null, today))
        assertTrue(UpdateLogic.shouldAutoCheck(today.minusDays(1), today))
        assertFalse(UpdateLogic.shouldAutoCheck(today, today))
    }

    @Test
    fun `typed addresses`() {
        assertEquals(ServerAddress("192.168.178.67", 8787), UpdateLogic.parseAddress("192.168.178.67"))
        assertEquals(ServerAddress("192.168.178.67", 8787), UpdateLogic.parseAddress(" http://192.168.178.67:8787/ "))
        assertEquals(ServerAddress("192.168.1.20", 9000), UpdateLogic.parseAddress("192.168.1.20:9000"))
        assertEquals(ServerAddress("guidopc.fritz.box", 8787), UpdateLogic.parseAddress("guidopc.fritz.box"))
        assertNull(UpdateLogic.parseAddress(""))
        assertNull(UpdateLogic.parseAddress("192.168.1.20:99999"))
        assertNull(UpdateLogic.parseAddress("192.168.1.20:abc"))
        assertNull(UpdateLogic.parseAddress("my pc"))
        assertNull(UpdateLogic.parseAddress("https://example.com/x"))
        assertEquals("192.168.178.67", ServerAddress.DEFAULT.toString())
        assertEquals("192.168.1.20:9000", ServerAddress("192.168.1.20", 9000).toString())
    }

    @Test
    fun `only the home network`() {
        assertTrue(UpdateLogic.isHomeNetwork(InetAddress.getByName("192.168.178.67")))
        assertTrue(UpdateLogic.isHomeNetwork(InetAddress.getByName("10.0.0.5")))
        assertTrue(UpdateLogic.isHomeNetwork(InetAddress.getByName("172.16.3.4")))
        assertTrue(UpdateLogic.isHomeNetwork(InetAddress.getByName("127.0.0.1")))
        assertFalse(UpdateLogic.isHomeNetwork(InetAddress.getByName("8.8.8.8")))
        assertFalse(UpdateLogic.isHomeNetwork(InetAddress.getByName("172.32.0.1")))
    }

    @Test
    fun `the download is checked before installing`() {
        val app = "nl.guido.foodtracker"
        fun check(size: Long = remote.sizeBytes, hash: String = sha, pkg: String? = app, code: Long? = 301) =
            UpdateLogic.verify(remote, size, hash, pkg, code, app)
        assertEquals(VerifyResult.OK, check())
        assertEquals(VerifyResult.OK, check(hash = sha.uppercase()))
        assertEquals(VerifyResult.WRONG_SIZE, check(size = 10))
        assertEquals(VerifyResult.WRONG_CHECKSUM, check(hash = "0".repeat(64)))
        assertEquals(VerifyResult.NOT_THIS_APP, check(pkg = "com.example.other"))
        assertEquals(VerifyResult.NOT_THIS_APP, check(pkg = null))
        assertEquals(VerifyResult.WRONG_VERSION, check(code = 300))
    }

    @Test
    fun `sha256 as lowercase hex`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateLogic.sha256Hex("abc".toByteArray()),
        )
    }
}
