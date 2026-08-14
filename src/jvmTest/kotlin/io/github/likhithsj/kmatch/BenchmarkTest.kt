package io.github.likhithsj.kmatch

import kotlin.random.Random
import kotlin.test.Test

/**
 * In-repo micro-benchmark: bit-parallel core vs the 0.1.0 DP, and the
 * mask-reusing extraction path vs the string-based scorer path.
 *
 * Gated behind KMATCH_BENCH=1 so normal test runs stay fast:
 *
 *     KMATCH_BENCH=1 ./gradlew jvmTest --tests '*BenchmarkTest' --rerun
 *
 * Methodology: fixed-seed data, warmup rounds, then the median of 7 timed
 * rounds. A checksum accumulator defeats dead-code elimination. Numbers are
 * indicative (shared CI-class hardware), not lab-grade.
 */
class BenchmarkTest {

    private fun enabled() = System.getenv("KMATCH_BENCH") == "1"

    private fun words(rng: Random, n: Int): List<String> {
        val syllables = listOf("ka", "ro", "mi", "ta", "shi", "lo", "ven", "dar", "el", "us", "gra", "pon")
        return (0 until n).map {
            (0 until 2 + rng.nextInt(4)).joinToString("") { syllables[rng.nextInt(syllables.size)] }
        }
    }

    private fun median(times: LongArray): Double {
        times.sort()
        return times[times.size / 2] / 1e6
    }

    private inline fun bench(name: String, rounds: Int = 7, warmup: Int = 3, body: () -> Long) {
        var checksum = 0L
        repeat(warmup) { checksum += body() }
        val times = LongArray(rounds) {
            val t0 = System.nanoTime()
            checksum += body()
            System.nanoTime() - t0
        }
        println("BENCH  $name: ${median(times).let { "%.2f".format(it) }} ms/round  (checksum $checksum)")
    }

    @Test
    fun pairwiseRatioMediumStrings() {
        if (!enabled()) return
        val rng = Random(1)
        val pairs = (0 until 2000).map {
            val a = words(rng, 3 + rng.nextInt(3)).joinToString(" ")
            val b = words(rng, 3 + rng.nextInt(3)).joinToString(" ")
            a.toCodePoints() to b.toCodePoints()
        }
        println("median cps length: ${pairs.flatMap { listOf(it.first.size, it.second.size) }.sorted()[pairs.size]}")
        bench("ratio medium  DP          ") {
            var acc = 0L
            for ((a, b) in pairs) acc += lcsLengthDp(a, b).toLong()
            acc
        }
        bench("ratio medium  bit-parallel") {
            var acc = 0L
            for ((a, b) in pairs) acc += lcsLength(a, b).toLong()
            acc
        }
    }

    @Test
    fun pairwiseRatioLongStrings() {
        if (!enabled()) return
        val rng = Random(2)
        val pairs = (0 until 300).map {
            val a = words(rng, 40).joinToString(" ")  // ~200+ cps: blocked path
            val b = words(rng, 40).joinToString(" ")
            a.toCodePoints() to b.toCodePoints()
        }
        println("median cps length: ${pairs.flatMap { listOf(it.first.size, it.second.size) }.sorted()[pairs.size]}")
        bench("ratio long    DP          ") {
            var acc = 0L
            for ((a, b) in pairs) acc += lcsLengthDp(a, b).toLong()
            acc
        }
        bench("ratio long    bit-parallel") {
            var acc = 0L
            for ((a, b) in pairs) acc += lcsLength(a, b).toLong()
            acc
        }
    }

    @Test
    fun extractionScan() {
        if (!enabled()) return
        val rng = Random(3)
        val choices = (0 until 20000).map { words(rng, 2 + rng.nextInt(4)).joinToString(" ") }
        val query = choices[12345]
        // Custom lambda defeats the identity check, forcing the string path
        // (per-choice query conversion, no mask reuse) with identical scores.
        val uncached = Scorer { a, b, c -> Fuzz.ratio(a, b, scoreCutoff = c) }
        bench("extractOne 20k  string path ", rounds = 7) {
            extractOne(query, choices, uncached)!!.index.toLong()
        }
        bench("extractOne 20k  mask reuse  ", rounds = 7) {
            extractOne(query, choices, Scorers.Ratio)!!.index.toLong()
        }
        bench("extractOne 20k  reuse+cutoff", rounds = 7) {
            extractOne(query, choices, Scorers.Ratio, scoreCutoff = 80.0)!!.index.toLong()
        }
    }

    @Test
    fun partialRatioScan() {
        if (!enabled()) return
        val rng = Random(4)
        val needle = words(rng, 3).joinToString(" ")
        val haystacks = (0 until 500).map { words(rng, 30).joinToString(" ") }
        bench("partialRatio 500 long texts") {
            var acc = 0L
            for (h in haystacks) acc += Fuzz.partialRatio(needle, h).toLong()
            acc
        }
    }
}
