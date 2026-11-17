package com.chesstutor.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-app updates from GitHub releases (D88) and their notes (D90). */
class AppUpdatesTest {

    private fun release(version: String, body: String, draft: Boolean = false, prerelease: Boolean = false, apk: Boolean = true): String {
        val url = "https://github.com/Bennykent21/Chess-tutor-app/releases/download/v$version/chess-tutor-$version.apk"
        val assets = if (!apk) "" else
            """{"name":"chess-tutor-$version.apk","size":94947862,"browser_download_url":"$url"},""" +
                """{"name":"chess-tutor-$version.apk.sha256","size":88,"browser_download_url":"$url.sha256"}"""
        val json = body.replace("\n", "\\n")
        return """{"tag_name":"v$version","draft":$draft,"prerelease":$prerelease,"body":"$json","assets":[$assets]}"""
    }

    // The shape of GitHub's list of releases (trimmed), newest first, with the bodies the release job writes.
    private val releases = "[" + listOf(
        release("1.3.0", "Testing", prerelease = true),
        release("1.2.0", "\n### Game review\n\n- Accuracy closer to chess.com.\n- **Faster** reviews.\n\nSHA-256: `abc`\n"),
        release("1.1.1", "\n### Play\n\n- Chess960.\n"),
        release("1.1.0", "\n### Learn\n\n- Courses.\n"),
        release("1.0.9", "No APK", apk = false),
    ).joinToString(",") + "]"

    @Test
    fun theNewestReleaseCarriesTheNotesOfEveryVersionSinceTheInstalledOne() {
        val release = AppUpdates.parseReleases(releases, current = "1.1.0")!!
        assertEquals("1.2.0", release.version)
        assertTrue(release.apkUrl.endsWith("/v1.2.0/chess-tutor-1.2.0.apk"))
        assertEquals(94_947_862L, release.apkBytes)
        assertTrue(release.sha256Url!!.endsWith(".apk.sha256"))
        assertEquals("95 MB", AppUpdates.megabytes(release.apkBytes))
        assertEquals(listOf("1.2.0", "1.1.1"), release.notes.map { it.version })
        assertEquals(NotesSection("Game review", listOf("Accuracy closer to chess.com.", "Faster reviews.")), release.notes.first().sections.single())
    }

    @Test
    fun draftsPreReleasesAndReleasesWithoutAnApkAreIgnored() {
        assertEquals("1.2.0", AppUpdates.parseReleases(releases, current = "1.2.0")!!.version)
        assertTrue("nothing newer, no notes", AppUpdates.parseReleases(releases, current = "1.2.0")!!.notes.isEmpty())
        assertNull(AppUpdates.parseReleases("[" + release("1.2.0", "", apk = false) + "]", current = "1.0.0"))
        assertNull(AppUpdates.parseReleases("[" + release("1.2.0", "", draft = true) + "]", current = "1.0.0"))
        assertNull(AppUpdates.parseReleases("not json", current = "1.0.0"))
    }

    @Test
    fun notesKeepTheOpeningLineAndHeadedListsWithoutTheChecksum() {
        val notes = AppUpdates.parseNotes("1.0.0", "\nThe first public release.\n\n### Play\n\n- 12 bots.\n- Clocks.\n\n### Empty\n\nSHA-256: `c70c`\n")
        assertEquals("The first public release.", notes.intro)
        assertEquals(listOf(NotesSection("Play", listOf("12 bots.", "Clocks."))), notes.sections)
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
