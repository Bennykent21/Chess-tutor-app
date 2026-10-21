package com.chesstutor.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFenGateTest {

    @Test
    fun acceptsNormalPositions() {
        assertNull(EngineFenGate.rejectionReason("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertNull(EngineFenGate.rejectionReason("4k3/8/8/8/8/8/8/4K2R w K - 0 1"))
        // The side TO move may be in check.
        assertNull(EngineFenGate.rejectionReason("4k3/4R3/8/8/8/8/8/4K3 b - - 0 1"))
    }

    @Test
    fun rejectsWhatStockfish19WouldTerminateOn() {
        assertNotNull(EngineFenGate.rejectionReason("not a fen"))
        assertNotNull(EngineFenGate.rejectionReason("4k3/4R3/8/8/8/8/8/4K3 w - - 0 1"))
        assertNotNull(
            EngineFenGate.rejectionReason(
                "k7/pppppppp/pppppppp/pppppppp/PPPPPPPP/PPPPPPPP/PPPPPPPP/K7 w - - 0 1"
            )
        )
        assertNotNull(EngineFenGate.rejectionReason("4k3/8/8/8/8/8/8/4K3 w - - 40000 1"))
        assertNotNull(EngineFenGate.rejectionReason("4k3/8/8/8/8/8/8/4K3 w - - 0 60000"))
        assertFalse(EngineFenGate.isAcceptable("8/8/8/8/8/8/8/4K3 w - - 0 1"))
    }
}

class UciProtocolExtendedTest {

    @Test
    fun parsesSpeedNodesAndMultiPv() {
        val info = UciProtocol.parseInfoLine(
            "info depth 20 seldepth 28 multipv 2 score cp 15 nodes 2500000 nps 1250000 hashfull 120 time 2000 pv d2d4 g8f6"
        )!!
        assertEquals(2, info.multiPv)
        assertEquals(2_500_000L, info.nodes)
        assertEquals(1_250_000L, info.nps)
        assertEquals(2_000L, info.timeMs)
        assertEquals(20, info.depth) // "seldepth" must not be mistaken for depth
        assertFalse(info.isBound)
    }

    @Test
    fun marksBoundScores() {
        assertTrue(UciProtocol.parseInfoLine("info depth 8 score cp 40 lowerbound nodes 10 pv e2e4")!!.isBound)
    }

    @Test
    fun infoStringsAreTextNotSearchData() {
        val line = "info string NNUE evaluation using nn-1a298aa575a0.nnue (60MiB)"
        assertNull(UciProtocol.parseInfoLine(line))
        assertEquals("NNUE evaluation using nn-1a298aa575a0.nnue (60MiB)", UciProtocol.parseInfoString(line))
        assertNull(UciProtocol.parseInfoString("info depth 3"))
    }

    @Test
    fun bestMoveLineDistinguishesNoMoveFromNotABestMove() {
        assertEquals(UciBestMove("e2e4"), UciProtocol.parseBestMoveLine("bestmove e2e4 ponder e7e5"))
        assertEquals(UciBestMove(null), UciProtocol.parseBestMoveLine("bestmove (none)"))
        assertNull(UciProtocol.parseBestMoveLine("readyok"))
        assertNull(UciProtocol.parseBestMoveLine("bestmovement e2e4"))
    }
}

class EngineHealthReportTest {

    private fun report(source: EngineSource, network: String?) = EngineHealthReport(
        answeredBy = source,
        nativeState = NativeEngineState.READY,
        telemetry = EngineTelemetry(engineIdName = "Stockfish 19", networkInfo = network, lastNps = 1_234_567),
        bestMove = "e2e4",
        centipawns = 30,
        depth = 18,
        elapsedMs = 1000,
        problem = null,
    )

    @Test
    fun verdictReflectsWhoAnsweredAndWhetherTheNetworkLoaded() {
        assertEquals(EngineHealthReport.Verdict.HEALTHY, report(EngineSource.NATIVE, "nn-x.nnue").verdict)
        assertEquals(
            EngineHealthReport.Verdict.NATIVE_WITHOUT_NETWORK_REPORT,
            report(EngineSource.NATIVE, null).verdict
        )
        assertEquals(EngineHealthReport.Verdict.USING_FALLBACK, report(EngineSource.LITE, null).verdict)
        assertEquals(EngineHealthReport.Verdict.USING_FALLBACK, report(EngineSource.CLOUD, null).verdict)
    }

    @Test
    fun reportTextUsesTheEnginesOwnNumbers() {
        val text = report(EngineSource.NATIVE, "nn-x.nnue").toReportText()
        assertTrue(text.contains("1,234,567 nodes/sec"))
        assertTrue(text.contains("Engine: Stockfish 19"))
        assertTrue(text.startsWith(EngineHealthReport.Verdict.HEALTHY.headline))
    }

    @Test
    fun statusIsDegradedWhenASubstituteAnswers() {
        assertFalse(EngineStatus().isDegraded)
        assertFalse(EngineStatus(NativeEngineState.READY, EngineSource.NATIVE).isDegraded)
        assertTrue(EngineStatus(NativeEngineState.READY, EngineSource.LITE).isDegraded)
        assertTrue(EngineStatus(NativeEngineState.DISABLED, EngineSource.UNKNOWN).isDegraded)
    }

    @Test
    fun serviceTurnsASingleAnswerIntoOneLine() {
        val single = PositionAnalysis(requestId = 1, bestMoveUci = "e2e4", centipawns = 20, principalVariation = listOf("e2e4", "e7e5"))
        assertEquals(listOf("e2e4", "e7e5"), ManagerEngineService.linesOf(single).single().moves)
        assertTrue(ManagerEngineService.linesOf(PositionAnalysis(requestId = 1, bestMoveUci = "0000")).isEmpty())
        val multi = single.copy(lines = listOf(EngineLine(1, listOf("d2d4")), EngineLine(2, listOf("c2c4"))))
        assertEquals(2, ManagerEngineService.linesOf(multi).size)
    }
}
