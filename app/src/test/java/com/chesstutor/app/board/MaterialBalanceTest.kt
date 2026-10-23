package com.chesstutor.app.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType.BISHOP
import com.chesstutor.app.core.PieceType.KNIGHT
import com.chesstutor.app.core.PieceType.PAWN
import com.chesstutor.app.core.PieceType.QUEEN
import com.chesstutor.app.core.PieceType.ROOK
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h300dp-xxhdpi")
class MaterialBalanceTest {

    @get:Rule
    val compose = createComposeRule()

    private val start = Position.initial()

    @Test
    fun nothingTakenAtTheStart() {
        val balance = MaterialBalance.of(start, start)
        assertEquals(emptyList<Any>(), balance.takenByWhite + balance.takenByBlack)
        assertNull(balance.leadOf(PieceColor.WHITE))
        assertNull(balance.leadOf(PieceColor.BLACK))
    }

    @Test
    fun takenPiecesAreListedCheapestFirstAndTheLeadIsInPawns() {
        // White is missing a pawn and a knight; Black is missing two pawns and both rooks.
        val now = Position.fromFen("1nbqkbn1/pp1pp1pp/8/8/8/8/PPPPPPP1/R1BQKBNR w - - 0 20")
        val balance = MaterialBalance.of(start, now)
        assertEquals(listOf(PAWN, PAWN, ROOK, ROOK), balance.takenByWhite)
        assertEquals(listOf(PAWN, KNIGHT), balance.takenByBlack)
        assertEquals("White 39 - 4 = 35, Black 39 - 12 = 27", 8, balance.whiteLead)
        assertEquals(8, balance.leadOf(PieceColor.WHITE))
        assertNull(balance.leadOf(PieceColor.BLACK))
    }

    @Test
    fun aPromotedPawnIsNotShownAsTaken() {
        // White promoted a pawn to a second queen; nothing was captured.
        val now = Position.fromFen("Qnbqkbnr/1ppppppp/8/8/8/8/1PPPPPPP/RNBQKBNR w KQk - 0 30")
        val balance = MaterialBalance.of(start, now)
        assertEquals("White's a-pawn became a queen", emptyList<Any>(), balance.takenByBlack)
        assertEquals("the queen took Black's a-pawn and rook", listOf(PAWN, ROOK), balance.takenByWhite)
        assertEquals(8 + 1 + 5, balance.whiteLead)
    }

    @Test
    fun aSetUpPositionCountsFromItsOwnStart() {
        val setUp = Position.fromFen("4k3/pppp4/8/8/8/8/PPPP4/4K2Q w - - 0 1")
        val later = Position.fromFen("4k3/ppp5/8/8/8/8/PPPP4/4K3 w - - 0 5")
        val balance = MaterialBalance.of(setUp, later)
        assertEquals(listOf(PAWN), balance.takenByWhite)
        assertEquals(listOf(QUEEN), balance.takenByBlack)
        assertEquals("4 pawns against 3", 1, balance.whiteLead)
    }

    @Test
    fun screenReadersHearWhatWasTaken() {
        assertEquals("Taken: 2 pawns and a knight. Ahead by 4.", describe(listOf(PAWN, PAWN, KNIGHT), 4))
        assertEquals("Taken: a queen.", describe(listOf(QUEEN), null))
    }

    @Test
    fun theRowShowsGroupsAndTheLead() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CtScreenBackground {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CapturedPieces(listOf(PAWN, PAWN, PAWN, KNIGHT, BISHOP, ROOK), PieceColor.BLACK, 5, PieceSet.DEFAULT)
                        CapturedPieces(listOf(PAWN, QUEEN), PieceColor.WHITE, null, PieceSet.DEFAULT)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Taken: 3 pawns, a knight, a bishop and a rook. Ahead by 5.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/CapturedPieces.png")
    }
}
