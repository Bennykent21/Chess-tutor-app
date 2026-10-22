package com.chesstutor.app.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEngineConfigTest {

    @Test
    fun defaultUsesAtMostTwoThreadsSoTheScreenStaysSmooth() {
        val threads = NativeEngineConfig().threads
        assertTrue("threads = $threads", threads in 1..2)
    }
}
