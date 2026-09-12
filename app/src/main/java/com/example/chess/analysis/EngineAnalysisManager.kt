package com.example.chess.analysis

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.engine.Evaluation
import com.example.chess.engine.LocalChessEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GameAnalysisState {
  object Idle : GameAnalysisState
  data class Analyzing(val progress: Float, val currentMove: Int, val totalMoves: Int) : GameAnalysisState
  data class Ready(
    val analyzedMoves: List<AnalyzedMove>,
    val whiteAccuracy: Float,
    val blackAccuracy: Float
  ) : GameAnalysisState
  data class Error(val message: String) : GameAnalysisState
}

/**
 * Manages background engine game analysis with robust cancellation and loading states,
 * accurately determining the engine's true best move alternative for every position.
 */
class EngineAnalysisManager(
  private val engine: LocalChessEngine = LocalChessEngine()
) {
  private var analysisJob: Job? = null

  private val _state = MutableStateFlow<GameAnalysisState>(GameAnalysisState.Idle)
  val state: StateFlow<GameAnalysisState> = _state.asStateFlow()

  fun cancel() {
    analysisJob?.cancel()
    analysisJob = null
    _state.value = GameAnalysisState.Idle
  }

  fun startAnalysis(scope: CoroutineScope, parsedGame: ParsedPgnGame) {
    analysisJob?.cancel()

    val moves = parsedGame.moves
    if (moves.isEmpty()) {
      _state.value = GameAnalysisState.Ready(emptyList(), 100f, 100f)
      return
    }

    analysisJob = scope.launch {
      try {
        _state.value = GameAnalysisState.Analyzing(0f, 0, moves.size)

        val analyzedList = mutableListOf<AnalyzedMove>()
        var posBefore = Position.fromFen(Position.STARTING_FEN)
        var prevEval = Evaluation.cp(engine.evaluateStatic(posBefore))

        for (i in moves.indices) {
          val m = moves[i]
          val posAfter = m.positionAfter
          val curCp = engine.evaluateStatic(posAfter)
          val curEval = Evaluation.cp(curCp)
          val playerColor = posBefore.sideToMove
          val isBook = i < 6

          // Calculate engine's ACTUAL best move alternative
          val (engineBestMove, _) = withContext(Dispatchers.Default) {
            engine.findBestMove(posBefore, depth = 2)
          }

          val isMatch = m.move == engineBestMove
          val classification = if (isBook) {
            MoveClassification.BOOK
          } else {
            BlunderClassifier.classify(playerColor, prevEval, curEval, isBestMove = isMatch)
          }

          val explanation = BlunderClassifier.generateExplanation(
            playerColor = playerColor,
            move = m.move,
            positionBefore = posBefore,
            positionAfter = posAfter,
            classification = classification,
            bestAlternative = engineBestMove
          )

          analyzedList.add(
            AnalyzedMove(
              moveIndex = i,
              move = m.move,
              playerColor = playerColor,
              positionBefore = posBefore,
              positionAfter = posAfter,
              evalBefore = prevEval,
              evalAfter = curEval,
              bestMove = engineBestMove,
              classification = classification,
              explanation = explanation
            )
          )

          posBefore = posAfter
          prevEval = curEval

          _state.value = GameAnalysisState.Analyzing(
            progress = (i + 1).toFloat() / moves.size,
            currentMove = i + 1,
            totalMoves = moves.size
          )
        }

        val whiteAcc = BlunderClassifier.calculateAccuracy(analyzedList, PieceColor.WHITE)
        val blackAcc = BlunderClassifier.calculateAccuracy(analyzedList, PieceColor.BLACK)

        _state.value = GameAnalysisState.Ready(
          analyzedMoves = analyzedList,
          whiteAccuracy = whiteAcc,
          blackAccuracy = blackAcc
        )
      } catch (e: CancellationException) {
        // Coroutine cancelled normally
      } catch (e: Exception) {
        _state.value = GameAnalysisState.Error(e.message ?: "Analysis failed")
      }
    }
  }
}
