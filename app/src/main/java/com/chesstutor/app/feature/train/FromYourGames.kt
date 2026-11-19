package com.chesstutor.app.feature.train

import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.domain.train.OpeningRecord
import com.chesstutor.app.design.CtTopBar
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
data class FindingsReport(
    val games: Int = 0,
    val reviewed: Int = 0,
    val findings: List<GameFinding> = emptyList(),
    /** The learner's taught openings, with results and how well they keep to the line (D103). */
    val openings: List<OpeningRecord> = emptyList(),
    /** What the reviewed games of each clock say, for Stats (D104). */
    val insights: Map<com.chesstutor.app.domain.account.TimeClass, List<com.chesstutor.app.domain.account.ReviewedGame>> = emptyMap(),
) {
    /** The learner's own opening moves that were the line's, while the book covered the game; null without any. */
    val onLineShare: Double? get() = openings.sumOf { it.bookMoves }.takeIf { it > 0 }?.let { openings.sumOf { o -> o.onLine }.toDouble() / it }
}

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
 * From your games (D103, mock-up round 5): up to three numbers, then each taught opening with its
 * results bar and where the learner leaves the line, endings reached a pawn up, and mistakes made
 * twice or more. Everything to fix has its own Train button.
 */
@Composable
fun FromYourGamesScreen(
    state: FindingsState,
    theme: BoardTheme,
    onTrainLine: (bookId: String, path: List<String>) -> Unit,
    onTrainEnding: (setId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Plays a repeated mistake's position against Stockfish; null hides those buttons. */
    onTrainPosition: ((fen: String) -> Unit)? = null,
) {
    val report = state.report
    val slips = report.findings.filterIsInstance<GameFinding.LineSlip>()
    val endings = report.findings.filterIsInstance<GameFinding.EndingResult>()
    val mistakes = report.findings.filterIsInstance<GameFinding.RepeatedMistake>()
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(
                title = "From your games",
                onBack = onBack,
                subtitle = if (state.loading) null else "${report.games} game${if (report.games == 1) "" else "s"}" + if (report.reviewed > 0) " · ${report.reviewed} reviewed" else "",
            )
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                if (!state.loading && report.findings.isEmpty() && report.openings.isEmpty()) {
                    Text(
                        if (report.games == 0) "Import your games in Account, or play a few here." else "Nothing comes back yet. A slip shows here once it repeats.",
                        style = CtType.Body.copy(color = CtColors.Text2),
                    )
                }
                val numbers = listOfNotNull(
                    report.onLineShare?.let { "${(it * 100).roundToInt()}%" to "opening moves on line" },
                    endings.takeIf { it.isNotEmpty() }?.let { "${it.sumOf { e -> e.won }}/${it.sumOf { e -> e.reached }}" to "endings won a pawn up" },
                    mistakes.takeIf { it.isNotEmpty() }?.let { "${it.size}" to if (it.size == 1) "repeated mistake" else "repeated mistakes" },
                )
                if (numbers.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        // Three slots of one size, whatever shows in them.
                        numbers.forEach { (value, label) -> NumberBox(value, label, Modifier.weight(1f)) }
                        repeat(3 - numbers.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (report.openings.isNotEmpty()) {
                    CtGroupLabel("Openings")
                    report.openings.forEach { record ->
                        OpeningCard(record, slips.filter { it.bookId == record.bookId }.take(MAX_SLIPS), theme, onTrainLine)
                    }
                }
                if (endings.isNotEmpty()) {
                    CtGroupLabel("Endings")
                    endings.forEach { ending -> EndingCard(ending, onTrain = { onTrainEnding(ending.kind.setId) }) }
                }
                if (mistakes.isNotEmpty()) {
                    CtGroupLabel("Mistakes that repeat")
                    mistakes.forEach { mistake -> MistakeCard(mistake, theme, onTrain = onTrainPosition?.let { train -> { train(mistake.fen) } }) }
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

private const val MAX_SLIPS = 3

/** One number and what it counts. */
@Composable
private fun NumberBox(value: String, label: String, modifier: Modifier) {
    Column(modifier.height(96.dp).clip(CtShapes.CardSmall).background(CtColors.Raised).padding(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = CtType.Headline)
        Text(label, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 2)
    }
}

/** A taught opening: its games, the results bar, then each place the learner leaves the line, or how well they keep to it. */
@Composable
private fun OpeningCard(record: OpeningRecord, slips: List<GameFinding.LineSlip>, theme: BoardTheme, onTrain: (String, List<String>) -> Unit) {
    CtCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
                Text("${if (record.side == PieceColor.WHITE) "as White" else "as Black"} · ${record.games} game${if (record.games == 1) "" else "s"}", style = CtType.Caption.copy(color = CtColors.Text2))
            }
            ResultsBar(record)
            if (slips.isEmpty()) {
                Text("On your line in ${record.keptLine} of ${record.games} games.", style = CtType.BodySmall.copy(color = CtColors.Text2))
            }
            slips.forEach { slip ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    BoardThumbnail(slip.fen, theme, flipped = slip.side == PieceColor.BLACK, size = 64.dp)
                    Text(
                        buildAnnotatedString {
                            slip.after?.let { append("After $it you played ") } ?: append("You played ")
                            withStyle(SpanStyle(color = CtColors.Error, fontWeight = FontWeight.SemiBold)) { append(slip.played) }
                            append(" in ${slip.misses} of ${slip.reached} games. ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(slip.right) }
                            append(" is your line.")
                        },
                        style = CtType.BodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    CtChip(text = "Train", onClick = { onTrain(slip.bookId, slip.path) })
                }
            }
        }
    }
}

/** Wins, draws and losses side by side, each as long as its share. */
@Composable
private fun ResultsBar(record: OpeningRecord) {
    val total = (record.wins + record.draws + record.losses).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CtShapes.Pill)
            .background(CtColors.Track)
            .semantics { contentDescription = "${record.wins} won, ${record.draws} drawn, ${record.losses} lost" },
    ) {
        listOf(record.wins to CtColors.Success, record.draws to CtColors.Text2, record.losses to CtColors.Error).forEach { (count, color) ->
            if (count > 0) Box(Modifier.weight(count.toFloat() / total).fillMaxHeight().background(color))
        }
        val unknown = total - record.wins - record.draws - record.losses
        if (unknown > 0) Spacer(Modifier.weight(unknown.toFloat() / total))
    }
}

@Composable
private fun EndingCard(ending: GameFinding.EndingResult, onTrain: () -> Unit) {
    CtCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ending.kind.title, style = CtType.TitleSmall)
                    Text("Won ${ending.won} of ${ending.reached} a pawn or more up", style = CtType.Caption.copy(color = CtColors.Text2))
                }
                CtChip(text = "Train", onClick = onTrain)
            }
            CtProgressBar(progress = ending.won.toFloat() / ending.reached, color = CtColors.Success, height = 6.dp)
        }
    }
}

@Composable
private fun MistakeCard(mistake: GameFinding.RepeatedMistake, theme: BoardTheme, onTrain: (() -> Unit)?) {
    CtCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            BoardThumbnail(mistake.fen, theme, flipped = mistake.fen.split(' ').getOrNull(1) == "b", size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOfNotNull(mistake.opening, mistake.after).joinToString(" · ").ifEmpty { mistake.played }, style = CtType.TitleSmall, maxLines = 2)
                Text(
                    buildAnnotatedString {
                        append("${mistake.times} of ${mistake.reached} games: you played ")
                        withStyle(SpanStyle(color = CtColors.Error, fontWeight = FontWeight.SemiBold)) { append(mistake.played) }
                        mistake.better?.let { append(". $it holds.") } ?: append(".")
                    },
                    style = CtType.Caption.copy(color = CtColors.Text2),
                )
            }
            onTrain?.let { CtChip(text = "Train", onClick = it) }
        }
    }
}
