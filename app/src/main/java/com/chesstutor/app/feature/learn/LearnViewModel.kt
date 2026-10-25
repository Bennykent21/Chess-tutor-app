package com.chesstutor.app.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The lessons and the player's progress through them. */
data class LearnState(
    val loading: Boolean = true,
    val library: LessonLibrary = LessonLibrary.EMPTY,
    val progress: Map<String, LessonProgress> = emptyMap(),
)

/**
 * The Learn tab's view model (plan section 7.3): loads the bundled lessons once, follows
 * lesson progress, and runs the lesson player. Shared by every screen in the tab.
 */
class LearnViewModel(
    private val progress: LessonProgressRepository,
    private val loadLibrary: suspend () -> LessonLibrary,
) : ViewModel() {
    private val library = MutableStateFlow<LessonLibrary?>(null)

    val learn: StateFlow<LearnState> = combine(library, progress.observe()) { lib, rows ->
        LearnState(loading = lib == null, library = lib ?: LessonLibrary.EMPTY, progress = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnState())

    private val mutablePlayer = MutableStateFlow<LessonPlayer?>(null)
    val player: StateFlow<LessonPlayer?> = mutablePlayer.asStateFlow()

    init {
        viewModelScope.launch { library.value = loadLibrary() }
    }

    /**
     * Opens [lessonId] in the player, resuming at the saved step unless the lesson is finished
     * or [fromStart] is set.
     */
    fun openLesson(lessonId: String, fromStart: Boolean = false) {
        if (mutablePlayer.value?.lesson?.id == lessonId && !fromStart) return
        mutablePlayer.value = null
        viewModelScope.launch {
            val lib = library.filterNotNull().first()
            val lesson = lib.lesson(lessonId) ?: return@launch
            val course = lib.courseOf(lessonId) ?: return@launch
            val saved = progress.get(lessonId)
            val resumeAt = if (fromStart || saved == null || saved.completed) 0 else saved.stepIndex
            mutablePlayer.value = LessonPlayer(
                course = course,
                lesson = lesson,
                progress = progress,
                scope = viewModelScope,
                startStep = resumeAt.coerceIn(0, lesson.steps.lastIndex),
                nextLesson = lib.nextAfter(lessonId),
            ).also { it.start() }
        }
    }

    fun closePlayer() {
        mutablePlayer.value = null
    }

    class Factory(
        private val progress: LessonProgressRepository,
        private val loadLibrary: suspend () -> LessonLibrary,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LearnViewModel(progress, loadLibrary) as T
    }
}
