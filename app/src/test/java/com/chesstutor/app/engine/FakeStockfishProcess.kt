package com.chesstutor.app.engine

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A scripted stand-in for the Stockfish process, so the process client can be tested on
 * the JVM. [script] runs for every command the client sends and answers with [emit].
 */
class FakeStockfishProcess(
    private val script: FakeStockfishProcess.(command: String) -> Unit = FakeStockfishProcess::defaultScript,
) : Process() {
    val commands = CopyOnWriteArrayList<String>()
    val goCount = AtomicInteger(0)

    @Volatile
    var exitCode: Int? = null
        private set

    private val stdout = QueueInputStream()
    private val stdin = LineOutputStream { line ->
        commands.add(line)
        if (line.startsWith("go")) goCount.incrementAndGet()
        if (exitCode == null) script(line)
    }

    fun emit(vararg lines: String) {
        if (exitCode != null) return
        lines.forEach { stdout.push("$it\n".toByteArray()) }
    }

    /** Ends the process as if it crashed or quit. */
    fun exit(code: Int) {
        if (exitCode != null) return
        exitCode = code
        stdout.endOfStream()
    }

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = stdout
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun waitFor(): Int {
        while (exitCode == null) Thread.sleep(5)
        return exitCode!!
    }
    override fun exitValue(): Int = exitCode ?: throw IllegalThreadStateException("still running")
    override fun destroy() = exit(143)

    fun defaultScript(command: String) {
        when {
            command == "uci" -> emit("id name Stockfish 19", "id author the Stockfish developers", "uciok")
            command == "isready" -> emit("readyok")
            command.startsWith("go") -> emit(
                "info string NNUE evaluation using nn-1a298aa575a0.nnue (60MiB, (22528, 1024, 15, 32, 1))",
                "info depth 12 seldepth 15 multipv 1 score cp 31 nodes 123456 nps 987654 time 125 pv e2e4 e7e5",
                "bestmove e2e4 ponder e7e5",
            )
            command == "quit" -> exit(0)
        }
    }

    /** Blocking byte queue with an end-of-stream marker. */
    private class QueueInputStream : InputStream() {
        private val queue = LinkedBlockingQueue<Int>()

        fun push(bytes: ByteArray) = bytes.forEach { queue.put(it.toInt() and 0xFF) }
        fun endOfStream() = queue.put(EOF)

        override fun read(): Int {
            val b = queue.take()
            if (b == EOF) {
                queue.put(EOF)
                return -1
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val first = read()
            if (first == -1) return -1
            b[off] = first.toByte()
            var count = 1
            while (count < len) {
                val next = queue.peek() ?: break
                if (next == EOF) break
                b[off + count] = queue.take().toByte()
                count++
            }
            return count
        }

        override fun available(): Int = queue.count { it != EOF }

        companion object {
            private const val EOF = -1
        }
    }

    /** Collects written bytes and hands over each complete line. */
    private class LineOutputStream(private val onLine: (String) -> Unit) : OutputStream() {
        private val buffer = StringBuilder()

        @Synchronized
        override fun write(b: Int) {
            val c = b.toChar()
            if (c == '\n') {
                val line = buffer.toString().trimEnd('\r')
                buffer.setLength(0)
                onLine(line)
            } else {
                buffer.append(c)
            }
        }
    }
}

/** Hands out fake processes in order and counts starts. */
class FakeLauncher(
    private val preflightError: String? = null,
    private val factory: () -> FakeStockfishProcess = { FakeStockfishProcess() },
) : NativeProcessLauncher {
    val started = CopyOnWriteArrayList<FakeStockfishProcess>()

    override fun preflight(path: String): String? = preflightError
    override fun sizeBytes(path: String): Long? = 64L * 1024 * 1024
    override fun start(path: String): Process = factory().also { started.add(it) }
}

/** Fallback engine that records calls and always answers the same thing. */
class RecordingFallback : EngineClient {
    val analyzed = CopyOnWriteArrayList<AnalysisRequest>()

    override suspend fun initialize() = Unit
    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
        analyzed.add(request)
        return PositionAnalysis(requestId = request.requestId, bestMoveUci = "fallback")
    }
    override suspend fun stop() = Unit
    override suspend fun dispose() = Unit
}
