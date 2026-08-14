package io.github.likhithsj.kmatch

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The prepared (mask-reusing) scorer path used inside extraction must return
 * exactly what the string-based scorer returns for every built-in scorer,
 * across cutoffs, empty strings, unicode, and >64-code-point inputs.
 */
class CachedScorerTest {

    private val allScorers = listOf(
        "Ratio" to Scorers.Ratio,
        "PartialRatio" to Scorers.PartialRatio,
        "TokenSortRatio" to Scorers.TokenSortRatio,
        "TokenSetRatio" to Scorers.TokenSetRatio,
        "TokenRatio" to Scorers.TokenRatio,
        "PartialTokenSortRatio" to Scorers.PartialTokenSortRatio,
        "PartialTokenSetRatio" to Scorers.PartialTokenSetRatio,
        "PartialTokenRatio" to Scorers.PartialTokenRatio,
        "WeightedRatio" to Scorers.WeightedRatio,
        "QuickRatio" to Scorers.QuickRatio,
    )

    private val samples = listOf(
        "",
        "a",
        "new york mets",
        "the wonderful new york mets",
        "café résumé naïve",
        "😀🚀 unicode 中文 test",
        "the quick brown fox jumps over the lazy dog and keeps on running far past sixty four code points total",
        "fox  the   quick",
        "completely unrelated words here",
    )

    @Test
    fun preparedPathMatchesStringPathExactly() {
        for ((name, scorer) in allScorers) {
            for (q in samples) {
                val prepared = prepareScorer(scorer, q.toCodePoints())!!
                for (c in samples) {
                    for (cutoff in listOf(0.0, 30.0, 60.0, 90.0)) {
                        val viaString = scorer.score(q, c, cutoff)
                        val viaPrepared = prepared.score(c.toCodePoints(), cutoff)
                        assertEquals(
                            viaString, viaPrepared,
                            "$name(\"$q\", \"$c\", cutoff=$cutoff)",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun extractionResultsUnchangedByFastPath() {
        // extractAll with each built-in scorer must equal a manual loop over
        // the public string API.
        val rng = Random(11)
        val words = listOf("new", "york", "mets", "atlanta", "braves", "café", "😀", "matching", "fuzzy")
        val choices = (0 until 40).map { (0 until 1 + rng.nextInt(6)).joinToString(" ") { words[rng.nextInt(words.size)] } }
        val query = "new york mets 😀"
        for ((name, scorer) in allScorers) {
            for (cutoff in listOf(null, 55.0)) {
                val viaExtract = extractAll(query, choices, scorer, null, cutoff)
                val manual = choices.mapIndexedNotNull { i, c ->
                    val s = scorer.score(query, c, cutoff)
                    if (s >= (cutoff ?: 0.0)) ExtractedResult(c, s, i) else null
                }
                assertEquals(manual, viaExtract, "$name cutoff=$cutoff")
            }
        }
    }

    @Test
    fun extractOneUnchangedByFastPath() {
        val choices = listOf("atlanta falcons", "new york jets", "new york giants", "dallas cowboys")
        for ((name, scorer) in allScorers) {
            val viaExtract = extractOne("new york jets", choices, scorer)
            val manual = choices.mapIndexed { i, c ->
                ExtractedResult(c, scorer.score("new york jets", c, null), i)
            }.maxByOrNull { it.score }
            assertEquals(manual?.choice, viaExtract?.choice, name)
            assertEquals(manual?.score ?: -1.0, viaExtract?.score ?: -1.0, name)
        }
    }
}
