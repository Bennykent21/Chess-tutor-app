package com.chesstutor.app.data.network

import com.chesstutor.app.domain.account.CloudEvals
import com.chesstutor.app.domain.account.PlyEval
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lichess's free cloud evaluations (no account needed), asked one at a time as Lichess asks. */
class LichessCloudEvals(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .dns(FallbackDns())
        .build(),
) : CloudEvals {
    override suspend fun lookup(ply: Int, fen: String): PlyEval? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://lichess.org/api/cloud-eval?fen=${URLEncoder.encode(fen, "UTF-8")}&multiPv=2")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()?.let { CloudEvals.parseLichess(ply, fen, it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val USER_AGENT = "ChessTutorAndroid/${com.chesstutor.app.BuildConfig.VERSION_NAME} (+https://github.com/Bennykent21/Chess-tutor-app)"
    }
}
