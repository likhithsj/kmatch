package io.github.likhithsj.kmatch

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the Hyyrö bit-parallel LCS equivalent to a straightforward DP (the
 * 0.1.0 implementation, kept here as the reference) across the regimes that
 * matter: empty inputs, single characters, the 63/64/65 word-boundary, long
 * blocked inputs, ASCII vs non-ASCII vs astral-plane alphabets, and window
 * sub-ranges as used by partialRatio.
 */
class BitParallelTest {

    // Alphabets chosen to exercise the ASCII dense table, the non-ASCII hash
    // map, and astral-plane code points. Small sizes force many matches.
    private val alphabets = listOf(
        intArrayOf('a'.code, 'b'.code),
        intArrayOf('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code, 'g'.code, 'h'.code),
        intArrayOf(0xE9, 0xF1, 0x4E2D, 0x6587, 'x'.code),          // é ñ 中 文 x
        intArrayOf(0x1F600, 0x1F680, 0x10348, 'a'.code, 0xE9),      // 😀 🚀 𐍈 a é
    )

    private fun randomSeq(rng: Random, alphabet: IntArray, length: Int): IntArray =
        IntArray(length) { alphabet[rng.nextInt(alphabet.size)] }

    @Test
    fun matchesDpOnRandomInputs() {
        val rng = Random(20260814)
        // Lengths straddle the single-word/blocked boundary (64) on both sides.
        val lengths = intArrayOf(0, 1, 2, 3, 7, 31, 63, 64, 65, 100, 128, 129, 200, 300)
        for (alphabet in alphabets) {
            repeat(60) {
                val la = lengths[rng.nextInt(lengths.size)]
                val lb = lengths[rng.nextInt(lengths.size)]
                val a = randomSeq(rng, alphabet, la)
                val b = randomSeq(rng, alphabet, lb)
                val expected = lcsLengthDp(a, b)
                assertEquals(
                    expected, lcsLength(a, b),
                    "lcsLength mismatch: la=$la lb=$lb alphabet=${alphabet.toList()}",
                )
            }
        }
    }

    @Test
    fun matchesDpOnWindowSubranges() {
        val rng = Random(97)
        for (alphabet in alphabets) {
            repeat(30) {
                val patternLen = 1 + rng.nextInt(150)
                val textLen = 1 + rng.nextInt(300)
                val p = randomSeq(rng, alphabet, patternLen)
                val t = randomSeq(rng, alphabet, textLen)
                val pattern = IndelPattern(p)
                repeat(10) {
                    val from = rng.nextInt(textLen + 1)
                    val to = from + rng.nextInt(textLen - from + 1)
                    val expected = lcsLengthDp(p, t.copyOfRange(from, to))
                    assertEquals(
                        expected, pattern.lcs(t, from, to),
                        "window lcs mismatch: patternLen=$patternLen from=$from to=$to",
                    )
                }
            }
        }
    }

    @Test
    fun scratchBufferReuseIsClean() {
        // Consecutive calls on a blocked pattern must not leak state.
        val rng = Random(7)
        val p = randomSeq(rng, alphabets[0], 130)
        val pattern = IndelPattern(p)
        val t1 = randomSeq(rng, alphabets[0], 200)
        val t2 = randomSeq(rng, alphabets[0], 50)
        val first = pattern.lcs(t1)
        assertEquals(lcsLengthDp(p, t2), pattern.lcs(t2))
        assertEquals(lcsLengthDp(p, t1), pattern.lcs(t1))
        assertEquals(first, pattern.lcs(t1))
    }

    @Test
    fun identicalAndDisjointSequences() {
        val a = IntArray(200) { 'a'.code + (it % 26) }
        assertEquals(200, lcsLength(a, a))
        val b = IntArray(150) { 0x4E00 + it } // CJK block, disjoint from a
        assertEquals(0, lcsLength(a, b))
    }

    @Test
    fun cutoffEarlyExitAgreesWithFullComputation() {
        val rng = Random(4242)
        repeat(300) {
            val alphabet = alphabets[rng.nextInt(alphabets.size)]
            val a = randomSeq(rng, alphabet, rng.nextInt(120))
            val b = randomSeq(rng, alphabet, rng.nextInt(120))
            val full = indelNormalizedSimilarity(a, b, null)
            val cutoff = rng.nextDouble()
            val withCutoff = indelNormalizedSimilarity(a, b, cutoff)
            assertEquals(if (full >= cutoff) full else 0.0, withCutoff)

            val dist = indelDistance(a, b, null)
            val distCutoff = rng.nextInt(60)
            val expectDist = if (dist <= distCutoff) dist else distCutoff + 1
            assertEquals(expectDist, indelDistance(a, b, distCutoff))
        }
    }
}
