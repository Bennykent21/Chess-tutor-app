package com.chesstutor.app.domain.update

import com.squareup.moshi.Moshi

/** A published release the app can update to (D88): its version and where its signed APK is. */
data class AppRelease(val version: String, val apkUrl: String, val apkBytes: Long, val sha256Url: String?)

/**
 * In-app updates from the project's GitHub releases (D88). No server of our own: the app asks
 * GitHub for the latest release, downloads its APK and hands it to Android's installer.
 */
object AppUpdates {
    const val LATEST_URL = "https://api.github.com/repos/Bennykent21/Chess-tutor-app/releases/latest"

    /** At most one check a day, so the app never nags GitHub (60 requests an hour without an account). */
    const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000

    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    /** GitHub's "latest release" answer, or null when it has no APK or no version tag. */
    fun parseLatest(json: String): AppRelease? {
        val root = runCatching { adapter.fromJson(json) as? Map<*, *> }.getOrNull() ?: return null
        if (root["draft"] == true || root["prerelease"] == true) return null
        val version = (root["tag_name"] as? String)?.removePrefix("v")?.takeIf { VERSION.matches(it) } ?: return null
        val assets = (root["assets"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
        val apk = assets.firstOrNull { (it["name"] as? String)?.endsWith(".apk") == true } ?: return null
        val apkName = apk["name"] as String
        val sha = assets.firstOrNull { it["name"] == "$apkName.sha256" }
        return AppRelease(
            version = version,
            apkUrl = apk["browser_download_url"] as? String ?: return null,
            apkBytes = (apk["size"] as? Number)?.toLong() ?: 0L,
            sha256Url = sha?.get("browser_download_url") as? String,
        )
    }

    /** True when [candidate] ("1.2.0") is a later version than [current]; anything unreadable is not. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /** The hash in a `sha256sum` line ("<64 hex>  name.apk"), lower case, or null. */
    fun parseSha256(text: String): String? = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { SHA.matches(it) }

    /** "95 MB": what a download costs, rounded up. */
    fun megabytes(bytes: Long): String = "${(bytes + 999_999) / 1_000_000} MB"

    private fun parts(version: String): List<Int>? = version.removePrefix("v").takeIf { VERSION.matches(it) }?.split('.')?.map(String::toInt)

    private val VERSION = Regex("""\d+\.\d+\.\d+""")
    private val SHA = Regex("[0-9a-f]{64}")
}
