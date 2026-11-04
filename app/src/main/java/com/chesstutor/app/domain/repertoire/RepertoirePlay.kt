package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.Position
import kotlin.random.Random

/**
 * "What do most players play here?" (D71: the other side's moves): asked once at a Core
 * branch point in a learn run, not scheduled. The choices are the most common replies at
 * the learner's level; the answer is the first.
 */
data class BranchQuestion(val node: RepertoireNode, val choices: List<RepertoireReply>) {
    val answer: RepertoireReply get() = choices.first()

    companion object {
        const val CHOICES = 3

        fun at(node: RepertoireNode, band: Int?): BranchQuestion? {
            if (node.own || node.level != RepertoireLevel.CORE) return null
            val replies = node.replies.filter { it.dataBacked }.sortedByDescending { it.shareFor(band) }
            if (replies.size < 2) return null
            return BranchQuestion(node, replies.take(CHOICES))
        }
    }
}

/** Where a game stands against the tree after the opponent's move (D73, out of book). */
sealed interface TreeStatus {
    /** A reply the tree knows; [card] is the learner's next position, or null where the line ends. */
    data class InTree(val reply: RepertoireReply, val card: RepertoireNode?) : TreeStatus

    /** A move the tree does not cover: say so, then the engine answers and the coach explains. */
    data object LeftTree : TreeStatus

    /** The line is over: every reply from here is beyond what the repertoire teaches. */
    data object EndOfLine : TreeStatus
}

/** What the sparring opponent does next: one of the tree's replies, or a move outside it. */
sealed interface SparringPick {
    data class Reply(val reply: RepertoireReply) : SparringPick

    /** Leave the tree as often as real players do here; the screen asks Maia for a human move. */
    data object Leave : SparringPick
}

object RepertoireGuide {
    /**
     * [before] is the position after the learner's repertoire move. No node there, or one
     * without replies, means the line has ended (the learner is never asked off the tree's
     * moves, so being here means the tree took them this far).
     */
    fun afterOpponentMove(book: RepertoireBook, before: Position, uci: String): TreeStatus {
        val node = book.at(before)?.takeIf { !it.own } ?: return TreeStatus.EndOfLine
        if (node.replies.isEmpty()) return TreeStatus.EndOfLine
        val reply = node.reply(uci) ?: return TreeStatus.LeftTree
        val after = RepertoireIndex.play(before, uci) ?: return TreeStatus.LeftTree
        return TreeStatus.InTree(reply, book.at(after)?.takeIf { it.own })
    }

    /**
     * Sparring (D73): replies by their real share at the learner's level, bad ones included,
     * and the rest of the time a move outside the tree, as often as players leave it here.
     * Only the [open] levels are played; a reply into a closed level counts as leaving.
     */
    fun sparringPick(node: RepertoireNode, band: Int?, open: RepertoireLevel, random: Random = Random.Default): SparringPick? {
        if (node.own || node.replies.isEmpty()) return null
        val weights = node.replies.map { it to it.shareFor(band) }
        val inTree = weights.filter { it.first.level <= open }
        val leave = (1.0 - inTree.sumOf { it.second }).coerceAtLeast(0.0)
        var roll = random.nextDouble() * (inTree.sumOf { it.second } + leave)
        for ((reply, weight) in inTree) {
            roll -= weight
            if (roll < 0) return SparringPick.Reply(reply)
        }
        return SparringPick.Leave
    }
}
