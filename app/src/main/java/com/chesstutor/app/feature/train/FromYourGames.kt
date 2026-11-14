package com.chesstutor.app.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.train.GameFinding
import com.chesstutor.app.feature.learn.BoardThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What "From your games" found, and in how many games it looked. */
data class FindingsReport(val games: Int = 0, val reviewed: Int = 0, val findings: List<GameFinding> = emptyList())

data class FindingsState(val loading: Boolean = true, val report: FindingsReport = FindingsReport())

/** Works the findings out from the learner's saved games, off the main thread, whenever asked again. */
class GameFindingsViewModel(private val load: suspend () -> FindingsReport) : ViewModel() {
    private val mutableState = MutableStateFlow(FindingsState())
    val state: StateFlow<FindingsState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /** Looks again, for games imported or reviewed since. */
    fun refresh() {
        viewModelScope.launch {
            val report = runCatching { withContext(Dispatchers.Default) { load() } }.getOrDefault(mutableState.value.report)
            mutableState.value = FindingsState(loading = false, report = report)
        }
    }

    class Factory(private val load: suspend () -> FindingsReport) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GameFindingsViewModel(load) as T
    }
}

/**
 * From your games (mock-up round 4): the learner's repeated mistakes as numbers, each with one
 * sentence, and "Train it" where Train has the drill for it.
 */
@Composable
fun FromYourGamesScreen(
    state: FindingsState,
    theme: BoardTheme,
    onTrainLine: (bookId: String, path: List<String>) -> Unit,
    onTrainEnding: (setId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("From your games", style = CtType.Display)
                    if (!state.loading) {
                        Text(
                            "${report.games} game${if (report.games == 1) "" else "s"}" + if (report.reviewed > 0) " · ${report.reviewed} reviewed" else "",
                            style = CtType.Caption.copy(color = CtColors.Text2),
                        )
                    }
                }
                if (!state.loading && report.findings.isEmpty()) {
                    Text(
                        if (report.games == 0) "Import your games in Account, or play a few here." else "Nothing comes back yet. A slip shows here once it repeats.",
                        style = CtType.Body.copy(color = CtColors.Text2),
                    )
                }
                report.findings.forEach { finding ->
                    when (finding) {
                        is GameFinding.LineSlip -> LineSlipCard(finding, theme, onTrain = { onTrainLine(finding.bookId, finding.path) })
                        is GameFinding.EndingResult -> EndingCard(finding, onTrain = { onTrainEnding(finding.kind.setId) })
                        is GameFinding.RepeatedMistake -> MistakeCard(finding)
                    }
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

@Composable
private fun LineSlipCard(slip: GameFinding.LineSlip, theme: BoardTheme, onTrain: () -> Unit) {
    CtCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                BoardThumbnail(slip.fen, theme, flipped = slip.side == PieceColor.BLACK, size = 76.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(slip.bookTitle + (slip.after?.let { " · after $it" } ?: ""), style = CtType.Caption.copy(color = CtColors.Text2))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        Text("${slip.misses}/${slip.reached}", style = CtType.BigNumber)
                        Text(
                            buildAnnotatedString {
                                append("you played ")
                                withStyle(SpanStyle(color = CtColors.Error, fontWeight = FontWeight.SemiBold)) { append(slip.played) }
                            },
                            style = CtType.BodySmall.copy(color = CtColors.Text2),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
            Text(
                buildAnnotatedString {
                    slip.note?.let { append("$it ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(slip.right) }
                    append(" is the line.")
                },
                style = CtType.BodySmall,
            )
            CtButton(text = "Train it", onClick = onTrain, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EndingCard(ending: GameFinding.EndingResult, onTrain: () -> Unit) {
    CtCard(Modifier.fillMaxWidth(), onClick = onTrain, onClickLabel = "Train ${ending.kind.title}") {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ending.kind.title, style = CtType.TitleSmall)
                Text("Won ${ending.won} of ${ending.reached} a pawn or more up", style = CtType.Caption.copy(color = CtColors.Text2))
            }
            CtProgressBar(progress = ending.won.toFloat() / ending.reached, color = CtColors.Success, height = 6.dp)
        }
    }
}

@Composable
private fun MistakeCard(mistake: GameFinding.RepeatedMistake) {
    CtCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(listOfNotNull(mistake.opening, mistake.after).joinToString(" · ").ifEmpty { mistake.played }, style = CtType.TitleSmall, modifier = Modifier.weight(1f, fill = false))
                Text("${mistake.times} of ${mistake.reached} games", style = CtType.Caption.copy(color = CtColors.Text2))
            }
            Text(
                "You played ${mistake.played}." + (mistake.better?.let { " $it was better." } ?: ""),
                style = CtType.Caption.copy(color = CtColors.Text2),
            )
        }
    }
}
