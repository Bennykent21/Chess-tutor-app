package com.chesstutor.app.board

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The move list of an analysis and where the board is in it. */
data class AnalysisView(
    val tokens: List<NotationToken>,
    val currentId: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    /** True when the current move is a side line, so it can be promoted or deleted. */
    val inVariation: Boolean,
)

/**
 * Free analysis on a board (plan section 4.2): both sides move, every new move from an
 * earlier position becomes a variation, and the player steps or jumps through the tree.
 * Stepping forward slides the piece; jumps and stepping back snap.
 */
class AnalysisSession(start: Position = Position.initial()) : BoardSession {
    private var tree = AnalysisTree(start)
    private val mutableState = MutableStateFlow(BoardSessionState(start))
    private val mutableView = MutableStateFlow(viewOf(tree))

    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()
    val view: StateFlow<AnalysisView> = mutableView.asStateFlow()

    /** The id of the position on the board, for things kept per position (drawn arrows). */
    val currentId: Int get() = tree.current.id

    override fun onMove(move: Move): BoardFeedback {
        val before = tree.current.position
        tree.play(move)
        publish(animate = true)
        return MoveEffects.feedbackFor(before, tree.current.move ?: move, tree.current.position)
    }

    fun back() = step { tree.back() }
    fun forward() = step(animate = true) { tree.forward() }
    fun toStart() = step { tree.toStart(); true }
    fun toEnd() = step { tree.toEnd(); true }
    fun goTo(id: Int) = step { tree.goTo(id) }
    fun takeBack() = step { tree.takeBack() }

    fun promoteCurrent() {
        tree.promote(tree.current.id)
        publish(animate = false)
    }

    fun deleteCurrent() = step { tree.delete(tree.current.id); true }

    fun reset(position: Position) {
        tree = AnalysisTree(position)
        publish(animate = false)
    }

    private inline fun step(animate: Boolean = false, action: () -> Boolean) {
        if (action()) publish(animate)
    }

    private fun publish(animate: Boolean) {
        val node = tree.current
        val legal = LegalMoveGenerator.generateLegalMoves(node.position)
        mutableState.value = BoardSessionState(
            position = node.position,
            previous = node.parent?.position,
            lastMove = node.move,
            version = mutableState.value.version + 1,
            animateLastMove = animate && node.move != null,
            movable = if (legal.isEmpty()) null else node.position.sideToMove,
            legalMoves = legal,
            start = tree.root.position,
            moves = tree.movesToCurrent(),
        )
        mutableView.value = viewOf(tree)
    }

    private fun viewOf(tree: AnalysisTree): AnalysisView {
        val node = tree.current
        val inVariation = generateSequence(node) { it.parent }.any { child -> child.parent?.mainChild?.let { it !== child } == true }
        return AnalysisView(
            tokens = tree.notation(),
            currentId = node.id,
            canGoBack = !node.isRoot,
            canGoForward = node.mainChild != null,
            inVariation = inVariation,
        )
    }
}
