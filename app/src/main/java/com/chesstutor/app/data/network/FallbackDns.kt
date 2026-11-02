package com.chesstutor.app.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Finds a site with the phone's own lookup first. Some networks and private DNS settings cannot
 * find one site while the rest of the internet works, which stopped imports with "Unable to
 * resolve host". Only then does it ask a public resolver over HTTPS, reached by its IP address so
 * that needs no lookup itself. The answer only says where to connect: the site's own certificate
 * is still checked, so a wrong answer cannot pass for Chess.com or Lichess.
 */
class FallbackDns(
    private val system: Dns = Dns.SYSTEM,
    /** The body of a GET to a resolver's URL, or null when it did not answer. */
    private val fetch: (String) -> String? = ::fetchFromResolver,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> = try {
        system.lookup(hostname)
    } catch (e: UnknownHostException) {
        RESOLVERS.firstNotNullOfOrNull { resolver ->
            fetch(resolver + hostname)?.let { parseAddresses(hostname, it) }?.ifEmpty { null }
        } ?: throw e
    }

    companion object {
        private val RESOLVERS = listOf(
            "https://1.1.1.1/dns-query?type=A&name=",
            "https://8.8.8.8/resolve?type=A&name=",
        )
        private const val A_RECORD = 1
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val http by lazy {
            OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
        }

        private fun fetchFromResolver(url: String): String? = try {
            http.newCall(Request.Builder().url(url).header("Accept", "application/dns-json").build()).execute().use { response ->
                response.body?.string().takeIf { response.isSuccessful }
            }
        } catch (e: IOException) {
            null
        }

        /** The IPv4 addresses in a resolver's JSON answer; empty when it is unreadable. */
        internal fun parseAddresses(hostname: String, json: String): List<InetAddress> {
            val answers = runCatching { JSONObject(json).optJSONArray("Answer") }.getOrNull() ?: return emptyList()
            return (0 until answers.length()).mapNotNull { i ->
                val answer = answers.optJSONObject(i)?.takeIf { it.optInt("type") == A_RECORD } ?: return@mapNotNull null
                val address = answer.optString("data").takeIf(IPV4::matches) ?: return@mapNotNull null
                runCatching {
                    InetAddress.getByAddress(hostname, address.split('.').map { it.toInt().toByte() }.toByteArray())
                }.getOrNull()
            }
        }
    }
}
