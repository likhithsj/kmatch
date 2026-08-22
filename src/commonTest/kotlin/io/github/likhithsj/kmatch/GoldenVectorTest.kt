package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Asserts exact equality against golden vectors generated from the pinned
 * RapidFuzz version. A single differing bit is a parity failure.
 */
class GoldenVectorTest {

    @Test
    fun scorersMatchRapidFuzzExactly() {
        val failures = StringBuilder()
        var failed = 0
        for (case in GOLDEN_CASES) {
            val processor: ((String) -> String)? = if (case.processed) ::defaultProcess else null
            val actual = score(case.scorer, case.s1, case.s2, processor)
            // Exact comparison, no tolerance: parity means bit-exact.
            if (actual != case.expected) {
                failed++
                if (failed <= 25) {
                    failures
                        .append(case.scorer)
                        .append(if (case.processed) "+process" else "")
                        .append("(\"").append(case.s1).append("\", \"").append(case.s2)
                        .append("\"): expected ").append(case.expected)
                        .append(", got ").append(actual).append('\n')
                }
            }
        }
        if (failed > 0) {
            fail("$failed of ${GOLDEN_CASES.size} golden cases failed:\n$failures")
        }
    }

    @Test
    fun scorersHonorScoreCutoffExactly() {
        val failures = StringBuilder()
        var failed = 0
        for (case in CUTOFF_CASES) {
            val processor: ((String) -> String)? = if (case.processed) ::defaultProcess else null
            val actual = score(case.scorer, case.s1, case.s2, processor, case.cutoff)
            if (actual != case.expected) {
                failed++
                if (failed <= 25) {
                    failures
                        .append(case.scorer)
                        .append(if (case.processed) "+process" else "")
                        .append("(\"").append(case.s1).append("\", \"").append(case.s2)
                        .append("\", cutoff=").append(case.cutoff)
                        .append("): expected ").append(case.expected)
                        .append(", got ").append(actual).append('\n')
                }
            }
        }
        if (failed > 0) {
            fail("$failed of ${CUTOFF_CASES.size} cutoff cases failed:\n$failures")
        }
    }

    @Test
    fun defaultProcessMatchesRapidFuzzExactly() {
        for (case in PROCESS_CASES) {
            assertEquals(
                case.expected,
                defaultProcess(case.input),
                "defaultProcess(${case.input.encodeToByteArray().joinToString(",")})",
            )
        }
    }

    private fun score(
        scorer: String,
        s1: String,
        s2: String,
        processor: ((String) -> String)?,
        cutoff: Double? = null,
    ): Double =
        when (scorer) {
            "ratio" -> Fuzz.ratio(s1, s2, processor, cutoff)
            "partial_ratio" -> Fuzz.partialRatio(s1, s2, processor, cutoff)
            "token_sort_ratio" -> Fuzz.tokenSortRatio(s1, s2, processor, cutoff)
            "token_set_ratio" -> Fuzz.tokenSetRatio(s1, s2, processor, cutoff)
            "token_ratio" -> Fuzz.tokenRatio(s1, s2, processor, cutoff)
            "partial_token_sort_ratio" -> Fuzz.partialTokenSortRatio(s1, s2, processor, cutoff)
            "partial_token_set_ratio" -> Fuzz.partialTokenSetRatio(s1, s2, processor, cutoff)
            "partial_token_ratio" -> Fuzz.partialTokenRatio(s1, s2, processor, cutoff)
            "WRatio" -> Fuzz.weightedRatio(s1, s2, processor, cutoff)
            "QRatio" -> Fuzz.quickRatio(s1, s2, processor, cutoff)
            else -> fail("unknown scorer in golden vectors: $scorer")
        }
}
