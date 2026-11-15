package com.chesstutor.app.domain.opening

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Positions that count as book in a game review (Phase 13B): those reached in enough master games,
 * as `tools/build_book.py` crawled them from the Lichess masters explorer. Each position is one
 * 64-bit fingerprint of its [com.chesstutor.app.core.repetitionKey], so the set is small and a
 * look-up is a binary search.
 */
class OpeningBook private constructor(private val fingerprints: LongArray) {
    val size: Int get() = fingerprints.size

    fun contains(key: String): Boolean = fingerprints.binarySearch(fingerprint(key)) >= 0

    companion object {
        val EMPTY = OpeningBook(LongArray(0))

        /** Reads `book.bin`: big-endian 64-bit fingerprints. */
        fun fromStream(stream: InputStream): OpeningBook {
            val input = DataInputStream(stream.buffered())
            val out = ArrayList<Long>()
            try {
                while (true) out += input.readLong()
            } catch (_: EOFException) {
                // The end of the file.
            }
            // The file is sorted as unsigned numbers; Kotlin's longs are signed.
            return OpeningBook(out.toLongArray().also { it.sort() })
        }

        /** FNV-1a, 64-bit, over the key's UTF-8 bytes, as the build script computes it. */
        fun fingerprint(key: String): Long {
            var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (b in key.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
