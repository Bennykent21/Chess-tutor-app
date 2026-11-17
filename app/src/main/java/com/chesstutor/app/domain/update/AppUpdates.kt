package com.chesstutor.app.domain.update

import com.squareup.moshi.Moshi

/**
 * A published release the app can update to (D88): its version, where its signed APK is, and the
 * notes of every version since the one installed, newest first.
 */
data class AppRelease(
    val version: String,
    val apkUrl: String,
    val apkBytes: Long,
    val sha256Url: String?,
    val notes: List<ReleaseNotes> = emptyList(),
)

/** One version's notes, from its CHANGELOG section: an optional opening line, then headed lists. */
data class ReleaseNotes(val version: String, val intro: String?, val sections: List<NotesSection>)

data class NotesSection(val title: String, val items: List<String>)

/**
 * In-app updates from the project's GitHub releases (D88). No server of our own: the app asks
 * GitHub for the latest release, downloads its APK and hands it to Android's installer.
 */
object AppUpdates {
    const val RELEASES_URL = "https://api.github.com/repos/Bennykent21/Chess-tutor-app/releases?per_page=10"

    /** Each start asks again, but not within 15 minutes (GitHub allows 60 requests an hour without an account). */
    const val CHECK_EVERY_MS = 15L * 60 * 1000

    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    /**
     * GitHub's list of releases: the newest one with an APK and a version tag, carrying the notes
     * of every version newer than [current]; null when there is none or the answer is unreadable.
     */
    fun parseReleases(json: String, current: String): AppRelease? {
        val list = runCatching { adapter.fromJson(json) as? List<*> }.getOrNull() ?: return null
        val releases = list.mapNotNull { (it as? Map<*, *>)?.let(::releaseOf) }.sortedWith { a, b -> compare(b.first.version, a.first.version) }
        val newest = releases.firstOrNull()?.first ?: return null
        return newest.copy(notes = releases.filter { isNewer(it.first.version, current) }.map { it.second })
    }

    /** A release and its notes, or null for drafts, pre-releases and releases without an APK. */
    private fun releaseOf(root: Map<*, *>): Pair<AppRelease, ReleaseNotes>? {
        if (root["draft"] == true || root["prerelease"] == true) return null
        val version = (root["tag_name"] as? String)?.removePrefix("v")?.takeIf { VERSION.matches(it) } ?: return null
        val assets = (root["assets"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
        val apk = assets.firstOrNull { (it["name"] as? String)?.endsWith(".apk") == true } ?: return null
        val apkName = apk["name"] as String
        val sha = assets.firstOrNull { it["name"] == "$apkName.sha256" }
        val release = AppRelease(
            version = version,
            apkUrl = apk["browser_download_url"] as? String ?: return null,
            apkBytes = (apk["size"] as? Number)?.toLong() ?: 0L,
            sha256Url = sha?.get("browser_download_url") as? String,
        )
        return release to parseNotes(version, root["body"] as? String ?: "")
    }

    /**
     * A release's notes from its body (the CHANGELOG section the release job copies): "### Title"
     * starts a section, "- item" adds to it, and a plain line before the first section is the
     * opening line. The checksum line and Markdown marks are left out.
     */
    fun parseNotes(version: String, body: String): ReleaseNotes {
        var intro: String? = null
        val sections = mutableListOf<NotesSection>()
        for (raw in body.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("SHA-256") -> Unit
                line.startsWith("#") -> sections += NotesSection(plain(line.trimStart('#')), emptyList())
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val item = plain(line.drop(2))
                    if (sections.isEmpty()) sections += NotesSection("", emptyList())
                    sections[sections.lastIndex] = sections.last().let { it.copy(items = it.items + item) }
                }
                sections.isEmpty() && intro == null -> intro = plain(line)
            }
        }
        return ReleaseNotes(version, intro, sections.filter { it.items.isNotEmpty() })
    }

    private fun plain(text: String) = text.replace("**", "").replace("`", "").trim()

    /** True when [candidate] ("1.2.0") is a later version than [current]; anything unreadable is not. */
    fun isNewer(candidate: String, current: String): Boolean {
        if (parts(candidate) == null || parts(current) == null) return false
        return compare(candidate, current) > 0
    }

    private fun compare(a: String, b: String): Int {
        val x = parts(a) ?: return -1
        val y = parts(b) ?: return 1
        for (i in 0 until 3) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /** The hash in a `sha256sum` line ("<64 hex>  name.apk"), lower case, or null. */
    fun parseSha256(text: String): String? = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { SHA.matches(it) }

    /** "95 MB": what a download costs, rounded up. */
    fun megabytes(bytes: Long): String = "${(bytes + 999_999) / 1_000_000} MB"

    private fun parts(version: String): List<Int>? = version.removePrefix("v").takeIf { VERSION.matches(it) }?.split('.')?.map(String::toInt)

    private val VERSION = Regex("""\d+\.\d+\.\d+""")
    private val SHA = Regex("[0-9a-f]{64}")
}
