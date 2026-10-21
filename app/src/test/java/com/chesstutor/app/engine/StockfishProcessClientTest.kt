package com.chesstutor.app.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockfishProcessClientTest {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

    private fun client(
        launcher: NativeProcessLauncher,
        fallback: EngineClient = RecordingFallback(),
        config: NativeEngineConfig = NativeEngineConfig(threads = 1, startupTimeoutMs = 2_000L),
    ) = StockfishProcessEngineClient("/fake/libstockfish.so", fallback, launcher, config)

    @Test
    fun healthyEngineAnswersNativelyAndReportsRealTelemetry() = runBlocking {
        val launcher = FakeLauncher()
        val engine = client(launcher)

        val result = engine.analyze(AnalysisRequest(requestId = 1, fen = startFen, movetimeMs = 100))

        assertEquals(EngineSource.NATIVE, result.source)
        assertEquals("e2e4", result.bestMoveUci)
        assertEquals(31, result.centipawns)
        assertEquals(NativeEngineState.READY, engine.status.value.nativeState)
        assertFalse(engine.status.value.isDegraded)

        val report = engine.healthCheck(100)
        assertEquals(EngineHealthReport.Verdict.HEALTHY, report.verdict)
        assertEquals("Stockfish 19", report.telemetry.engineIdName)
        assertEquals(987_654L, report.telemetry.lastNps)
        assertTrue(report.telemetry.networkInfo!!.startsWith("nn-1a298aa575a0.nnue"))
        assertTrue(report.toReportText().contains("987,654 nodes/sec"))
        // One process served both requests.
        assertEquals(1, launcher.started.size)
    }

    @Test
    fun blackToMoveScoreIsNormalizedToWhitesPerspective() = runBlocking {
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go")) {
                    emit("info depth 10 score cp 25 nodes 1000 nps 50000 pv e7e5", "bestmove e7e5")
                } else {
                    defaultScript(command)
                }
            }
        }
        val result = client(launcher).analyze(AnalysisRequest(requestId = 2, fen = afterE4, movetimeMs = 50))
        assertEquals("e7e5", result.bestMoveUci)
        assertEquals(-25, result.centipawns)
    }

    @Test
    fun positionsStockfishWouldRejectNeverReachIt() = runBlocking {
        // White to move while Black's king is already in check: Stockfish 19 would terminate.
        val sideNotToMoveInCheck = "4k3/4R3/8/8/8/8/8/4K3 w - - 0 1"
        val launcher = FakeLauncher()
        val fallback = RecordingFallback()
        val engine = client(launcher, fallback)

        val result = engine.analyze(AnalysisRequest(requestId = 3, fen = sideNotToMoveInCheck, movetimeMs = 50))

        assertEquals(EngineSource.LITE, result.source)
        assertEquals(1, fallback.analyzed.size)
        val sentPositions = launcher.started.flatMap { it.commands }.filter { it.startsWith("position") }
        assertTrue("Rejected FEN must not be sent: $sentPositions", sentPositions.isEmpty())
    }

    @Test
    fun crashDuringSearchFallsBackThenDisablesAfterTwoFailures() = runBlocking {
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go")) {
                    emit("info string CRITICAL ERROR: Command `go` failed. Reason: test crash")
                    exit(1)
                } else {
                    defaultScript(command)
                }
            }
        }
        val fallback = RecordingFallback()
        val engine = client(launcher, fallback)

        val first = engine.analyze(AnalysisRequest(requestId = 10, fen = startFen, movetimeMs = 50))
        assertEquals(EngineSource.LITE, first.source)
        assertEquals("fallback", first.bestMoveUci)
        assertEquals(NativeEngineState.FAILED, engine.status.value.nativeState)
        assertTrue(engine.status.value.problem!!.contains("CRITICAL ERROR"))

        engine.analyze(AnalysisRequest(requestId = 11, fen = startFen, movetimeMs = 50))
        assertEquals(2, launcher.started.size)
        assertEquals(NativeEngineState.DISABLED, engine.status.value.nativeState)

        // Given up for the session: no third process.
        val third = engine.analyze(AnalysisRequest(requestId = 12, fen = startFen, movetimeMs = 50))
        assertEquals(2, launcher.started.size)
        assertEquals(EngineSource.LITE, third.source)
        assertTrue(engine.status.value.isDegraded)
        assertEquals(2, engine.currentTelemetry().unexpectedExits)
    }

    @Test
    fun engineWithoutItsNetworkIsReportedWithStockfishsOwnReason() = runBlocking {
        // Exactly what the old Stockfish 16.1 build did: start fine, then exit on "go".
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go")) {
                    emit(
                        "info string ERROR: Network evaluation parameters compatible with the engine must be available.",
                        "info string ERROR: The network file nn-b1a57edbea57.nnue was not loaded successfully.",
                        "info string ERROR: The engine will be terminated now.",
                    )
                    exit(1)
                } else {
                    defaultScript(command)
                }
            }
        }
        val engine = client(launcher)

        val report = engine.healthCheck(50)

        assertEquals(EngineHealthReport.Verdict.USING_FALLBACK, report.verdict)
        assertTrue(report.problem!!.contains("Network evaluation parameters"))
        assertTrue(report.toReportText().contains("NOT working"))
    }

    @Test
    fun missingBinaryDisablesNativeWithoutTryingToStart() = runBlocking {
        val launcher = FakeLauncher(preflightError = "Stockfish binary not found at /fake")
        val engine = client(launcher)

        val result = engine.analyze(AnalysisRequest(requestId = 20, fen = startFen, movetimeMs = 50))

        assertEquals(EngineSource.LITE, result.source)
        assertTrue(launcher.started.isEmpty())
        assertEquals(NativeEngineState.DISABLED, engine.status.value.nativeState)
        val report = engine.healthCheck(50)
        assertEquals(EngineHealthReport.Verdict.USING_FALLBACK, report.verdict)
        assertTrue(report.problem!!.contains("not found"))
    }

    @Test
    fun startupThatNeverAnswersTimesOutInsteadOfHanging() = runBlocking {
        val launcher = FakeLauncher { FakeStockfishProcess { /* never answers anything */ } }
        val engine = client(launcher, config = NativeEngineConfig(threads = 1, startupTimeoutMs = 300L))

        val started = System.currentTimeMillis()
        engine.initialize()
        val elapsed = System.currentTimeMillis() - started

        assertTrue("initialize took $elapsed ms", elapsed < 3_000L)
        assertEquals(NativeEngineState.FAILED, engine.status.value.nativeState)
        assertTrue(engine.lastStartupError!!.contains("uci"))
    }

    @Test
    fun crashDuringStartupCountsAsOneFailure() = runBlocking {
        val launcher = FakeLauncher { FakeStockfishProcess { command -> if (command == "uci") exit(1) } }
        val engine = client(launcher)

        engine.initialize()

        assertEquals(NativeEngineState.FAILED, engine.status.value.nativeState)
    }

    @Test
    fun checkmatedPositionReturnsNoMoveWithoutWaitingForTimeout() = runBlocking {
        // Fool's mate: White is checkmated.
        val mated = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go")) {
                    emit("info depth 0 score mate 0", "bestmove (none)")
                } else {
                    defaultScript(command)
                }
            }
        }
        val started = System.currentTimeMillis()
        val result = client(launcher).analyze(AnalysisRequest(requestId = 30, fen = mated, movetimeMs = 50))

        assertEquals("0000", result.bestMoveUci)
        assertEquals(EngineSource.NATIVE, result.source)
        assertTrue(System.currentTimeMillis() - started < 2_000L)
    }

    @Test
    fun lateBestMoveFromATimedOutSearchCannotAnswerTheNextRequest() = runBlocking {
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                when {
                    command.startsWith("go") && goCount.get() == 1 -> Unit // first search hangs
                    command.startsWith("go") -> emit(
                        "bestmove d2d4", // late answer to the abandoned first search
                        "info depth 9 score cp 12 nodes 500 nps 40000 pv e2e4",
                        "bestmove e2e4",
                    )
                    else -> defaultScript(command)
                }
            }
        }
        val fallback = RecordingFallback()
        val engine = client(launcher, fallback)

        val timedOut = engine.analyze(AnalysisRequest(requestId = 40, fen = startFen, movetimeMs = 10))
        assertEquals(EngineSource.LITE, timedOut.source)
        assertTrue(launcher.started.single().commands.contains("stop"))

        val next = engine.analyze(AnalysisRequest(requestId = 41, fen = startFen, movetimeMs = 10))
        assertEquals("e2e4", next.bestMoveUci)
        assertEquals(EngineSource.NATIVE, next.source)
    }

    @Test
    fun multiPvLinesAreSortedValidatedAndNormalized() = runBlocking {
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go")) {
                    emit(
                        "info depth 14 multipv 2 score cp 10 nodes 9000 nps 90000 pv c7c5 g1f3",
                        "info depth 14 multipv 1 score cp 20 nodes 9000 nps 90000 pv e7e5 g1f3",
                        "info depth 14 multipv 3 score cp 5 nodes 9000 nps 90000 pv e7e6 d2d4",
                        "info depth 14 multipv 4 score cp 0 nodes 9000 nps 90000 pv e1e2",
                        "bestmove e7e5",
                    )
                } else {
                    defaultScript(command)
                }
            }
        }
        val engine = client(launcher)

        val result = engine.analyze(AnalysisRequest(requestId = 50, fen = afterE4, movetimeMs = 50, multiPv = 4))

        val sent = launcher.started.single().commands
        assertTrue(sent.contains("setoption name MultiPV value 4"))
        // The 4th line starts with an illegal move (a White move with Black to play): dropped.
        assertEquals(listOf(1, 2, 3), result.lines.map { it.rank })
        assertEquals(listOf("e7e5", "c7c5", "e7e6"), result.lines.map { it.firstMove })
        assertEquals(listOf(-20, -10, -5), result.lines.map { it.centipawns })

        engine.analyze(AnalysisRequest(requestId = 51, fen = afterE4, movetimeMs = 50))
        assertTrue(sent.contains("setoption name MultiPV value 1"))
    }

    @Test
    fun strengthIsAppliedOnlyToTheRequestThatAsksForIt() = runBlocking {
        val launcher = FakeLauncher()
        val engine = client(launcher)

        engine.analyze(AnalysisRequest(requestId = 60, fen = startFen, movetimeMs = 20, strengthElo = 1500))
        engine.analyze(AnalysisRequest(requestId = 61, fen = startFen, movetimeMs = 20))
        engine.analyze(AnalysisRequest(requestId = 62, fen = startFen, movetimeMs = 20, strengthElo = 800))

        val sent = launcher.started.single().commands
        val limitOn = sent.indexOf("setoption name UCI_LimitStrength value true")
        val elo = sent.indexOf("setoption name UCI_Elo value 1500")
        val fullStrength = sent.indexOf("setoption name Skill Level value 20")
        val skill = sent.indexOf("setoption name Skill Level value ${StockfishProcessEngineClient.skillLevelFor(800)}")
        assertTrue(limitOn in 0 until elo)
        assertTrue("Analysis after a bot move must return to full strength", fullStrength > elo)
        assertTrue(skill > fullStrength)
        assertEquals(2, StockfishProcessEngineClient.skillLevelFor(800))
    }

    @Test
    fun healthCheckRetriesAfterNativeWasDisabledByFailures() = runBlocking {
        var crash = true
        val launcher = FakeLauncher {
            FakeStockfishProcess { command ->
                if (command.startsWith("go") && crash) exit(1) else defaultScript(command)
            }
        }
        val engine = client(launcher)
        engine.analyze(AnalysisRequest(requestId = 70, fen = startFen, movetimeMs = 20))
        engine.analyze(AnalysisRequest(requestId = 71, fen = startFen, movetimeMs = 20))
        assertEquals(NativeEngineState.DISABLED, engine.status.value.nativeState)

        crash = false
        val report = engine.healthCheck(20)

        assertEquals(EngineSource.NATIVE, report.answeredBy)
        assertNotNull(report.bestMove)
        assertEquals(NativeEngineState.READY, engine.status.value.nativeState)
    }
}
