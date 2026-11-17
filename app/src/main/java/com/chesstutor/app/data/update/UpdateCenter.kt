package com.chesstutor.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.chesstutor.app.BuildConfig
import com.chesstutor.app.data.network.FallbackDns
import com.chesstutor.app.domain.update.AppRelease
import com.chesstutor.app.domain.update.AppUpdates
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where an update stands, for the prompt, the Home icon, What's new and the Settings row. */
sealed interface UpdateState {
    /** The version waiting to be installed, if any. */
    val release: AppRelease? get() = null

    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(override val release: AppRelease) : UpdateState
    data class Downloading(override val release: AppRelease, val done: Long, val total: Long) : UpdateState {
        val progress: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }
    data class Ready(override val release: AppRelease, val file: File) : UpdateState
    /** Android has to be told once that Chess Tutor may install its own updates. */
    data class NeedsPermission(override val release: AppRelease, val file: File) : UpdateState
    data class Failed(override val release: AppRelease?, val message: String) : UpdateState
}

/**
 * In-app updates (D88, D90). Only builds made from a version tag check (they are signed with the
 * release key, so the new APK installs over them); test builds never do. Checks each time the app
 * comes to the front (not within [AppUpdates.CHECK_EVERY_MS]), prompts once per version, downloads
 * into the app's cache, checks the SHA-256 the release publishes, then opens Android's installer,
 * where the player confirms.
 */
class UpdateCenter(
    context: Context,
    private val scope: CoroutineScope,
    val enabled: Boolean = BuildConfig.UPDATES_FROM_GITHUB,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(FallbackDns())
        .build(),
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val folder = File(app.cacheDir, "updates")

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** True while the centred prompt should show. */
    private val mutablePrompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = mutablePrompt.asStateFlow()

    private var download: Job? = null

    /**
     * Each time the app comes to the front: asks GitHub unless it did in the last few minutes
     * (a fresh start always asks), and prompts once per new version.
     */
    fun checkIfDue() {
        if (!enabled) return
        val now = state.value
        if (now is UpdateState.Checking || now is UpdateState.Downloading) return
        if (now != UpdateState.Idle && clock() - prefs.getLong(LAST_CHECK, 0L) < AppUpdates.CHECK_EVERY_MS) return
        check()
    }

    /** The Settings row without a known update: asks GitHub now, and says what it found. */
    fun checkNow() {
        if (!enabled || state.value == UpdateState.Checking) return
        check(always = true)
    }

    /** What's new was opened: the prompt has done its job for this version. */
    fun seen() {
        state.value.release?.let { prefs.edit().putString(DISMISSED, it.version).apply() }
        mutablePrompt.value = false
    }

    /** Asks GitHub; a version already found stays shown (no flicker) unless a newer one replaces it. */
    private fun check(always: Boolean = false) {
        val known = state.value.release
        if (known == null) mutableState.value = UpdateState.Checking
        scope.launch {
            val release = latest()
            prefs.edit().putLong(LAST_CHECK, clock()).apply()
            when {
                release == null -> if (known == null) {
                    mutableState.value = if (always) UpdateState.Failed(null, "Couldn't reach GitHub. Check your internet and try again.") else UpdateState.Idle
                }
                !AppUpdates.isNewer(release.version, currentVersion) -> {
                    mutableState.value = UpdateState.UpToDate
                    clearOldDownloads()
                }
                release.version == known?.version -> Unit
                else -> {
                    val file = apkFile(release)
                    mutableState.value = if (file.exists()) UpdateState.Ready(release, file) else UpdateState.Available(release)
                    // The centred prompt comes once per version; the Home icon stays until it is installed.
                    if (always || release.version != prefs.getString(DISMISSED, null)) mutablePrompt.value = true
                }
            }
            if (always && mutableState.value is UpdateState.Failed) mutablePrompt.value = true
        }
    }

    /** "Later": the prompt stays away for this version; the Home icon and Settings still offer it. */
    fun later() {
        val release = state.value.release
        if (release != null) prefs.edit().putString(DISMISSED, release.version).apply()
        mutablePrompt.value = false
        if (state.value is UpdateState.Failed) mutableState.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
    }

    /** Hides the prompt while a download carries on. */
    fun hide() {
        mutablePrompt.value = false
    }

    /** "Update": downloads (or resumes the finished file), then installs. */
    fun update(context: Context) {
        when (val now = state.value) {
            is UpdateState.Ready -> install(context, now.release, now.file)
            is UpdateState.NeedsPermission -> install(context, now.release, now.file)
            is UpdateState.Available -> startDownload(context, now.release)
            is UpdateState.Failed -> now.release?.let { startDownload(context, it) } ?: check(always = true)
            else -> Unit
        }
    }

    private fun startDownload(context: Context, release: AppRelease) {
        if (download?.isActive == true) return
        mutableState.value = UpdateState.Downloading(release, 0, release.apkBytes)
        download = scope.launch {
            val file = apkFile(release)
            val ok = runCatching { fetch(release, file) }.getOrElse { e -> if (e is CancellationException) throw e else false }
            if (ok) {
                mutableState.value = UpdateState.Ready(release, file)
                // Straight on to Android's installer when the prompt is still open.
                if (prompt.value) withContext(Dispatchers.Main) { install(context, release, file) }
            } else {
                mutableState.value = UpdateState.Failed(release, "The download didn't finish. Check your internet and try again.")
                mutablePrompt.value = true
            }
        }
    }

    private fun install(context: Context, release: AppRelease, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            mutableState.value = UpdateState.NeedsPermission(release, file)
            mutablePrompt.value = true
            return
        }
        mutableState.value = UpdateState.Ready(release, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { mutableState.value = UpdateState.Failed(release, "Android couldn't open the installer.") }
    }

    /** Android's page where Chess Tutor may be allowed to install updates. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(AppUpdates.RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()?.let { AppUpdates.parseReleases(it, currentVersion) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads [release] into [file] through a temporary file, checking its SHA-256 when published. */
    private suspend fun fetch(release: AppRelease, file: File): Boolean = withContext(Dispatchers.IO) {
        folder.mkdirs()
        folder.listFiles()?.filter { it != file }?.forEach { it.delete() }
        val expected = release.sha256Url?.let { url ->
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.let(AppUpdates::parseSha256) else null
            } ?: return@withContext false
        }
        val partial = File(folder, file.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(release.apkUrl).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            val body = response.body ?: return@withContext false
            if (!response.isSuccessful) return@withContext false
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkBytes
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - shown >= 512 * 1024) {
                            shown = done
                            mutableState.update { UpdateState.Downloading(release, done, total) }
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected != null && actual != expected) {
            partial.delete()
            return@withContext false
        }
        partial.renameTo(file)
    }

    private fun apkFile(release: AppRelease) = File(folder, "chess-tutor-${release.version}.apk")

    /** Once installed (or older than what runs), a downloaded APK is only taking space. */
    private fun clearOldDownloads() {
        scope.launch(Dispatchers.IO) { folder.listFiles()?.forEach { it.delete() } }
    }

    private companion object {
        const val LAST_CHECK = "last_check"
        const val DISMISSED = "dismissed_version"
        val USER_AGENT = "ChessTutorAndroid/${BuildConfig.VERSION_NAME} (+https://github.com/Bennykent21/Chess-tutor-app)"
    }
}
