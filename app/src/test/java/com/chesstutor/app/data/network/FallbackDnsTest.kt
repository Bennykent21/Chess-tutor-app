package com.chesstutor.app.data.network

import okhttp3.Dns
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class FallbackDnsTest {

    private val found = listOf(InetAddress.getByAddress("api.chess.com", byteArrayOf(10, 0, 0, 1)))
    private val working = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = found
    }
    private val failing = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException("Unable to resolve host \"$hostname\"")
    }

    private val answer = JSONObject(
        """{"Status":0,"Answer":[
            {"name":"api.chess.com","type":5,"data":"cdn.example.com."},
            {"name":"cdn.example.com","type":1,"data":"104.18.138.67"},
            {"name":"cdn.example.com","type":1,"data":"104.18.141.67"}]}""",
    ).toString()

    @Test
    fun thePhonesOwnLookupComesFirstAndNothingLeavesThePhone() {
        val asked = mutableListOf<String>()

        val addresses = FallbackDns(working) { asked += it; answer }.lookup("api.chess.com")

        assertEquals(found, addresses)
        assertTrue("no public resolver hears of it", asked.isEmpty())
    }

    @Test
    fun aFailedLookupAsksAResolverAndKeepsTheSiteName() {
        val addresses = FallbackDns(failing) { answer }.lookup("api.chess.com")

        assertEquals(listOf("104.18.138.67", "104.18.141.67"), addresses.map { it.hostAddress })
        assertEquals("the certificate is checked against this name", "api.chess.com", addresses.first().hostName)
    }

    @Test
    fun theSecondResolverIsTriedWhenTheFirstIsDown() {
        val asked = mutableListOf<String>()

        val addresses = FallbackDns(failing) { url -> asked += url; answer.takeIf { asked.size == 2 } }.lookup("lichess.org")

        assertEquals(2, asked.size)
        assertTrue(asked.all { it.startsWith("https://") && it.endsWith("name=lichess.org") })
        assertEquals(2, addresses.size)
    }

    @Test
    fun theOriginalFailureStandsWhenNoResolverHelps() {
        assertThrows(UnknownHostException::class.java) { FallbackDns(failing) { null }.lookup("api.chess.com") }
        assertThrows(UnknownHostException::class.java) { FallbackDns(failing) { "not json" }.lookup("api.chess.com") }
        assertThrows(UnknownHostException::class.java) { FallbackDns(failing) { """{"Status":3}""" }.lookup("nosuchsite.example") }
    }
}
