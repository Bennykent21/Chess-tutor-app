package com.chesstutor.app.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OnlineStockfishEngineClient(
    private val fallback: EngineClient = LocalFallbackEngineClient()
) : EngineClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() {
        fallback.initialize()
    }

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(Dispatchers.IO) {
        val targetDepth = (request.depth ?: 10).coerceIn(6, 15)

        // Strategy 1: stockfish.online (fast GET API)
        try {
            val encodedFen = URLEncoder.encode(request.fen, "UTF-8")
            val url = "https://stockfish.online/api/s/v2.php?fen=$encodedFen&depth=$targetDepth"
            val httpRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "ChessTutorAndroid/2.0")
                .get()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optBoolean("success", false)) {
                        val evalDouble = if (json.isNull("evaluation")) null else json.optDouble("evaluation")
                        val mateVal = if (json.isNull("mate")) null else json.optInt("mate")
                        val rawBestMove = json.optString("bestmove", "")
                        val bestMoveUci = parseBestMoveUci(rawBestMove)
                        val continuationStr = json.optString("continuation", "")
                        val pvList = if (continuationStr.isNotBlank()) {
                            continuationStr.trim().split(Regex("\\s+"))
                        } else emptyList()

                        if (bestMoveUci.isNotBlank() && bestMoveUci != "0000") {
                            val cp = evalDouble?.let { (it * 100).toInt() }
                            logD("stockfish.online success: bestMove=$bestMoveUci, cp=$cp, mate=$mateVal")
                            return@withContext PositionAnalysis(
                                requestId = request.requestId,
                                bestMoveUci = bestMoveUci,
                                centipawns = cp,
                                mateInMoves = mateVal,
                                principalVariation = if (pvList.isNotEmpty()) pvList else listOf(bestMoveUci),
                                depth = targetDepth
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logW("stockfish.online failed: ${e.message}, attempting secondary API...")
        }

        // Strategy 2: chess-api.com (POST API)
        try {
            val jsonBody = JSONObject().apply {
                put("fen", request.fen)
                put("depth", targetDepth)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)
            val httpRequest = Request.Builder()
                .url("https://chess-api.com/v1")
                .header("User-Agent", "ChessTutorAndroid/2.0")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val move = json.optString("move", "")
                    val cp = if (json.isNull("centipawns")) {
                        if (json.isNull("eval")) null else (json.optDouble("eval") * 100).toInt()
                    } else json.optInt("centipawns")
                    val mate = if (json.isNull("mate")) null else json.optInt("mate")

                    val pv = mutableListOf<String>()
                    if (move.isNotBlank()) pv.add(move)
                    val contArr = json.optJSONArray("continuationArr")
                    if (contArr != null) {
                        for (i in 0 until contArr.length()) {
                            pv.add(contArr.optString(i))
                        }
                    }

                    if (move.isNotBlank()) {
                        logD("chess-api.com success: bestMove=$move, cp=$cp, mate=$mate")
                        return@withContext PositionAnalysis(
                            requestId = request.requestId,
                            bestMoveUci = move,
                            centipawns = cp,
                            mateInMoves = mate,
                            principalVariation = pv,
                            depth = targetDepth
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logW("chess-api.com failed: ${e.message}, falling back to local engine...")
        }

        // Strategy 3: Reliable Local Engine Fallback (Offline safe)
        logI("Using offline local chess engine fallback for analysis")
        fallback.analyze(request)
    }

    override suspend fun stop() {
        fallback.stop()
    }

    override suspend fun dispose() {
        fallback.dispose()
    }

    private fun parseBestMoveUci(raw: String): String {
        val trimmed = raw.trim()
        val parts = trimmed.split(Regex("\\s+"))
        return if (parts.size >= 2 && parts[0] == "bestmove") {
            parts[1]
        } else if (parts.isNotEmpty()) {
            parts[0]
        } else ""
    }

    companion object {
        private const val TAG = "OnlineStockfish"

        private fun logD(msg: String) {
            try { Log.d(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }

        private fun logW(msg: String) {
            try { Log.w(TAG, msg) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        }

        private fun logI(msg: String) {
            try { Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }
    }
}
