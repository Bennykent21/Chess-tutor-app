package com.chesstutor.app.board

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter

/** One position in an analysis: the move that led here and the moves tried from here. */
class AnalysisNode internal constructor(
    val id: Int,
    val parent: AnalysisNode?,
    /** Null only for the root. */
    val move: Move?,
    val san: String,
    val position: Position,
    /** Ply of [move]: 1 is White's first move of a game that starts from the usual position. */
    val ply: Int,
) {
    /** The first child continues the main line; the others are variations. */
    val children: MutableList<AnalysisNode> = mutableListOf()

    val isRoot: Boolean get() = parent == null
    val mainChild: AnalysisNode? get() = children.firstOrNull()
}

/** A piece of move-list text for a tree with variations. */
sealed interface NotationToken {
    data class MoveToken(val nodeId: Int, val numberLabel: String?, val san: String, val depth: Int) : NotationToken
    data class VariationStart(val depth: Int) : NotationToken
    data class VariationEnd(val depth: Int) : NotationToken
}

/**
 * A game with side lines (plan section 4.2, free analysis): play a move anywhere and it
 * becomes a variation, step through with back and forward, jump to any move, promote or
 * delete lines. Pure, so it is unit tested; [AnalysisSession] puts it on a board.
 */
class AnalysisTree(start: Position = Position.initial()) {
    private var nextId = 0
    private val nodesById = HashMap<Int, AnalysisNode>()

    val root: AnalysisNode = newNode(parent = null, move = null, san = "", position = start, ply = if (start.sideToMove == PieceColor.WHITE) 0 else 1)
    var current: AnalysisNode = root
        private set

    fun node(id: Int): AnalysisNode? = nodesById[id]

    private fun newNode(parent: AnalysisNode?, move: Move?, san: String, position: Position, ply: Int): AnalysisNode =
        AnalysisNode(nextId++, parent, move, san, position, ply).also { nodesById[it.id] = it }

    /**
     * Plays [move] from the current position. An existing line with the same move is
     * followed instead of duplicated. Returns false (and changes nothing) if it is illegal.
     */
    fun play(move: Move): Boolean {
        current.children.firstOrNull { it.move?.uci == move.uci }?.let {
            current = it
            return true
        }
        val legal = LegalMoveGenerator.generateLegalMoves(current.position).firstOrNull { it.uci == move.uci } ?: return false
        val san = SanFormatter.format(current.position, legal)
        val child = newNode(current, legal, san, LegalMoveGenerator.makeMove(current.position, legal), current.ply + 1)
        current.children += child
        current = child
        return true
    }

    fun back(): Boolean {
        current = current.parent ?: return false
        return true
    }

    /** Forward along the line being viewed (the main continuation from here). */
    fun forward(): Boolean {
        current = current.mainChild ?: return false
        return true
    }

    fun toStart() {
        current = root
    }

    fun toEnd() {
        while (forward()) Unit
    }

    fun goTo(id: Int): Boolean {
        current = nodesById[id] ?: return false
        return true
    }

    /** Moves from the start to the current position. */
    fun movesToCurrent(): List<Move> = generateSequence(current) { it.parent }.mapNotNull { it.move }.toList().reversed()

    /** Makes the line through [id] the main line at every branching point above it. */
    fun promote(id: Int) {
        var node = nodesById[id] ?: return
        while (true) {
            val parent = node.parent ?: return
            parent.children.remove(node)
            parent.children.add(0, node)
            node = parent
        }
    }

    /** Deletes [id] and everything after it. The current position moves up if it was inside. */
    fun delete(id: Int) {
        val node = nodesById[id] ?: return
        val parent = node.parent ?: return
        if (generateSequence(current) { it.parent }.any { it === node }) current = parent
        parent.children.remove(node)
        forget(node)
    }

    /** Removes the current move if nothing follows it, then steps back (a takeback in a game). */
    fun takeBack(): Boolean {
        val node = current
        if (node.isRoot) return false
        if (node.children.isNotEmpty()) return back()
        delete(node.id)
        return true
    }

    private fun forget(node: AnalysisNode) {
        nodesById.remove(node.id)
        node.children.forEach(::forget)
    }

    /**
     * The move list in the usual book layout: the main line, with each variation in
     * brackets straight after the main-line move it replaces, for example
     * `1. e4 e5 2. Nf3 (2. Bc4 Nf6) 2... Nc6`.
     */
    fun notation(): List<NotationToken> {
        val tokens = mutableListOf<NotationToken>()
        writeLine(root, depth = 0, tokens = tokens, forceNumber = true)
        return tokens
    }

    private fun writeLine(from: AnalysisNode, depth: Int, tokens: MutableList<NotationToken>, forceNumber: Boolean) {
        var node = from
        var needNumber = forceNumber
        while (true) {
            val main = node.mainChild ?: return
            tokens += moveToken(main, depth, needNumber)
            needNumber = false
            val variations = node.children.drop(1)
            variations.forEach { variation ->
                tokens += NotationToken.VariationStart(depth + 1)
                tokens += moveToken(variation, depth + 1, forceNumber = true)
                writeLine(variation, depth + 1, tokens, forceNumber = false)
                tokens += NotationToken.VariationEnd(depth + 1)
            }
            // After a variation, Black's reply needs its number again ("2... Nc6").
            if (variations.isNotEmpty()) needNumber = true
            node = main
        }
    }

    private fun moveToken(node: AnalysisNode, depth: Int, forceNumber: Boolean): NotationToken.MoveToken {
        val whiteMove = node.ply % 2 == 1
        // The root's ply is 0 (White to move) or 1 (Black to move), so this counts from the start position's number.
        val number = (node.ply + 1) / 2 + root.position.fullmoveNumber - 1
        val label = when {
            whiteMove -> "$number."
            forceNumber -> "$number..."
            else -> null
        }
        return NotationToken.MoveToken(node.id, label, node.san, depth)
    }
}
