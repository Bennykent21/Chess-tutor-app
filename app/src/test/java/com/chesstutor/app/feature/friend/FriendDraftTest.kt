package com.chesstutor.app.feature.friend

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.opening.Opening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FriendDraftTest {
    @Test
    fun theOwnerAndTheFriendTakeTheirColoursNamesAndClocks() {
        val draft = FriendDraft(friendName = " Ada ", ownerColor = PieceColor.BLACK, clock = PlayerClock.of(10), friendClock = PlayerClock.of(3, 2))
        val setup = draft.toSetup("Sam")
        assertEquals("Ada", setup.whiteName)
        assertEquals("Sam", setup.blackName)
        assertEquals(PieceColor.BLACK, setup.ownerColor)
        assertEquals("the friend plays White on their own clock", PlayerClock.of(3, 2), setup.whiteClock)
        assertEquals(PlayerClock.of(10), setup.blackClock)
    }

    @Test
    fun emptyNamesGetPlainOnesAndNoClockMeansNoClockForEither() {
        val setup = FriendDraft(clock = null, friendClock = PlayerClock.of(5)).toSetup("")
        assertEquals("You", setup.whiteName)
        assertEquals("Friend", setup.blackName)
        assertNull(setup.whiteClock)
        assertNull(setup.blackClock)
    }

    @Test
    fun theStartFollowsTheChoiceAndAnUnfinishedChoiceBlocksTheStart() {
        val sicilian = Opening("B20", "Sicilian Defense", listOf("e2e4", "c7c5"))
        assertEquals("Choose an opening", FriendDraft(start = FriendStart.OPENING).problem)
        val opening = FriendDraft(start = FriendStart.OPENING, opening = sicilian)
        assertNull(opening.problem)
        assertEquals(listOf("e2e4", "c7c5"), opening.toSetup("Sam").openingMoves)
        assertEquals("a chosen opening is ignored for a normal start", emptyList<String>(), opening.copy(start = FriendStart.NORMAL).toSetup("Sam").openingMoves)

        val kings = FriendDraft(start = FriendStart.POSITION, board = SetupBoard.KINGS_ONLY)
        assertEquals(SetupBoard.KINGS_ONLY.toFen(), kings.toSetup("Sam").startFen)
        assertEquals(Position.STARTING_FEN, kings.copy(start = FriendStart.NORMAL).toSetup("Sam").startFen)
        val noKings = SetupBoard(List(64) { null })
        assertNotNull(FriendDraft(start = FriendStart.POSITION, board = noKings).problem)
    }
}
