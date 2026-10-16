package com.chesstutor.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.puzzle.PuzzleAssetLoader
import com.chesstutor.app.data.puzzle.SharedPrefsPuzzleProgressStore
import com.chesstutor.app.domain.puzzle.PuzzlePhase
import com.chesstutor.app.domain.puzzle.PuzzleScreenState
import com.chesstutor.app.domain.puzzle.PuzzleTrainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connects [PuzzleTrainer] (the rules of the Puzzles tab) to the screen. It only adds the short
 * pauses that make puzzles feel natural: showing the opponent's set-up move, their reply after a
 * correct move, clearing a mistake, and playing out the solution.
 */
class PuzzleViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SharedPrefsPuzzleProgressStore(application)
    private val _state = MutableStateFlow(PuzzleScreenState())
    val state: StateFlow<PuzzleScreenState> = _state.asStateFlow()

    private var trainer: PuzzleTrainer? = null
    private var started = false
    private var pacingJob: Job? = null

    /** Safe to call on every recomposition; only the first call does anything. */
    fun start(seedRating: Int) {
        if (started) return
        started = true
        viewModelScope.launch {
            val pack = PuzzleAssetLoader(getApplication()).load()
            val t = PuzzleTrainer(pack, store.load(seedRating), store)
            trainer = t
            publish()
            if (!pack.isEmpty) stageNext()
        }
    }

    fun onSquareTapped(square: String) {
        val t = trainer ?: return
        t.onSquareTapped(square)
        afterPlayerAction(t)
    }

    fun choosePromotion(piece: Char) {
        val t = trainer ?: return
        t.choosePromotion(piece)
        afterPlayerAction(t)
    }

    fun cancelPromotion() {
        trainer?.cancelPromotion()
        publish()
    }

    fun hint() {
        trainer?.hint()
        publish()
    }

    fun reveal() {
        val t = trainer ?: return
        pacingJob?.cancel()
        val moves = t.reveal()
        publish()
        pacingJob = viewModelScope.launch {
            for (move in moves) {
                delay(REVEAL_STEP_MS)
                t.applyRevealMove(move)
                publish()
            }
        }
    }

    fun skip() {
        val t = trainer ?: return
        t.markSkipped()
        stageNext()
    }

    fun next() = stageNext()

    fun setTheme(theme: String?) {
        val t = trainer ?: return
        t.setTheme(theme)
        stageNext()
    }

    // ---------- pacing ----------

    private fun afterPlayerAction(t: PuzzleTrainer) {
        publish()
        when (t.state.phase) {
            PuzzlePhase.REPLYING -> {
                pacingJob?.cancel()
                pacingJob = viewModelScope.launch {
                    delay(REPLY_DELAY_MS)
                    t.showReply()
                    publish()
                }
            }
            PuzzlePhase.WRONG -> {
                pacingJob?.cancel()
                pacingJob = viewModelScope.launch {
                    delay(WRONG_DELAY_MS)
                    t.clearWrong()
                    publish()
                }
            }
            else -> Unit
        }
    }

    /** Loads the next puzzle, shows the position before the opponent's move, then plays it. */
    private fun stageNext() {
        val t = trainer ?: return
        pacingJob?.cancel()
        if (!t.loadNext()) {
            publish()
            return
        }
        publish()
        pacingJob = viewModelScope.launch {
            delay(SETUP_DELAY_MS)
            t.beginSolving()
            publish()
        }
    }

    private fun publish() {
        trainer?.let { _state.value = it.state }
    }

    private companion object {
        const val SETUP_DELAY_MS = 600L
        const val REPLY_DELAY_MS = 550L
        const val WRONG_DELAY_MS = 900L
        const val REVEAL_STEP_MS = 800L
    }
}
