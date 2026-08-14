package io.github.likhithsj.kmatch

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlignTest {

    /** Blocks must be valid, ordered, maximal-ish, and cover exactly the LCS. */
    private fun checkInvariants(s1: String, s2: String): List<MatchingBlock> {
        val blocks = matchingBlocks(s1, s2)
        var lastA = 0
        var lastB = 0
        var coveredCps = 0
        for (blk in blocks) {
            assertTrue(blk.length > 0, "empty block for ($s1, $s2)")
            assertTrue(blk.aStart >= lastA && blk.bStart >= lastB, "blocks out of order for ($s1, $s2)")
            val fromA = s1.substring(blk.aStart, blk.aStart + blk.length)
            val fromB = s2.substring(blk.bStart, blk.bStart + blk.length)
            assertEquals(fromA, fromB, "block text mismatch for ($s1, $s2)")
            coveredCps += fromA.toCodePoints().size
            lastA = blk.aStart + blk.length
            lastB = blk.bStart + blk.length
        }
        assertEquals(
            lcsLengthDp(s1.toCodePoints(), s2.toCodePoints()), coveredCps,
            "blocks do not cover the LCS for ($s1, $s2)",
        )
        return blocks
    }

    @Test
    fun simpleCases() {
        assertEquals(emptyList(), matchingBlocks("", "abc"))
        assertEquals(emptyList(), matchingBlocks("abc", ""))
        assertEquals(emptyList(), matchingBlocks("abc", "xyz"))
        assertEquals(listOf(MatchingBlock(0, 0, 3)), matchingBlocks("abc", "abc"))

        checkInvariants("this is a test", "this is a test!")
        checkInvariants("new york mets", "new YORK mets")
        checkInvariants("kitten", "sitting")
    }

    @Test
    fun astralPlaneCharIndices() {
        // "😀ab" vs "x😀ab": 😀 is 2 UTF-16 chars; indices must be char units.
        val blocks = checkInvariants("😀ab", "x😀ab")
        assertEquals(listOf(MatchingBlock(0, 1, 4)), blocks)
    }

    @Test
    fun highlightingRanges() {
        val ranges = matchingRanges("new york", "the new york times")
        // Concatenating the highlighted text must contain exactly the LCS.
        val highlighted = ranges.joinToString("") { "the new york times".substring(it.first, it.last + 1) }
        assertEquals("new york", highlighted)
    }

    @Test
    fun randomizedInvariants() {
        val rng = Random(31)
        val alphabets = listOf("ab", "abcde", "aé中😀")
        repeat(200) {
            val alphabet = alphabets[rng.nextInt(alphabets.size)]
            fun gen(n: Int) = buildString {
                repeat(n) {
                    val ch = alphabet[rng.nextInt(alphabet.length)]
                    if (ch.isSurrogate()) append("😀") else append(ch)
                }
            }
            checkInvariants(gen(rng.nextInt(80)), gen(rng.nextInt(80)))
        }
    }

    @Test
    fun longInputsStayLinearMemory() {
        // 4000 x 4000 code points: the full DP matrix would be 64 MB of ints;
        // Hirschberg handles it in linear memory. This mostly asserts it
        // completes and stays correct.
        val a = (0 until 4000).joinToString("") { ('a' + (it % 17)).toString() }
        val b = (0 until 4000).joinToString("") { ('a' + (it % 13)).toString() }
        val blocks = matchingBlocks(a, b)
        val covered = blocks.sumOf { it.length }
        assertEquals(lcsLengthDp(a.toCodePoints(), b.toCodePoints()), covered)
    }
}
