package com.chesstutor.app.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The step 1A board renderer, drawn with Robolectric's native graphics so the piece
 * VectorDrawables really inflate. Look at it with
 * `.\gradlew.bat recordRoborazziDebug --tests "*BoardViewTest*"` (images in app/build/outputs/roborazzi).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class BoardViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val position = Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
    private val marks = BoardMarks(
        lastMove = Square.fromAlgebraic("f8") to Square.fromAlgebraic("c5"),
        selected = Square.fromAlgebraic("f3"),
        legalTargets = setOf("g5", "e5", "h4", "d4", "g1").map(Square::fromAlgebraic).toSet(),
    )

    @Test
    fun everyPieceDrawableInflatesAndIsDistinct() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PieceSet.entries.forEach { set ->
            val ids = PieceArt.allPieces.map { PieceArt.drawable(set, it) }
            assertEquals("${set.id} has 12 different drawables", 12, ids.toSet().size)
            ids.forEach { assertNotNull(ContextCompat.getDrawable(context, it)) }
        }
        assertEquals(48, PieceSet.entries.flatMap { set -> PieceArt.allPieces.map { PieceArt.drawable(set, it) } }.toSet().size)
    }

    @Test
    fun everyPieceSetOnItsOwnTheme() {
        val pairs = PieceSet.entries.zip(listOf(BoardColors.CLASSIC, BoardColors.MEADOW, BoardColors.LAGOON, BoardColors.WALNUT))
        compose.setContent {
            CtTheme {
                CtScreenBackground {
                    Column(Modifier.padding(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        pairs.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                                row.forEach { (set, colors) ->
                                    BoardView(position, BoardTheme(colors, set), Modifier.weight(1f), marks = marks)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onAllNodes(hasContentDescription("Chess board. White to move. White at the bottom.")).assertCountEquals(4)
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun remainingThemesAndBlackAtTheBottom() {
        val themes = listOf(BoardColors.ICE, BoardColors.NEON, BoardColors.MARBLE, BoardColors.CLASSIC)
        compose.setContent {
            CtTheme {
                CtScreenBackground {
                    Column(Modifier.padding(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        themes.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                                row.forEach { colors ->
                                    BoardView(
                                        position = position,
                                        theme = BoardTheme(colors, PieceSet.CBURNETT),
                                        modifier = Modifier.weight(1f),
                                        whiteAtBottom = colors != BoardColors.CLASSIC,
                                        marks = marks.copy(check = if (colors == BoardColors.MARBLE) Square.fromAlgebraic("e1") else null),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNode(hasContentDescription("Chess board. White to move. Black at the bottom.")).assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun descriptionNamesTheSideToMoveAndOrientation() {
        assertEquals("Chess board. White to move. White at the bottom.", boardDescription(Position.initial(), true))
        val blackToMove = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        assertEquals("Chess board. Black to move. Black at the bottom.", boardDescription(blackToMove, false))
    }
}
