package com.chesstutor.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-app updates from GitHub releases (D88). */
class AppUpdatesTest {

    // The shape of GitHub's answer for the project's v1.0.0 release (trimmed).
    private val latest = """
        {"tag_name":"v1.2.0","draft":false,"prerelease":false,"name":"Chess Tutor 1.2.0",
         "assets":[
          {"name":"chess-tutor-1.2.0.apk","size":94947862,"browser_download_url":"https://github.com/Bennykent21/Chess-tutor-app/releases/download/v1.2.0/chess-tutor-1.2.0.apk"},
          {"name":"chess-tutor-1.2.0.apk.sha256","size":88,"browser_download_url":"https://github.com/Bennykent21/Chess-tutor-app/releases/download/v1.2.0/chess-tutor-1.2.0.apk.sha256"}
         ]}
    """.trimIndent()

    @Test
    fun theLatestReleaseGivesItsVersionApkAndChecksum() {
        val release = AppUpdates.parseLatest(latest)!!
        assertEquals("1.2.0", release.version)
        assertTrue(release.apkUrl.endsWith("/v1.2.0/chess-tutor-1.2.0.apk"))
        assertEquals(94_947_862L, release.apkBytes)
        assertTrue(release.sha256Url!!.endsWith(".apk.sha256"))
        assertEquals("95 MB", AppUpdates.megabytes(release.apkBytes))
    }

    @Test
    fun releasesWithoutAnApkOrAVersionTagAreIgnored() {
        assertNull(AppUpdates.parseLatest("""{"tag_name":"v1.2.0","assets":[]}"""))
        assertNull(AppUpdates.parseLatest(latest.replace("v1.2.0\",\"draft", "nightly\",\"draft")))
        assertNull(AppUpdates.parseLatest(latest.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(AppUpdates.parseLatest("not json"))
    }

    @Test
    fun versionsCompareByNumberNotByText() {
        assertTrue(AppUpdates.isNewer("1.10.0", "1.9.3"))
        assertTrue(AppUpdates.isNewer("v2.0.0", "1.99.99"))
        assertFalse(AppUpdates.isNewer("1.2.0", "1.2.0"))
        assertFalse(AppUpdates.isNewer("1.1.9", "1.2.0"))
        assertFalse("a test build is never offered an update", AppUpdates.isNewer("1.2.0", "0.1-local"))
    }

    @Test
    fun theChecksumIsReadFromASha256sumLine() {
        val hash = "a".repeat(64)
        assertEquals(hash, AppUpdates.parseSha256("$hash  chess-tutor-1.2.0.apk\n"))
        assertEquals(hash, AppUpdates.parseSha256(hash.uppercase()))
        assertNull(AppUpdates.parseSha256("not a hash"))
    }
}
